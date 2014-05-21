package org.discovery.dvms.dvms

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

import akka.actor.{ActorLogging, Actor}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Await}
import org.discovery.peeractor.util.{MayFail, NodeRef}

import org.discovery.DiscoveryModel.model.ReconfigurationModel._
import org.discovery.dvms.monitor.MonitorEvent
import org.discovery.peeractor.overlay.OverlayService
import ExecutionContext.Implicits.global
import org.discovery.dvms.utility.PlanApplicator
import java.util.Date
import org.discovery.dvms.entropy.EntropyProtocol.EntropyComputeReconfigurePlan
import org.discovery.DiscoveryModel.model.ReconfigurationModel.ReconfigurationSolution
import org.discovery.peeractor.notification.NotificationActorProtocol.Register
import scala.Some
import org.discovery.dvms.dvms.DvmsProtocol.CheckTimeout
import org.discovery.dvms.dvms.LocalityBasedSchedulerProtocol._
import org.discovery.DiscoveryModel.model.ReconfigurationModel.ReconfigurationlNoSolution


object LocalityBasedSchedulerProtocol {
  case class YouBelongToThisPartition(partition: Partition) extends DvmsMessage
  case class MergeWithThisPartition(partition: Partition) extends DvmsMessage
  case class Downgrade() extends DvmsMessage
  case class PartitionChanged(partition: Partition) extends DvmsMessage
  case class Exit() extends DvmsMessage
}

case class Partition(leader: MayFail[NodeRef], nodes: List[MayFail[NodeRef]])

object LocalityBasedScheduler {
  val partitionTimeout: FiniteDuration = 3500 milliseconds
}

class LocalityBasedScheduler(currentNode: NodeRef, overlayService: OverlayService, planApplicator: PlanApplicator) extends Actor with ActorLogging with SchedulerActor {

  implicit val timeout = Timeout(2 seconds)
  var isLeading = false
  var currentPartition: Option[Partition] = None
  var lastPartitionUpdate: Option[Date] = None

  def enoughResources(partition: Partition): Boolean = {

    val filteredNodes = partition.nodes.map(mayFailedNode => {
      for {
        node <- mayFailedNode.executeInProtectedSpace(n => n)
      } yield node
    })

    val futureList = Future.sequence(filteredNodes)
    val nodes = Await.result(futureList, 2 seconds)

    val entropyComputeAsFuture: Future[ReconfigurationResult] = (
      (currentNode.ref ? EntropyComputeReconfigurePlan(nodes)) recover {
        case _: Throwable => ReconfigurationlNoSolution()
      }
      ).mapTo[ReconfigurationResult]

    Await.result(entropyComputeAsFuture, 2 seconds) match {
      case plan@ReconfigurationSolution(actions) =>
        planApplicator.applySolution(currentNode, plan, nodes)
        true
      case ReconfigurationlNoSolution() =>
        false
    }
  }

  def startIterativeScheduling() {

    isLeading = true
    currentPartition = Some(Partition(MayFail.protect(currentNode), List(MayFail.protect(currentNode))))
    lastPartitionUpdate = Some(new Date())

    log.info(s"$currentNode: starting ISP")

    do {
      log.info(s"$currentNode: asking for a Node")
      Await.result(overlayService.giveSomeNeighbourOutside(currentPartition.get.nodes), 2 seconds) match {

        case Some(mayFailedNode) =>
          mayFailedNode.watch(failedNode => {
            log.info("removing failed node")
            currentPartition = Some(Partition(MayFail.protect(currentNode),
              currentPartition.get.nodes.filter(mayFailedNode => failedNode != mayFailedNode)))
            lastPartitionUpdate = Some(new Date())
          })
          currentPartition = Some(Partition(MayFail.protect(currentNode), mayFailedNode :: currentPartition.get.nodes))
          mayFailedNode.executeInProtectedSpace(node => node.ref ! YouBelongToThisPartition(currentPartition.get))
          log.info(s"updating partition: $currentPartition")
          notifyPartitionMembers()
        case _ =>
          destroyPartition()
      }

    } while (isLeading && !enoughResources(currentPartition.get))

    if(isLeading) {destroyPartition()}
  }

  def destroyPartition() {
    currentPartition match {
      case Some(partition) =>
        partition.nodes.foreach(mayFailedNode => mayFailedNode.executeInProtectedSpace(node =>
          if(node.location.isDifferentFrom(currentNode.location)) {
            node.ref ! Exit()
          }
        ))
        currentPartition = None
        lastPartitionUpdate = None
        isLeading = false
        log.info(s"[$currentNode]: i'm free")
      case None =>
    }
  }

  def notifyPartitionMembers() {
    currentPartition match {
      case Some(partition) =>
        partition.nodes.foreach(mayFailedNode => mayFailedNode.executeInProtectedSpace(node =>
          if(node.location.isDifferentFrom(currentNode.location)) {
            node.ref ! PartitionChanged(partition)
          }
        ))
      case None =>
    }
  }

  def exitPartition() {
    currentPartition = None
    lastPartitionUpdate = None
  }

  override def receive = {
    case YouBelongToThisPartition(remotePartition: Partition) =>
      currentPartition match {
        case Some(partition) =>
          remotePartition.leader.executeInProtectedSpace(otherLeader => partition.leader.executeInProtectedSpace(myLeader =>
            if(otherLeader.location.isInferiorThan(myLeader.location)) {
              otherLeader.ref ! MergeWithThisPartition(partition)
            } else {
              myLeader.ref ! MergeWithThisPartition(remotePartition)
            }
          ))
        case None =>
          currentPartition = Some(remotePartition)
          lastPartitionUpdate = Some(new Date())
      }

    case Downgrade() =>
      isLeading = false

    case MergeWithThisPartition(remotePartition: Partition) =>

      currentPartition match {
        case Some(partition) =>
          log.info(s"$currentNode: need to merge $partition with $remotePartition")
          remotePartition.leader.executeInProtectedSpace(concurrentLeader => concurrentLeader.ref ! Downgrade())
          currentPartition = Some(Partition(partition.leader, partition.nodes ::: remotePartition.nodes))
          lastPartitionUpdate = Some(new Date())
          notifyPartitionMembers()
        case None =>
      }


    case CheckTimeout() if(currentPartition != None) =>
      val now: Date = new Date()
      val duration: Duration = (now.getTime - lastPartitionUpdate.get.getTime) milliseconds

      if (duration > DvmsActor.partitionUpdateTimeout) {
        exitPartition()
      }

    case Exit() =>
      currentPartition = None
      lastPartitionUpdate = None
      log.info(s"[$currentNode]: i'm free")

    case msg => currentNode.ref.forward(msg)
  }

  // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
  currentNode.ref ! Register((e: MonitorEvent.CpuViolation) => {
    currentPartition match {
      case None =>
        startIterativeScheduling()
      case _ =>
    }
  })

  // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
  context.system.scheduler.schedule(0 milliseconds,
    500 milliseconds,
    self,
    CheckTimeout())
}


