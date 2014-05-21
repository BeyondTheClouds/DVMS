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
import concurrent.{Future, Await, ExecutionContext}
import java.util.concurrent.Executors
import org.discovery.peeractor.util.{MayFail, NodeRef}
import java.util.{Date, UUID}

import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsModel.DvmsPartititionState._
import org.discovery.dvms.log.LoggingProtocol._
import org.discovery.dvms.configuration.ExperimentConfiguration
import org.discovery.dvms.entropy.EntropyProtocol.EntropyComputeReconfigurePlan
import org.discovery.DiscoveryModel.model.ReconfigurationModel._
import org.discovery.dvms.monitor.MonitorEvent
import org.discovery.peeractor.notification.NotificationActorProtocol.Register
import org.discovery.peeractor.overlay.OverlayService
import org.discovery.dvms.utility.PlanApplicator

object DvmsActor {
  val partitionUpdateTimeout: FiniteDuration = 3500 milliseconds
}

class DvmsActor(applicationRef: NodeRef, overlayService: OverlayService, planApplicator: PlanApplicator) extends Actor with ActorLogging with SchedulerActor {

  implicit val timeout = Timeout(2 seconds)
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  // by default, a node is in a ring containing only it self
//  var nextDvmsNode: NodeRef = applicationRef

  // Variables that are specific to a node member of a partition
//  var firstOut: Option[NodeRef] = None
  def firstOut:Option[NodeRef] = {

    val future = currentPartition match {
      case Some(partition) =>
        overlayService.giveSomeNeighbourOutside(partition.nodes.map(n => MayFail.protect(n)))
      case None =>
        overlayService.giveSomeNeighbour()
    }

    Await.result(future, 2 seconds) match {
      case Some(mayFailedNode: MayFail[NodeRef]) => {

        val futureResult = mayFailedNode.executeInProtectedSpace((n:NodeRef) => n)
        val result = Await.result(futureResult, 2 seconds)
        Some(result)
      }
      case _ => None
    }

  }

  var currentPartition: Option[DvmsPartition] = None

  // Variables used for resiliency
  var lastPartitionUpdateDate: Option[Date] = None

  var lockedForFusion: Boolean = false

  def mergeWithThisPartition(partition: DvmsPartition) {

    log.info(s"merging $partition with ${currentPartition.get}")
    currentPartition = Some(DvmsPartition(
      currentPartition.get.leader,
      currentPartition.get.initiator,
      currentPartition.get.nodes ::: partition.nodes,
      Growing(), UUID.randomUUID()))

    lastPartitionUpdateDate = Some(new Date())

    currentPartition.get.nodes.foreach(node => {
      log.info(s"(a) $applicationRef: sending a new version of the partition ${IAmTheNewLeader(currentPartition.get)} to $node")
      node.ref ! IAmTheNewLeader(currentPartition.get)
    })

    val computationResult = computeEntropy()
    computationResult match{
      case solution: ReconfigurationSolution => {
        log.info(s"(1) the partition $currentPartition is enough to reconfigure")

        applySolution(solution)


        log.info(s"(a) I decide to dissolve $currentPartition")
        currentPartition.get.nodes.foreach(node => {
          node.ref ! DissolvePartition("violation resolved")
        })
      }
      case ReconfigurationlNoSolution() => {

        log.info(s"(1a) the partition $currentPartition is not enough to reconfigure," +
          s" I try to find another node for the partition, deadlock? ${currentPartition.get.nodes.contains(firstOut)}")

        firstOut match {
          case Some(existingNode) =>
            log.info(s"(Y) $applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
            existingNode.ref ! TransmissionOfAnISP(currentPartition.get)
          case None =>
            log.info(s"(Y) $applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
        }
      }
    }
  }

  def changeCurrentPartitionState(newState: DvmsPartititionState) {
    currentPartition match {
      case Some(partition) =>

         currentPartition = Some(DvmsPartition(
            partition.leader,
            partition.initiator,
            partition.nodes,
            newState,
            partition.id
         ))

         lastPartitionUpdateDate = Some(new Date())

      case None =>
    }
  }

  def remoteNodeFailureDetected(node: NodeRef) {
    currentPartition match {
      case Some(p) => {
        if (p.nodes.contains(node)) {
          node match {
            // the initiator of the partition has crashed
            case node: NodeRef if (node.location isEqualTo p.initiator.location) => {

              log.info(s"$applicationRef: The initiator ($node) has crashed, I am becoming the new leader of $currentPartition")

              // the partition will be dissolved
              p.nodes.filterNot(n => n.location isEqualTo node.location).foreach(n => {
                n.ref ! DissolvePartition("initiator crashed")
              })
            }

            // the leader or a normal node of the partition has crashed
            case node: NodeRef => {

              // creation of a new partition without the crashed node
              val newPartition: DvmsPartition = new DvmsPartition(
                applicationRef,
                p.initiator,
                p.nodes.filterNot(n => n.location isEqualTo node.location),
                p.state,
                UUID.randomUUID()
              )

              currentPartition = Some(newPartition)
//              firstOut = Some(nextDvmsNode)

              lastPartitionUpdateDate = Some(new Date())

              log.info(s"$applicationRef: A node crashed ($node), I am becoming the new leader of $currentPartition")

              newPartition.nodes.foreach(node => {
                node.ref ! IAmTheNewLeader(newPartition)
              })
            }
          }
        }
      }
      case None =>
    }

  }

  override def receive = {

    case IsThisVersionOfThePartitionStillValid(partition) => {
      currentPartition match {
        case Some(p) => sender ! partition.id.equals(currentPartition.get.id)
        case None => sender ! false
      }
    }

    case FailureDetected(node) => {
      remoteNodeFailureDetected(node)
    }


    case CheckTimeout() => {

      //         log.info(s"$applicationRef: check if we have reach the timeout of partition")

//      log.info("checkTimeout")
      printDetails()

      (currentPartition, lastPartitionUpdateDate) match {
        case (Some(p), Some(d)) => {

          val now: Date = new Date()
          val duration: Duration = (now.getTime - d.getTime) milliseconds

          if (duration > DvmsActor.partitionUpdateTimeout) {

            log.info(s"$applicationRef: timeout of partition has been reached: I dissolve everything")

            p.nodes.foreach(n => {
              n.ref ! DissolvePartition("timeout")
            })
          }

        }
        case _ =>
      }
    }


    case CanIMergePartitionWithYou(partition, contact) => {

      sender ! (!lockedForFusion)

      if (!lockedForFusion) {
        lockedForFusion = true
      }
    }

    case DissolvePartition(reason) => {

      currentPartition match {
        case Some(p) =>
          log.info(s"$applicationRef: I dissolve the partition $p, because <$reason>")
        case None =>
          log.info(s"$applicationRef: I dissolve the partition None, because <$reason>")
      }


//      firstOut = None
      currentPartition = None
      lockedForFusion = false
      lastPartitionUpdateDate = None

      // Alert LogginActor that the current node is free
      applicationRef.ref ! IsFree(ExperimentConfiguration.getCurrentTime())
    }

    case IAmTheNewLeader(partition) => {

      log.info(s"$applicationRef: ${partition.leader} is the new leader of $partition")

      val outdatedUpdate: Boolean = (currentPartition, partition.state) match {
        case (None, Finishing()) => true
        case _ => false
      }

      if(!outdatedUpdate) {
        currentPartition = Some(partition)
        lastPartitionUpdateDate = Some(new Date())

        lockedForFusion = false

//        firstOut match {
//          case None => firstOut = Some(firstOutOfTheLeader)
//          case Some(node) => {
//            if (firstOut.get.location isEqualTo partition.leader.location) {
//              firstOut = Some(firstOutOfTheLeader)
//            }
//          }
//        }
      }
    }

    case ChangeTheStateOfThePartition(newState) => {
      changeCurrentPartitionState(newState)
    }

    case msg@TransmissionOfAnISP(partition) => {

      log.info(s"received an ISP: $msg @$currentPartition and @$firstOut")
      printDetails()

      currentPartition match {
        case Some(p) => p match {
          // the ISP went back to it's initiator for the first time
          case _ if ((partition.initiator.location isEqualTo p.initiator.location)
            && (partition.state isEqualTo Growing())) => {

            log.info(s"$applicationRef: the partition $partition went back to it's initiator" +
              s" with a Growing state: it becomes blocked :s")


            changeCurrentPartitionState(Blocked())

            // the state of the current partition become Blocked()
            p.nodes.foreach(node => {
              node.ref ! ChangeTheStateOfThePartition(Blocked())
            })


            firstOut match {
              case Some(existingNode) =>
                log.info(s"(X) $applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
                existingNode.ref ! TransmissionOfAnISP(currentPartition.get)
              case None =>
                log.info(s"(X) $applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
            }



          }
          // the ISP went back to it's initiator for the second time
          case _ if ((partition.initiator.location isEqualTo p.initiator.location)
            && (partition.state isEqualTo Blocked())) => {

            log.info(s"$applicationRef: the partition $partition went back to it's initiator" +
              s" with a Blocked state: it dissolve it :(")
            // the currentPartition should be dissolved
            p.nodes.foreach(node => {
              node.ref ! DissolvePartition("back to initiator with a blocked state")
            })

          }
          // the incoming ISP is different from the current ISP and the current state is not Blocked
          case _ if ((partition.initiator.location isDifferentFrom p.initiator.location)
            && (p.state isEqualTo Growing())) => {

            // I forward the partition to the current firstOut
            firstOut match {
              case Some(existingNode) =>
                log.info(s"$applicationRef: forwarding $msg to $firstOut")
                firstOut.get.ref.forward(msg)
              case None =>
                log.info(s"$applicationRef: cannot forward to firstOut")
            }


          }
          // the incoming ISP is different from the current ISP and the current state is Blocked
          //   ==> we may merge!
          case _ if ((partition.initiator.location isDifferentFrom p.initiator.location)
            && (p.state isEqualTo Blocked())) => {

            partition.state match {
              case Blocked() => {

                if (partition.initiator.location isSuperiorThan p.initiator.location) {
                  log.info(s"$applicationRef: may merge $p with $partition")


                  lockedForFusion = true
                  val willMerge: Boolean = Await.result(sender ? CanIMergePartitionWithYou(p, applicationRef), 1 second).asInstanceOf[Boolean]

                  log.info(s"$applicationRef got a result $willMerge")

                  willMerge match {
                    case true => {
                      lockedForFusion = true

                      log.info(s"$applicationRef is effectively merging partition $p with $partition")

                      mergeWithThisPartition(partition)
                    }
                    case false =>
                  }
                } else {

                  firstOut match {
                    case Some(existingNode) =>
                      // the order between nodes is not respected, the ISP should be forwarded
                      log.info(s"$applicationRef: order between nodes is not respected, I forward $partition to $existingNode")
                      existingNode.ref.forward(msg)
                    case None =>
                      // the order between nodes is not respected, the ISP should be forwarded
                      log.info(s"bug:$applicationRef: cannot forward $partition to $firstOut")
                  }

                }

              }

              case Finishing() =>  {
                firstOut match {
                  case Some(existingNode) =>
                    log.info(s"$applicationRef: forwarding $msg to $firstOut")
                    firstOut.get.ref.forward(msg)
                  case None =>
                    log.info(s"$applicationRef: cannot forward to firstOut")
                }
              }

              case Growing() => {

                firstOut match {
                  case Some(existingNode) =>
                    log.info(s"$applicationRef: forwarding $msg to $firstOut")
                    firstOut.get.ref.forward(msg)
                  case None =>
                    log.info(s"$applicationRef: cannot forward to firstOut")
                }

              }
            }
          }
          // other case... (if so)
          case _ => {
            firstOut match {
              case Some(existingNode) =>
                log.info(s"$applicationRef: forwarding $msg to $firstOut (forward-bis)")
                firstOut.get.ref.forward(msg)
              case None =>
                log.info(s"$applicationRef: cannot forward to firstOut (forward-bis)")
            }
          }
        }

        case None => {

          var partitionIsStillValid: Boolean = true

          if (partition.state isEqualTo Blocked()) {
            try {

              // TODO: there was a mistake reported here!

              partitionIsStillValid = Await.result(partition.initiator.ref ?
                IsThisVersionOfThePartitionStillValid(partition), 1 second
              ).asInstanceOf[Boolean]
            } catch {
              case e: Throwable => {
                log.info(s"Partition $partition is no more valid (Exception")
                e.printStackTrace()
                partitionIsStillValid = false
              }
            }

          }

          if (partitionIsStillValid) {

            // the current node is becoming the leader of the incoming ISP
            log.info(s"$applicationRef: I am becoming the new leader of $partition")

            val newPartition: DvmsPartition = new DvmsPartition(
              applicationRef,
              partition.initiator,
              applicationRef :: partition.nodes,
              Growing(),
              UUID.randomUUID()
            )

            currentPartition = Some(newPartition)
//            firstOut = Some(nextDvmsNode)
            lastPartitionUpdateDate = Some(new Date())

            // Alert LogginActor that the current node is booked in a partition
            applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

            partition.nodes.foreach(node => {
              log.info(s"$applicationRef: sending the $newPartition to $node")
              node.ref ! IAmTheNewLeader(newPartition)
            })

            lastPartitionUpdateDate = Some(new Date())

            // ask entropy if the new partition is enough to resolve the overload

            val computationResult = computeEntropy()

            computationResult match {
              case solution: ReconfigurationSolution => {
                log.info("(A) Partition was enough to reconfigure ")


                val newPartition: DvmsPartition = new DvmsPartition(
                  applicationRef,
                  partition.initiator,
                  applicationRef :: partition.nodes,
                  Finishing(),
                  UUID.randomUUID()
                )

                currentPartition = Some(newPartition)
//                firstOut = Some(nextDvmsNode)


                // Alert LogginActor that the current node is booked in a partition
                applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

                partition.nodes.filter(n => n.location.isDifferentFrom(applicationRef.location)).foreach(node => {
                  log.info(s"$applicationRef: sending the $newPartition to $node")
                  node.ref ! IAmTheNewLeader(newPartition)
                })

                lastPartitionUpdateDate = Some(new Date())

                // Applying the reconfiguration plan
                applySolution(solution)

                // it was enough: the partition is no more useful
                currentPartition.get.nodes.foreach(node => {
                  node.ref ! DissolvePartition("violation resolved")
                })
              }
              case ReconfigurationlNoSolution() => {

                firstOut match {
                  case Some(existingNode) =>
                    log.info(s"(A) Partition was not enough to reconfigure, forwarding to $existingNode")
                    existingNode.ref ! TransmissionOfAnISP(currentPartition.get)
                  case None =>
                    log.info(s"(A) $applicationRef : ${currentPartition.get} was not forwarded to nobody")
                }
              }
            }
          } else {
            log.warning(s"$applicationRef: $partition is no more valid (source: ${partition.initiator})")
          }
        }
      }
    }

    case CpuViolationDetected() => {


    }

    case ThisIsYourNeighbor(node) => {
//      log.info(s"my neighbor has changed: $node")
//      nextDvmsNode = node
    }

    case YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) => {

//      (firstOut, oldNeighbor) match {
//        case (Some(fo), Some(n)) if (fo.location isEqualTo n.location) => firstOut = Some(newNeighbor)
//        case _ =>
//      }
    }

    case msg => applicationRef.ref.forward(msg)
  }


  def computeEntropy(): ReconfigurationResult = {

    log.info("computeEntropy (1)")

    val entropyComputeAsFuture: Future[ReconfigurationResult] = (
      (applicationRef.ref ? EntropyComputeReconfigurePlan(currentPartition.get.nodes)) recover {
         case _: Throwable => ReconfigurationlNoSolution()
      }
   ).mapTo[ReconfigurationResult]


    val computationResult = Await.result(entropyComputeAsFuture, 4 seconds)

    log.info("computeEntropy (2)")

    computationResult
  }

  def applySolution(solution: ReconfigurationSolution) {


    currentPartition match {
      case Some(partition) =>

        var continueToUpdatePartition: Boolean = true

        Future {
          while(continueToUpdatePartition) {

            val newPartition: DvmsPartition = new DvmsPartition(
              applicationRef,
              partition.initiator,
              partition.nodes,
              partition.state,
              UUID.randomUUID()
            )

            currentPartition = Some(newPartition)


            log.info(s"$applicationRef: updating the $newPartition to nodes (to prevent timeout)")

            partition.nodes.foreach(node => {
              node.ref ! IAmTheNewLeader(newPartition)
            })

            Thread.sleep(500)
          }
        }

        planApplicator.applySolution(applicationRef, solution, partition.nodes)

        continueToUpdatePartition = false

        partition.nodes.foreach(node => {
          log.info(s"$applicationRef: reconfiguration plan has been applied, dissolving partition $partition")
          node.ref ! DissolvePartition("Reconfiguration plan has been applied")
        })


      case None =>
        log.info("cannot apply reconfigurationSolution: current partition is undefined")
    }
  }


  def printDetails() {
//    log.info(s"currentPartition: $currentPartition")
//    log.info(s"firstOut: $firstOut")
//    log.info(s"DvmsNextNode: $nextDvmsNode")
//    log.info(s"lastPartitionUpdate: $lastPartitionUpdateDate")
//    log.info(s"lockedForFusion: $lockedForFusion")
  }

  // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
  applicationRef.ref ! Register((e: MonitorEvent.CpuViolation) => {

    // Alert LogginActor that a violation has been detected
    applicationRef.ref ! ViolationDetected(ExperimentConfiguration.getCurrentTime())

    currentPartition match {
      case None => {
        log.info("Dvms has detected a new cpu violation")
        printDetails()

        //          firstOut = Some(nextDvmsNode)

        currentPartition = Some(DvmsPartition(
          applicationRef,
          applicationRef,
          List(applicationRef),
          Growing(),
          UUID.randomUUID()
        ))

        lastPartitionUpdateDate = Some(new Date())

        // Alert LogginActor that the current node is booked in a partition
        applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

        firstOut match {
          case Some(existingNode) =>
            log.info(s"$applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $existingNode")
            existingNode.ref ! TransmissionOfAnISP(currentPartition.get)
          case None =>
            log.info(s"$applicationRef transmitting a new ISP ${currentPartition.get} to nobody")
        }

      }
      case _ =>
        println(s"violation detected: this is my Partition [$currentPartition]")
    }
  })

  // registering a timer that will check if the node is in a partition and then if there is an activity from
  // this partition

  context.system.scheduler.schedule(0 milliseconds,
    500 milliseconds,
    self,
    CheckTimeout())


}


