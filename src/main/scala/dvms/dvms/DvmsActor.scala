package dvms.dvms

import akka.actor.{ActorLogging, Actor}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.{Future, Await, ExecutionContext}
import java.util.concurrent.{Executors}
import org.bbk.AkkaArc.util.NodeRef
import org.bbk.AkkaArc.notification.{WantsToRegister, ToNotificationActor}
import dvms.entropy.EntropyComputeReconfigurePlan
import dvms.monitor.CpuViolation
import java.util.{Date, UUID}
import dvms.ActorIdParser

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
//case class EverythingIsOkToken(id:UUID)
//case class VerifyEverythingIsOk(id:UUID, count:Int)
case class AskTimeoutDetected(e:AskTimeoutException)
case class FailureDetected(node:NodeRef)
case class CheckTimeout()

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
//   val PeriodOfPartitionNodeChecking:FiniteDuration = 100 milliseconds
   val partitionUpdateTimeout:FiniteDuration = 1500 milliseconds
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
   var lastPartitionUpdateDate:Option[Date] = None

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

   def remoteNodeFailureDetected(node:NodeRef) {
      currentPartition match {
         case Some(p) => {
            if(p.nodes.contains(node)) {
               node match {
                  // the initiator of the partition has crashed
                  case node:NodeRef if(node.location isEqualTo p.initiator.location) => {

                     log.info(s"$applicationRef: The initiator ($node) has crashed, I am becoming the new leader of $currentPartition")

                     // the partition will be dissolved
                     p.nodes.filterNot(n => n.location isEqualTo node.location).foreach(n => {
                        n.ref ! ToDvmsActor(DissolvePartition())
                     })
                  }

                  // the leader or a normal node of the partition has crashed
                  case node:NodeRef => {

                     // creation of a new partition without the crashed node
                     val newPartition:DvmsPartition = new DvmsPartition(applicationRef, p.initiator, p.nodes.filterNot(n => n.location isEqualTo node.location), p.state, UUID.randomUUID())

                     currentPartition = Some(newPartition)
                     firstOut = Some(nextDvmsNode)

                     if(node.location.getId == 10) {
                        println("toto")
                     }

                     log.info(s"$applicationRef: A node crashed ($node), I am becoming the new leader of $currentPartition")

                     newPartition.nodes.foreach(node => {
                        node.ref ! ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get))
                     })

                     countOfCheck = Some((newPartition.id, -1))
//                     self ! EverythingIsOkToken(newPartition.id)
                  }
               }
            }
         }
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

      case AskTimeoutDetected(e:AskTimeoutException) => {

         val id:String = ActorIdParser.parse(ActorIdParser.chain, e.toString).get

         log.info(s"$applicationRef: AskTimeoutDetected received!!")

         currentPartition match {
            case Some(p) => p.nodes.foreach(n => {

               val nId:String = ActorIdParser.parse(ActorIdParser.chain, n.ref.toString).get

               if(nId == id) {

                  log.info(s"$applicationRef: $n has failed!!")
                  remoteNodeFailureDetected(n)
               }
            })
            case None =>
         }
      }

      case FailureDetected(node) => {
         remoteNodeFailureDetected(node)
      }

      case CheckTimeout() => {

//         log.info(s"$applicationRef: check if we have reach the timeout of partition")

         (currentPartition, lastPartitionUpdateDate) match {
            case (Some(p), Some(d)) => {

               val now:Date = new Date()
               val duration:Duration = (now.getTime - d.getTime) milliseconds

               if(duration > DvmsActor.partitionUpdateTimeout) {

                  log.info(s"$applicationRef: timeout of partition has been reached: I dissolve everything")

                  p.nodes.foreach( n => {
                     n.ref ! ToDvmsActor(DissolvePartition())
                  })
               }

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
         lastPartitionUpdateDate = None
      }

      case IAmTheNewLeader(partition, firstOutOfTheLeader) => {

         log.info(s"$applicationRef: ${partition.leader} is the new leader of $partition")

         currentPartition = Some(partition)
         lockedForFusion = false

         countOfCheck = Some((partition.id,-1))

         lastPartitionUpdateDate = Some(new Date())

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

                  if(firstOut.get.location.getId == 4) {

                     val neighbor = nextDvmsNode
                     println("toto")
                  }
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

                  val newPartition:DvmsPartition = new DvmsPartition(applicationRef, partition.initiator, applicationRef::partition.nodes, Growing(), UUID.randomUUID())

                  currentPartition = Some(newPartition)
                  firstOut = Some(nextDvmsNode)

                  partition.nodes.foreach(node => {
                     log.info(s"$applicationRef: sending the $newPartition to $node")
                     node.ref ! ToDvmsActor(IAmTheNewLeader(newPartition, firstOut.get))
                  })

                  countOfCheck = Some((newPartition.id, -1))
                  lastPartitionUpdateDate = Some(new Date())
//                  newPartition.nodes(1).ref ! ToDvmsActor(EverythingIsOkToken(newPartition.id))

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

      val entropyComputeAsFuture:Future[Boolean] = (applicationRef.ref ? ToEntropyActor(EntropyComputeReconfigurePlan(currentPartition.get.nodes))).mapTo[Boolean]
      var result:Boolean = false
      var hasComputed = false

      for{
         futureResult <- entropyComputeAsFuture
      } yield {
         result = futureResult
         hasComputed = true
      }

      while(!hasComputed) {
         Thread.sleep(100)
      }

      result
   }

   // registering an event: when a CpuViolation is triggered, CpuViolationDetected() is sent to dvmsActor
   applicationRef.ref ! ToNotificationActor(WantsToRegister(applicationRef, new CpuViolation(), n => {
      n.ref ! ToDvmsActor(CpuViolationDetected())
   }))

   // registering a timer that will check if the node is in a partition and then if there is an activity from
   // this partition

   context.system.scheduler.schedule(0 milliseconds,
      500 milliseconds,
      self,
      CheckTimeout())


}


