package dvms.monitor

import org.bbk.AkkaArc.util.NodeRef
import org.bbk.driver.LibvirtDriver
import org.bbk.model.IVirtualMachine
import scala.collection.JavaConversions._
import dvms.dvms.{ComputerSpecification, PhysicalNode, VirtualMachine}
import dvms.configuration.{VirtualMachineConfiguration, HardwareConfiguration}

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/6/13
 * Time: 10:54 AM
 * To change this template use File | Settings | File Templates.
 */

object LibvirtMonitorActor {

   val driver: LibvirtDriver = new LibvirtDriver("configuration/driver.cfg");
   driver.connect()

}

class LibvirtMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {

   def getVmsWithConsumption(): PhysicalNode = {
      PhysicalNode(applicationRef, LibvirtMonitorActor.driver.getRunningVms.toList.map(vm =>
         VirtualMachine(
            vm.getName,
            LibvirtMonitorActor.driver.getUserCpu(vm) + LibvirtMonitorActor.driver.getStealCpu(vm),
            ComputerSpecification(
               VirtualMachineConfiguration.getNumberOfCpus,
               VirtualMachineConfiguration.getRamCapacity,
               VirtualMachineConfiguration.getCpuCoreCapacity
            )
         )),
         LibvirtMonitorActor.driver.getMigrationUrl()
         ,
         ComputerSpecification(
            HardwareConfiguration.getNumberOfCpus,
            HardwareConfiguration.getRamCapacity,
            HardwareConfiguration.getCpuCoreCapacity
         )
      )
   }

   def uploadCpuConsumption(): Double = {

      val cpuConsumption: Double = LibvirtMonitorActor.driver.getRunningVms.toList.foldLeft[Double](0)((a: Double, b: IVirtualMachine) => a + (b match {
         case machine: IVirtualMachine => LibvirtMonitorActor.driver.getStealCpu(machine) + LibvirtMonitorActor.driver.getUserCpu(machine)
         case _ => 0.0
      }))

      log.info(s"load: $cpuConsumption")

      cpuConsumption
   }
}
