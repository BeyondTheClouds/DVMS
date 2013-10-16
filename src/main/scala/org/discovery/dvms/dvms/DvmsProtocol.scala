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


trait DvmsMessage

object DvmsProtocol {

   case class ThisIsYourNeighbor(neighbor: NodeRef) extends DvmsMessage
   case class YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) extends DvmsMessage
   case class CpuViolationDetected() extends DvmsMessage

   // Message used for the base of DVMS
   case class DissolvePartition() extends DvmsMessage
   case class TransmissionOfAnISP(currentPartition: DvmsPartition) extends DvmsMessage
   case class IAmTheNewLeader(partition: DvmsPartition, firstOut: NodeRef) extends DvmsMessage

   // Message used for the merge of partitions
   case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition) extends DvmsMessage
   case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: NodeRef) extends DvmsMessage
   case class ChangeTheStateOfThePartition(newState: DvmsPartititionState) extends DvmsMessage

   // Message for the resiliency
   case class AskTimeoutDetected(e: AskTimeoutException) extends DvmsMessage
   case class FailureDetected(node: NodeRef) extends DvmsMessage
   case class CheckTimeout() extends DvmsMessage


}
