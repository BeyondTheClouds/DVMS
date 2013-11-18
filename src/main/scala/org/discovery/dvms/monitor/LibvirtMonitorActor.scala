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
import org.discovery.driver.{LibvirtDriver, LibvirtG5kDriver}
import org.discovery.model.{IDriver}
import scala.collection.JavaConversions._
import org.discovery.dvms.configuration._
import org.discovery.AkkaArc.notification.TriggerEvent
import org.discovery.dvms.log.LoggingProtocol.CurrentLoadIs
import org.discovery.dvms.dvms.DvmsModel.PhysicalNode
import org.discovery.AkkaArc.PeerActorProtocol.ToNotificationActor
import org.discovery.dvms.dvms.DvmsModel.ComputerSpecification
import org.discovery.dvms.dvms.DvmsModel.VirtualMachine
import org.discovery.model.network.CpuConsumptions

object LibvirtMonitorDriver {
   val driver: IDriver = DvmsConfiguration.IS_G5K_MODE match {

      case true =>
         new LibvirtG5kDriver("configuration/driver.cfg")
      case false =>
         new LibvirtDriver("configuration/driver.cfg")
   }

   driver.connect()

}

class LibvirtMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {


   def getVmsWithConsumption(): PhysicalNode = {

      val cpuCnsumptions = LibvirtMonitorDriver.driver.getCpuConsumptions

      PhysicalNode(applicationRef, LibvirtMonitorDriver.driver.getRunningVms.par.map(vm =>
         VirtualMachine(
            vm.getName,
            cpuCnsumptions.get(vm.getName).get(CpuConsumptions.US) + cpuCnsumptions.get(vm.getName).get(CpuConsumptions.ST),
            ComputerSpecification(
               VirtualMachineConfiguration.getNumberOfCpus,
               VirtualMachineConfiguration.getRamCapacity,
               VirtualMachineConfiguration.getCpuCoreCapacity
            )
         )).toList,
         LibvirtMonitorDriver.driver.getMigrationUrl()
         ,
         ComputerSpecification(
            HardwareConfiguration.getNumberOfCpus,
            HardwareConfiguration.getRamCapacity,
            HardwareConfiguration.getCpuCoreCapacity
         )
      )
   }

   def uploadCpuConsumption(): Double = {

      val cpuCnsumptions = LibvirtMonitorDriver.driver.getCpuConsumptions

      val listOfConsumptions: List[Double] = cpuCnsumptions.keySet().map ( key =>
         cpuCnsumptions.get(key).get(CpuConsumptions.US) + cpuCnsumptions.get(key).get(CpuConsumptions.ST)
      ).toList

      val cpuConsumption: Double = listOfConsumptions.foldLeft(0.0)((a,b) => a + b)

      // Send the CPU consumption to logginActor
      applicationRef.ref ! CurrentLoadIs(ExperimentConfiguration.getCurrentTime(), cpuConsumption)

      cpuConsumption
   }

   def uploadCpuConsumption(tickNumber: Int): Double = uploadCpuConsumption()

   var tickCount: Int = 0;

   override def receive = {

      case Tick() => {

         tickCount += 1

         val tickNumber = tickCount

         cpuConsumption = uploadCpuConsumption(tickNumber)

         log.info(s"the new consumption is : $cpuConsumption")

         if (cpuConsumption > G5kNodes.getCurrentNodeInstance().getCPUCapacity) {
            log.info(s"the cpu consumption is under violation")

            // triggering CpuViolation event
            applicationRef.ref ! ToNotificationActor(TriggerEvent(new MonitorEvent.CpuViolation()))
         }
      }

      case msg => super.receive(msg)
   }
}
