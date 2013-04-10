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
import java.util.UUID

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

object DvmsPartition {
   def apply(leader:NodeRef, initiator:NodeRef, nodes:List[NodeRef], state:DvmsPartititionState):DvmsPartition = DvmsPartition(leader, initiator, nodes, state, UUID.randomUUID())
}

case class DvmsPartition(leader:NodeRef, initiator:NodeRef, nodes:List[NodeRef], state:DvmsPartititionState, id:UUID)

case class TransmissionOfAnISP(currentPartition:DvmsPartition)
case class IAmTheNewLeader(partition:DvmsPartition, firstOut:NodeRef)

// Message used for the merge of partitions
case class IsThisVersionOfThePartitionStillValid(partition:DvmsPartition)
case class CanIMergePartitionWithYou(partition:DvmsPartition, contact:NodeRef)
case class ChangeTheStateOfThePartition(newState:DvmsPartititionState)

// Message for the resiliency
case class EverythingIsOkToken(id:UUID)
case class VerifyEverythingIsOk(id:UUID, count:Int)

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

object DvmsActor {
   val PeriodOfPartitionNodeChecking:FiniteDuration = 1000 milliseconds
}

class DvmsActor(applicationRef:NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   // by default, a node is in a ring containing only it self
   var nextDvmsNode:NodeRef = applicationRef

   // Variables that are specific to a node member of a partition
   var firstOut:Option[NodeRef] = None
   var currentPartition:Option[DvmsPartition] = None

   // Variables for the resiliency
   var countOfCheck:Option[(UUID, Int)] = None

   def mergeWithThisPartition(partition:DvmsPartition) {

      log.info(s"merging $partition with ${currentPartition.get}")
      currentPartition = Some(DvmsPartition(
        currentPartition.get.leader,
        currentPartition.get.initiator,
        currentPartition.get.nodes ::: partition.nodes,
        Growing(), UUID.randomUUID()))

      currentPartition.get.nodes.foreach(node => {
         log.info(s"(a) $applicationRef: sending ${IAmTheNewLeader(currentPartition.get, firstOut.get)} to $node")
        node.ref ! ToDvmsActor(IAmTheNewLeader(currentPartition.get, firstOut.get))
      })

      if(computeEntropy()) {
        log.info(s"(1) the partition $currentPartition is enough to reconfigure")

         log.info(s"(a) I decide to dissolve $currentPartition")
        currentPartition.get.nodes.foreach( node => {
          node.ref ! ToDvmsActor(DissolvePartition())
        })
      } else {

         log.info(s"(1a) the partition $currentPartition is not enough to reconfigure," +
           s" I try to find another node for the partition, deadlock? ${currentPartition.get.nodes.contains(firstOut)}")

         log.info(s"(1) $applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
         firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
      }
   }

   def changeCurrentPartitionState(newState:DvmsPartititionState) {
      currentPartition match {
         case Some(partition) => currentPartition = Some(DvmsPartition(partition.leader, partition.initiator, partition.nodes, newState, partition.id))
         case None =>
      }
   }

   var lockedForFusion:Boolean = false

   override def receive = {


      case IsThisVersionOfThePartitionStillValid(partition) => {
         currentPartition match {
            case Some(p) => sender ! partition.id.equals(currentPartition.get.id)
            case None => sender ! false
         }
      }

      case VerifyEverythingIsOk(id, count) => {
         countOfCheck match {
            case Some((pid, pcount)) if(pid == id) => {
               // the predecessor in the partition order has crashed
               if(!(count > pcount)) {
                  currentPartition match {
                     case Some(p) => {
                        (p.nodes.indexOf(applicationRef) match {
                           case i:Int if(i == 0) => p.nodes(p.nodes.size - 1)
                           case i:Int => p.nodes(i-1)
                        }) match {
                           // the initiator of the partition has crashed
                           case node:NodeRef if(node.location isEqualTo p.initiator.location) => {

                              log.info(s"$applicationRef: The initiator has crashed, I am becoming the new leader of $currentPartition")

                              // the partition will be dissolved
                              p.nodes.filterNot(n => n.location isEqualTo node.location).foreach(n => {
                                 n.ref ! ToDvmsActor(DissolvePartition())
                              })
                           }

                           // the leader or a normal node of the partition has crashed
                           case node:NodeRef => {

                              // creation of a new partition without the crashed node
                              val newPartition:DvmsPartition = new DvmsPartition(p.initiator, applicationRef, p.nodes.filterNot(n => n.location isEqualTo node.location), p.state, UUID.randomUUID())

                              currentPartition = Some(newPartition)
                              firstOut = Some(nextDvmsNode)


                              log.info(s"$applicationRef: A node crashed, I am becoming the new leader of $currentPartition")

                              newPartition.nodes.foreach(node => {
                                 node.ref ! ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get))
                              })

                              countOfCheck = Some((newPartition.id, -1))
                              self ! VerifyEverythingIsOk(newPartition.id, 0)
                           }
                        }
                     }
                     case None =>
                  }
               }
            }
            case None =>
         }
      }

      case EverythingIsOkToken(id) => {
         currentPartition match {
            case Some(p) if(p.id == id) => {
               val nextNodeRef:NodeRef = p.nodes.indexOf(applicationRef) match {
                  case i:Int if(i == (p.nodes.size - 1)) => p.nodes(0)
                  case i:Int => p.nodes(i+1)
               }

               countOfCheck = Some((p.id, countOfCheck.get._2+1))

               context.system.scheduler.scheduleOnce((2*DvmsActor.PeriodOfPartitionNodeChecking), self, VerifyEverythingIsOk(id, countOfCheck.get._2+1))
               context.system.scheduler.scheduleOnce((DvmsActor.PeriodOfPartitionNodeChecking/(p.nodes.size)), nextNodeRef.ref, ToDvmsActor(EverythingIsOkToken(id)))
            }
            case _ =>
         }
      }

      case CanIMergePartitionWithYou(partition, contact) => {

         sender ! (!lockedForFusion)

         if(!lockedForFusion) {
           lockedForFusion = true
         }
      }

      case DissolvePartition() =>  {

         currentPartition match {
            case Some(p) => {
               log.info(s"$applicationRef: I dissolve the partition $p")
            }
            case None =>  log.info(s"$applicationRef: I dissolve the partition None")
         }


         firstOut = None
         currentPartition = None
         lockedForFusion = false
      }

      case IAmTheNewLeader(partition, firstOutOfTheLeader) => {

         log.info(s"$applicationRef: ${partition.leader} is the new leader of $partition")

         currentPartition = Some(partition)
         lockedForFusion = false

         countOfCheck = Some((partition.id,-1))

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

         currentPartition match {
            case Some(p) => p match {
               // the ISP went back to it's initiator for the first time
               case _ if((partition.initiator.location isEqualTo p.initiator.location)
                 && (partition.state isEqualTo Growing())) => {

                  log.info(s"$applicationRef: the partition $partition went back to it's initiator" +
                    s" with a Growing state: it becomes blocked :s")


                  changeCurrentPartitionState(Blocked())

                  // the state of the current partition become Blocked()
                  p.nodes.foreach(node => {
                     node.ref ! ToDvmsActor(ChangeTheStateOfThePartition(Blocked()))
                  })

                  firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))

               }
               // the ISP went back to it's initiator for the second time
               case _ if((partition.initiator.location isEqualTo p.initiator.location)
                 && (partition.state isEqualTo Blocked())) => {

                  log.info(s"$applicationRef: the partition $partition went back to it's initiator" +
                    s" with a Blocked state: it dissolve it :(")
                  // the currentPartition should be dissolved
                  p.nodes.foreach(node => {
                     node.ref ! ToDvmsActor(DissolvePartition())
                  })

               }
               // the incoming ISP is different from the current ISP and the current state is not Blocked
               case _ if((partition.initiator.location isDifferentFrom p.initiator.location)
                 && (p.state isEqualTo Growing())) => {

                  log.info(s"$applicationRef: forwarding $msg to $firstOut")

                  // I forward the partition to the current firstOut
                  firstOut.get.ref.forward(ToDvmsActor(msg))

               }
               // the incoming ISP is different from the current ISP and the current state is Blocked
               //   ==> we may merge!
               case _ if((partition.initiator.location isDifferentFrom p.initiator.location)
                 && (p.state isEqualTo Blocked())) => {

                  partition.state match {
                     case Blocked() => {

                        if(partition.initiator.location isSuperiorThan p.initiator.location) {
                           log.info(s"$applicationRef: may merge $p with $partition")


                           lockedForFusion = true
                           val willMerge:Boolean = Await.result(sender ? CanIMergePartitionWithYou(p, applicationRef), 1 second).asInstanceOf[Boolean]

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
                           firstOut.get.ref.forward(ToDvmsActor(msg))
                        }

                     }
                     case Growing() => {
                        log.info(s"$applicationRef: forwarding $msg to $firstOut")
                        firstOut.get.ref.forward(ToDvmsActor(msg))
                     }
                  }
               }
               // other case... (if so)
               case _ => {

               }
            }

            case None => {

               var partitionIsStillValid:Boolean = true

               if(partition.state isEqualTo Blocked()) {
                  try {
                     partitionIsStillValid = Await.result(partition.initiator.ref ? ToDvmsActor(IsThisVersionOfThePartitionStillValid(partition)), 1 second).asInstanceOf[Boolean]
                  } catch {
                     case e:Throwable => {
                        partitionIsStillValid = false
                     }
                  }

               }

               if(partitionIsStillValid) {

                  // the current node is becoming the leader of the incoming ISP
                  log.info(s"$applicationRef: I am becoming the new leader of $partition")

                  val newPartition:DvmsPartition = new DvmsPartition(partition.initiator, applicationRef, applicationRef::partition.nodes, Growing(), UUID.randomUUID())

                  currentPartition = Some(newPartition)
                  firstOut = Some(nextDvmsNode)

                  partition.nodes.foreach(node => {
                     node.ref ! ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get))
                  })

                  countOfCheck = Some((newPartition.id, -1))
                  self ! VerifyEverythingIsOk(newPartition.id, 0)

                  // ask entropy if the new partition is enough to resolve the overload
                  if(computeEntropy()) {

                     // it was enough: the partition is no more useful
                     currentPartition.get.nodes.foreach( node => {
                        node.ref ! ToDvmsActor(DissolvePartition())
                     })
                  } else {
                     // it was not enough: the partition is forwarded to the firstOut
                     firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
                  }
               } else {
                  log.warning(s"$applicationRef: $partition is no more valid (source: ${partition.initiator})")
               }
            }
         }
      }

      case CpuViolationDetected() => {
         currentPartition match {
            case None => {
               log.info("Dvms has detected a new cpu violation")
               firstOut = Some(nextDvmsNode)

               currentPartition = Some(DvmsPartition(applicationRef, applicationRef, List(applicationRef), Growing(), UUID.randomUUID()))

               log.info(s"$applicationRef transmitting ISP ${currentPartition.get} to $firstOut")
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


