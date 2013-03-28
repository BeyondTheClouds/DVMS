package dvms.dvms

import akka.actor.{ActorLogging, Actor}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import org.bbk.AkkaArc.util.NodeRef
import org.bbk.AkkaArc.notification.{WantsToRegister, ToNotificationActor}
import dvms.entropy.EntropyComputeReconfigurePlan
import dvms.monitor.CpuViolation
import parallel.Future

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/14/13
 * Time: 4:43 PM
 * To change this template use File | Settings | File Templates.
 */


// Routing messages
case class ToMonitorActor(msg:Any)
case class ToDvmsActor(msg:Any)
case class ToEntropyActor(msg:Any)



case class ThisIsYourNeighbor(neighbor:NodeRef)
case class CpuViolationDetected()

// Message used for the base of DVMS
case class DissolvePartition()
case class DvmsPartition(leader:NodeRef, initiator:NodeRef, nodes:List[NodeRef], state:DvmsPartititionState)

case class TransmissionOfAnISP(currentPartition:DvmsPartition)
case class IAmTheNewLeader(partition:DvmsPartition, firstOut:NodeRef)

// Message used for the merge of partitions
case class IsStillValid(partition:DvmsPartition)
case class CanIMergePartitionWithYou(partition:DvmsPartition, contact:NodeRef)
//case class LockForMerging(ref:NodeRef)
//case class MakeAnElection()
//case class MergeOurPartitions(partition:DvmsPartition)

case class WhoMerge(otherNode:NodeRef)

class DvmsPartititionState(val name:String) {

   def getName():String = name

   def isEqualTo(a: DvmsPartititionState): Boolean = {
      this.name == a.getName
   }

   def isDifferentFrom(a: DvmsPartititionState): Boolean = {
      this.name != a.getName
   }
}

case class Created() extends DvmsPartititionState("Created")
case class Blocked() extends DvmsPartititionState("Blocked")
case class Growing() extends DvmsPartititionState("Growing")
case class Destroyed() extends DvmsPartititionState("Destroyed")

class DvmsActor(applicationRef:NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   // by default, a node is in a ring containing only it self
   var nextDvmsNode:NodeRef = applicationRef

   // Variables that are specific to a node member of a partition
   var firstOut:Option[NodeRef] = None
   var currentPartition:Option[DvmsPartition] = None

   def mergeWithThisPartition(partition:DvmsPartition) {

      log.info(s"merging $partition with ${currentPartition.get}")
      currentPartition = Some(DvmsPartition(
        currentPartition.get.leader,
        currentPartition.get.initiator,
        currentPartition.get.nodes ::: partition.nodes,
        Growing()))

      currentPartition.get.nodes.foreach(node => {
        node.ref ! ToDvmsActor(IAmTheNewLeader(currentPartition.get, firstOut.get))
      })

      if(computeEntropy()) {
        log.info(s"(1) the partition $currentPartition is enough to reconfigure")

        currentPartition.get.nodes.foreach( node => {
          node.ref ! ToDvmsActor(DissolvePartition())
        })
      } else {

        log.info(s"(1a) the partition $currentPartition is not enough to reconfigure," +
          s" I try to find another node for the partition, deadlock? ${partition.nodes.contains(firstOut)}")

        log.info(s"(1) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
        firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
      }
   }


   var lockedForFusion:Boolean = false

   override def receive = {


      case IsStillValid(partition) => {
         var result:Boolean = false

         if(lockedForFusion) {
            result = false
         } else {
            currentPartition match {
               case Some(p) => {
                  if(p.nodes.size == partition.nodes.size) {
                     result = p.nodes.forall(n => {
                        partition.nodes.contains(n)
                     })
                  } else {
                     result = false
                  }
               }
               case None => result = false
            }
         }


         sender ! result
      }

      case CanIMergePartitionWithYou(partition, contact) => {

//         log.info(s"$applicationRef: $contact asked me if i am locked: $lockedForFusion")

         sender ! (!lockedForFusion)

         if(!lockedForFusion) {
           lockedForFusion = true
         }
      }

      case DissolvePartition() =>  {

//         currentPartition match {
//            case Some(p) => {
//               log.info(s"$applicationRef: I dissolve the partition $p")
//            }
//            case None =>  log.info(s"$applicationRef: I dissolve the partition None")
//         }


         firstOut = None
         currentPartition = None
         this.lockedForFusion = false
      }

      case IAmTheNewLeader(partition, firstOutOfTheLeader) => {

         log.info(s"$applicationRef: ${partition.leader} is the new leader of $partition")

         this.currentPartition = Some(partition)
         this.lockedForFusion = false

         firstOut match {
            case None => firstOut = Some(firstOutOfTheLeader)
            case Some(node) => {
               if (firstOut.get.location isEqualTo partition.leader.location) {
                  firstOut = Some(firstOutOfTheLeader)
               }
            }
         }
      }

      case msg@TransmissionOfAnISP(partition) => {

         log.info(s"received an ISP: $msg")

         currentPartition match {
            case Some(p) => {
               if(partition.initiator.location isDifferentFrom applicationRef.location) {

                  if((currentPartition.get.state isEqualTo Blocked()) && (partition.state isEqualTo Blocked())) {

                     log.info(s"we can merge $currentPartition and $partition")

                    // the firstOut is in the same partition
                     if(currentPartition.get.initiator.location isEqualTo partition.initiator.location) {

                        currentPartition.get.nodes.foreach( node => {
                        node.ref ! ToDvmsActor(DissolvePartition())
                        })

                     } else {

                        if(currentPartition.get.initiator.location isSuperiorThan partition.initiator.location) {
                           log.info(s"$applicationRef is waiting for $partition")

                           lockedForFusion = true

                           for {
                              locked <- sender ? CanIMergePartitionWithYou(currentPartition.get, applicationRef)
                           } yield {

                              log.info(s"$applicationRef got a result $locked")

                              locked match {
                                 case true => {
                                    lockedForFusion = true

//                                    log.info(s"$applicationRef is effectively merging partition $currentPartition with $partition")

                                    mergeWithThisPartition(partition)
                                 }
                                 case false =>
                              }
                           }
                        }
                     }

                  } else {

                     log.info(s"I'm not in a Blocked state," +
                       s" but I belong to a partition, so I forward $currentPartition")
                     firstOut.get.ref.forward(ToDvmsActor(msg))
                  }

               } else {
                  log.info(s"the partition $partition got back to it's initiator, firstout:${firstOut}")

                  if (!(firstOut.get.location isEqualTo applicationRef.location)) {
                    partition.state match {
                      case Blocked() => {
                        context.system.scheduler.scheduleOnce(
                          1 second,
                          self,
                          msg)
                      }
                      case _ => {
                        currentPartition = Some(DvmsPartition(
                          currentPartition.get.leader,
                          currentPartition.get.initiator,
                          currentPartition.get.nodes,
                          Blocked()))

                        partition.nodes.foreach(n => {
                          n.ref ! ToDvmsActor(IAmTheNewLeader(currentPartition.get, firstOut.get))
                        })


                        log.info(s"(2) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
                        firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
                      }
                    }
                  } else {
                    currentPartition.get.nodes.foreach( node => {
                      node.ref ! ToDvmsActor(DissolvePartition())
                    })
                  }

               }
            }

            case None => {


               var partitionIsStillValid:Boolean = true

               if(partition.state isEqualTo Blocked()) {
                  try {
                     partitionIsStillValid = Await.result(partition.leader.ref ? ToDvmsActor(IsStillValid(partition)), 1 second).asInstanceOf[Boolean]
                  } catch {
                     case e => partitionIsStillValid = false
                  }

               }

               if(partitionIsStillValid) {

                  currentPartition = Some(DvmsPartition(applicationRef, partition.initiator,
                     applicationRef::partition.nodes, partition.state))
                  firstOut = Some(nextDvmsNode)



                  log.info(s"(5) $applicationRef: $currentPartition is valid")

                  partition.nodes.foreach( node => {
                     //                  log.info(s"$applicationRef: sending ${IAmTheNewLeader(currentPartition.get, firstOut.get)}")
                     node.ref ! ToDvmsActor(IAmTheNewLeader(currentPartition.get, firstOut.get))
                  })

                  log.info(s"$applicationRef: sending ${IAmTheNewLeader(currentPartition.get, firstOut.get)}")

                  if(computeEntropy()) {
                     log.info(s"(2) the partition $currentPartition is enough to reconfigure")

                     currentPartition.get.nodes.foreach( node => {
                        node.ref ! ToDvmsActor(DissolvePartition())
                     })
                  } else {
                     log.info(s"the partition $currentPartition is not enough to reconfigure," +
                       s" I try to find another node for the partition")

                     log.info(s"(3) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
                     firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
                  }
               }
            }
         }
      }

      case CpuViolationDetected() => {
         currentPartition match {
            case None => {
               log.info("Dvms has detected a new cpu violation")
               firstOut = Some(nextDvmsNode)

               currentPartition = Some(DvmsPartition(applicationRef, applicationRef, List(applicationRef), Growing()))

               log.info(s"(4) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
               nextDvmsNode.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
            }
            case _ =>
         }
      }

      case ThisIsYourNeighbor(node) => {
         log.info(s"my neighbor has changed: $node")
         nextDvmsNode = node
      }

      case msg => {
         log.warning(s"received an unknown message <$msg>")
         applicationRef.ref.forward(msg)
      }
   }


   def computeEntropy():Boolean =  {

      return Await.result(
        applicationRef.ref ? ToEntropyActor(EntropyComputeReconfigurePlan(currentPartition.get.nodes)),
        1 second).asInstanceOf[Boolean]
   }

   // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
   applicationRef.ref ! ToNotificationActor(WantsToRegister(applicationRef, new CpuViolation(), n => {
      n.ref ! ToDvmsActor(CpuViolationDetected())
   }))
}


