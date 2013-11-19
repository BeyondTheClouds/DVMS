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
import scala.concurrent.duration._
import akka.pattern.ask
import entropy.plan.choco.ChocoCustomRP
import entropy.configuration.{SimpleConfiguration, SimpleVirtualMachine, SimpleNode, Configuration}
import entropy.plan.durationEvaluator.MockDurationEvaluator
import concurrent.{Future, Await}
import org.discovery.dvms.dvms.DvmsModel._
import scala.collection.JavaConversions._
import org.discovery.dvms.monitor.MonitorProtocol._

class EntropyActor(applicationRef: NodeRef) extends AbstractEntropyActor(applicationRef) {

   val planner: ChocoCustomRP = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
   planner.setTimeLimit(2);

   def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

      log.info("inside computeAndApplyReconfigurationPlan (1)")

      val initialConfiguration: Configuration = new SimpleConfiguration();

      // building the entropy configuration

      val physicalNodesWithVmsConsumption = Await.result(Future.sequence(nodes.map({
         n =>
            n.ref ? GetVmsWithConsumption()
      })).mapTo[List[PhysicalNode]], 1 second)

      log.info("inside computeAndApplyReconfigurationPlan (2)")

      physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {

         val entropyNode = new SimpleNode(physicalNodeWithVmsConsumption.ref.toString,
            physicalNodeWithVmsConsumption.specs.numberOfCPU,
            physicalNodeWithVmsConsumption.specs.coreCapacity,
            physicalNodeWithVmsConsumption.specs.ramCapacity);
         initialConfiguration.addOnline(entropyNode);

         physicalNodeWithVmsConsumption.machines.foreach(vm => {
            val entropyVm = new SimpleVirtualMachine(vm.name,
               vm.specs.numberOfCPU,
               0,
               vm.specs.ramCapacity,
               vm.specs.coreCapacity,
               vm.specs.ramCapacity);
            initialConfiguration.setRunOn(entropyVm, entropyNode);
         })
      })

      log.info("inside computeAndApplyReconfigurationPlan (3)")

      val result = EntropyService.computeAndApplyReconfigurationPlan(initialConfiguration, physicalNodesWithVmsConsumption)

      log.info("inside computeAndApplyReconfigurationPlan (4)")

      result
   }
}
