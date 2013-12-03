package org.discovery.dvms.entropy

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

import org.discovery.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import scala.concurrent.duration._
import org.discovery.dvms.entropy.EntropyProtocol._
import org.discovery.DiscoveryModel.model.ReconfigurationModel.ReconfigurationResult

abstract class AbstractEntropyActor(applicationRef: NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())


   def computeReconfigurationPlan(nodes: List[NodeRef]): ReconfigurationResult

   override def receive = {
      case EntropyComputeReconfigurePlan(nodes) => {
         sender ! computeReconfigurationPlan(nodes)
      }

      case MigrateVirtualMachine(vmName, nodeName) =>
         log.info(s"[libvirt is not enabled], cannot migrate $vmName to $nodeName")

      case msg => {
         applicationRef.ref ! msg
      }
   }
}
