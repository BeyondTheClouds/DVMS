package org.discovery.dvms.dvms

import org.discovery.AkkaArc.util.NodeRef
import org.discovery.dvms.dvms.DvmsModel._
import java.util.UUID
import akka.pattern.AskTimeoutException

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/31/13
 * Time: 11:21 AM
 * To change this template use File | Settings | File Templates.
 */

object DvmsProtocol {

   // Routing messages
   case class ToMonitorActor(msg: Any)
   case class ToDvmsActor(msg: Any)
   case class ToEntropyActor(msg: Any)


   case class ThisIsYourNeighbor(neighbor: NodeRef)
   case class YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef)
   case class CpuViolationDetected()

   // Message used for the base of DVMS
   case class DissolvePartition()
   case class TransmissionOfAnISP(currentPartition: DvmsPartition)
   case class IAmTheNewLeader(partition: DvmsPartition, firstOut: NodeRef)

   // Message used for the merge of partitions
   case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition)
   case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: NodeRef)
   case class ChangeTheStateOfThePartition(newState: DvmsPartititionState)

   // Message for the resiliency
   case class AskTimeoutDetected(e: AskTimeoutException)
   case class FailureDetected(node: NodeRef)
   case class CheckTimeout()


}
