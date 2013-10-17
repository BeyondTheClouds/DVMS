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
import org.discovery.model.{IDriver, IVirtualMachine}
import scala.collection.JavaConversions._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.configuration.{ExperimentConfiguration, DvmsConfiguration, VirtualMachineConfiguration, HardwareConfiguration}
import scala.concurrent.duration._
import org.discovery.dvms.log.LoggingProtocol.CurrentLoadIs

object LibvirtMonitorDriver {
   val driver: IDriver = DvmsConfiguration.IS_G5K_MODE match {

      case true =>
         val nodeName: String = "localhost";
         val hypervisorUrl: String = String.format(
            "qemu+ssh://root@%s/session?socket=/var/run/libvirt/libvirt-sock",
            nodeName
         )
         new LibvirtG5kDriver(nodeName, hypervisorUrl, "/usr/local/bin/virsh")

      case false =>
         new LibvirtDriver("configuration/driver.cfg")
   }

   driver.connect()

}

class LibvirtMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {

   def getVmsWithConsumption(): PhysicalNode = {


      PhysicalNode(applicationRef, LibvirtMonitorDriver.driver.getRunningVms.toList.map(vm =>
         VirtualMachine(
            vm.getName,
            LibvirtMonitorDriver.driver.getUserCpu(vm) + LibvirtMonitorDriver.driver.getStealCpu(vm),
            ComputerSpecification(
               VirtualMachineConfiguration.getNumberOfCpus,
               VirtualMachineConfiguration.getRamCapacity,
               VirtualMachineConfiguration.getCpuCoreCapacity
            )
         )),
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

      val cpuConsumption: Double = LibvirtMonitorDriver.driver.getRunningVms.toList.par.foldLeft[Double](0)((a: Double, b: IVirtualMachine) => a + (b match {
         case machine: IVirtualMachine => LibvirtMonitorDriver.driver.getStealCpu(machine) + LibvirtMonitorDriver.driver.getUserCpu(machine)
         case _ => 0.0
      }))

      log.info(s"load: $cpuConsumption")

      // Alert LogginActor that the current node is booked in a partition
      applicationRef.ref ! CurrentLoadIs(ExperimentConfiguration.getCurrentTime(), cpuConsumption)

      cpuConsumption
   }
}
