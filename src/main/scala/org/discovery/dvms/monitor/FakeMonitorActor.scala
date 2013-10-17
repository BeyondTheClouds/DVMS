package org.discovery.dvms.monitor

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
import org.discovery.dvms.dvms.DvmsModel._
import util.Random
import org.discovery.dvms.log.LoggingProtocol.CurrentLoadIs
import org.discovery.dvms.configuration.ExperimentConfiguration

class FakeMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {

   val delta: Double = 8
   val seed: Long = applicationRef.location.getId
   val random: Random = new Random(seed)

   def getVmsWithConsumption(): PhysicalNode = {
      PhysicalNode(applicationRef, List(VirtualMachine("fakeVM", cpuConsumption, null)), "", null)
   }

   def uploadCpuConsumption(): Double = {
      val cpuConsumptionChange = random.nextDouble() * 2 * delta - delta

      (cpuConsumption + cpuConsumptionChange) match {
         case n: Double if (n < 0) => cpuConsumption = 0
         case n: Double => cpuConsumption = n
      }

      // Alert LogginActor that the current node is booked in a partition
      applicationRef.ref ! CurrentLoadIs(ExperimentConfiguration.getCurrentTime(), cpuConsumption)

      cpuConsumption
   }
}