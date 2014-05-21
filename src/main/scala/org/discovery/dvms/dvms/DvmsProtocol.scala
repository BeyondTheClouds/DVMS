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

import org.discovery.peeractor.util.NodeRef
import org.discovery.dvms.dvms.DvmsModel._
import java.util.UUID
import akka.pattern.AskTimeoutException

trait DvmsMessage

object DvmsProtocol {

   case class ThisIsYourNeighbor(neighbor: NodeRef) extends DvmsMessage
   case class YouMayNeedToUpdateYourFirstOut(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) extends DvmsMessage
   case class CpuViolationDetected() extends DvmsMessage

   // Message used for the base of DVMS
   case class DissolvePartition(reason: String) extends DvmsMessage
   case class TransmissionOfAnISP(currentPartition: DvmsPartition) extends DvmsMessage
   case class IAmTheNewLeader(partition: DvmsPartition) extends DvmsMessage

   // Message used for the merge of partitions
   case class IsThisVersionOfThePartitionStillValid(partition: DvmsPartition) extends DvmsMessage
   case class CanIMergePartitionWithYou(partition: DvmsPartition, contact: NodeRef) extends DvmsMessage
   case class ChangeTheStateOfThePartition(newState: DvmsPartititionState) extends DvmsMessage

   // Message for the resiliency
   case class AskTimeoutDetected(e: AskTimeoutException) extends DvmsMessage
   case class FailureDetected(node: NodeRef) extends DvmsMessage
   case class CheckTimeout() extends DvmsMessage


}
