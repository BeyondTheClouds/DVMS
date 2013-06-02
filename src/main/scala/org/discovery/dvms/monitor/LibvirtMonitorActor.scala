package org.discovery.dvms.monitor

import org.discovery.AkkaArc.util.NodeRef
import org.discovery.driver.LibvirtDriver
import org.discovery.model.IVirtualMachine
import scala.collection.JavaConversions._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.configuration.{VirtualMachineConfiguration, HardwareConfiguration}
import scala.concurrent.duration._

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/6/13
 * Time: 10:54 AM
 * To change this template use File | Settings | File Templates.
 */

object LibvirtMonitorDriver {

   val driver: LibvirtDriver = new LibvirtDriver("configuration/driver.cfg");
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

      cpuConsumption
   }
}
