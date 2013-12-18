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
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.{Future, Await, ExecutionContext}
import java.util.concurrent.Executors
import org.discovery.AkkaArc.util.NodeRef
import org.discovery.AkkaArc.notification.{WantsToRegister}
import org.discovery.dvms.monitor.{MonitorEventsTypes}
import java.util.{Date, UUID}

import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsModel.DvmsPartititionState._
import org.discovery.AkkaArc.PeerActorProtocol.ToNotificationActor
import org.discovery.dvms.log.LoggingProtocol._
import org.discovery.dvms.configuration.ExperimentConfiguration
import org.discovery.dvms.entropy.EntropyProtocol.{MigrateVirtualMachine, EntropyComputeReconfigurePlan}
import org.discovery.DiscoveryModel.model.ReconfigurationModel._

object DvmsActor {
  val partitionUpdateTimeout: FiniteDuration = 3500 milliseconds
}

class DvmsActor(applicationRef: NodeRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(2 seconds)
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  // by default, a node is in a ring containing only it self
  var nextDvmsNode: NodeRef = applicationRef

  // Variables that are specific to a node member of a partition
  var firstOut: Option[NodeRef] = None
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
      log.info(s"(a) $applicationRef: sending a new version of the partition ${IAmTheNewLeader(currentPartition.get, firstOut.get)} to $node")
      node.ref ! IAmTheNewLeader(currentPartition.get, firstOut.get)
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

        log.info(s"(1) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
        firstOut.get.ref !TransmissionOfAnISP(currentPartition.get)
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
              firstOut = Some(nextDvmsNode)

              lastPartitionUpdateDate = Some(new Date())

              log.info(s"$applicationRef: A node crashed ($node), I am becoming the new leader of $currentPartition")

              newPartition.nodes.foreach(node => {
                node.ref ! IAmTheNewLeader(newPartition, firstOut.get)
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


      firstOut = None
      currentPartition = None
      lockedForFusion = false
      lastPartitionUpdateDate = None

      // Alert LogginActor that the current node is free
      applicationRef.ref ! IsFree(ExperimentConfiguration.getCurrentTime())
    }

    case IAmTheNewLeader(partition, firstOutOfTheLeader) => {

      log.info(s"$applicationRef: ${partition.leader} is the new leader of $partition")

      currentPartition = Some(partition)
      lastPartitionUpdateDate = Some(new Date())

      lockedForFusion = false

      firstOut match {
        case None => firstOut = Some(firstOutOfTheLeader)
        case Some(node) => {
          if (firstOut.get.location isEqualTo partition.leader.location) {
            firstOut = Some(firstOutOfTheLeader)
          }
        }
      }
    }

    case ChangeTheStateOfThePartition(newState) => {
      changeCurrentPartitionState(newState)
    }

    case msg@TransmissionOfAnISP(partition) => {

      log.info(s"received an ISP: $msg @$currentPartition")
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

            firstOut.get.ref ! TransmissionOfAnISP(currentPartition.get)

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

            log.info(s"$applicationRef: forwarding $msg to $firstOut")

            // I forward the partition to the current firstOut
            firstOut.get.ref.forward(msg)

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

                  // the order between nodes is not respected, the ISP should be forwarded
                  log.info(s"$applicationRef: order between nodes is not respected, I forward $partition to $firstOut")
                  firstOut.get.ref.forward(msg)
                }

              }

              case Finishing() =>  {
                log.info(s"$applicationRef: forwarding $msg to $firstOut")
                firstOut.get.ref.forward(msg)
              }

              case Growing() => {
                log.info(s"$applicationRef: forwarding $msg to $firstOut")
                firstOut.get.ref.forward(msg)
              }
            }
          }
          // other case... (if so)
          case _ => {

          }
        }

        case None => {

          var partitionIsStillValid: Boolean = true

          if (partition.state isEqualTo Blocked()) {
            try {
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
            firstOut = Some(nextDvmsNode)
            lastPartitionUpdateDate = Some(new Date())

            // Alert LogginActor that the current node is booked in a partition
            applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

            partition.nodes.foreach(node => {
              log.info(s"$applicationRef: sending the $newPartition to $node")
              node.ref ! IAmTheNewLeader(newPartition, firstOut.get)
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
                firstOut = Some(nextDvmsNode)


                // Alert LogginActor that the current node is booked in a partition
                applicationRef.ref ! IsBooked(ExperimentConfiguration.getCurrentTime())

                partition.nodes.foreach(node => {
                  log.info(s"$applicationRef: sending the $newPartition to $node")
                  node.ref ! IAmTheNewLeader(newPartition, firstOut.get)
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

                log.info(s"(A) Partition was not enough to reconfigure, forwarding to ${firstOut.get}")
                // it was not enough: the partition is forwarded to the firstOut
                firstOut.get.ref ! TransmissionOfAnISP(currentPartition.get)
              }
            }
          } else {
            log.warning(s"$applicationRef: $partition is no more valid (source: ${partition.initiator})")
          }
        }
      }
    }

    case CpuViolationDetected() => {

      // Alert LogginActor that a violation has been detected
      applicationRef.ref ! ViolationDetected(ExperimentConfiguration.getCurrentTime())

      currentPartition match {
        case None => {
          log.info("Dvms has detected a new cpu violation")
          printDetails()

          firstOut = Some(nextDvmsNode)

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

          log.info(s"$applicationRef transmitting a new ISP ${currentPartition.get} to neighbour: $nextDvmsNode")
          nextDvmsNode.ref ! TransmissionOfAnISP(currentPartition.get)
        }
        case _ =>
      }
    }

    case ThisIsYourNeighbor(node) => {
      log.info(s"my neighbor has changed: $node")
      nextDvmsNode = node
    }

    case YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) => {

      (firstOut, oldNeighbor) match {
        case (Some(fo), Some(n)) if (fo.location isEqualTo n.location) => firstOut = Some(newNeighbor)
        case _ =>
      }
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


    val computationResult = Await.result(entropyComputeAsFuture, 2 seconds)

    log.info("computeEntropy (2)")

    computationResult
  }

  def applySolution(solution: ReconfigurationSolution) {

    import scala.collection.JavaConversions._

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

            partition.nodes.foreach(node => {
              log.info(s"$applicationRef: updating the $newPartition to $node (to prevent timeout)")
              node.ref ! IAmTheNewLeader(newPartition, firstOut.get)
            })

            Thread.sleep(500)
          }
        }

        solution.actions.keySet().foreach(key => {
          solution.actions.get(key).foreach(action => {


            action match {
              case MakeMigration(from, to, vmName) =>
                log.info(s"preparing migration of $vmName")


                var fromNodeRef: Option[NodeRef] = None
                var toNodeRef: Option[NodeRef] = None

                partition.nodes.foreach(nodeRef => {
                  log.info(s"check ${nodeRef.location.getId} == ( $from | $to ) ?")

                  if (s"${nodeRef.location.getId}" == from) {
                    fromNodeRef = Some(nodeRef)
                  }

                  if (s"${nodeRef.location.getId}" == to) {
                    toNodeRef = Some(nodeRef)
                  }
                })

                (fromNodeRef, toNodeRef) match {
                  case (Some(from), Some(to)) =>
                    log.info(s"send migrate message {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName}")

                    var migrationSucceed = false

                    implicit val timeout = Timeout(300 seconds)
                    val future = (from.ref ? MigrateVirtualMachine(vmName, to.location)).mapTo[Boolean]

                    try {
                      migrationSucceed  = Await.result(future, timeout.duration)
                    } catch {
                      case e: Throwable =>
                        e.printStackTrace()
                        migrationSucceed = false
                    }

                    log.info(s"migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName} : result => $migrationSucceed")
                  case _ =>
                    log.info(s"migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName} : failed")
                }
              case otherAction =>
                log.info(s"unknownAction $otherAction")
            }




          })
        })

        continueToUpdatePartition = false


      case None =>
        log.info("cannot apply reconfigurationSolution: current partition is undefined")
    }
  }


  def printDetails() {
    //      log.info(s"currentPartition: $currentPartition")
    //      log.info(s"firstOut: $firstOut")
    //      log.info(s"DvmsNextNode: $nextDvmsNode")
    //      log.info(s"lastPartitionUpdate: $lastPartitionUpdateDate")
    //      log.info(s"lockedForFusion: $lockedForFusion")
  }

  // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
  applicationRef.ref ! ToNotificationActor(
    WantsToRegister(
      applicationRef,
      MonitorEventsTypes.OnCpuViolation(), (n, e) => {
        n.ref ! CpuViolationDetected()
      }
    )
  )

  // registering a timer that will check if the node is in a partition and then if there is an activity from
  // this partition

  context.system.scheduler.schedule(0 milliseconds,
    500 milliseconds,
    self,
    CheckTimeout())


}


