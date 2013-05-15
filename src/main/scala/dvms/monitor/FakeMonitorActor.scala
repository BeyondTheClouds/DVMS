package dvms.monitor

import org.bbk.AkkaArc.util.NodeRef
import dvms.dvms.{PhysicalNode, VirtualMachine}

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:12 PM
 * To change this template use File | Settings | File Templates.
 */

class FakeMonitorActor(applicationRef:NodeRef) extends AbstractMonitorActor(applicationRef) {

  def getVmsWithConsumption():PhysicalNode = {
     PhysicalNode(applicationRef, List(VirtualMachine("fakeVM", cpuConsumption)))
  }
  def uploadCpuConsumption():Double = {
    val cpuConsumptionChange = random.nextDouble()*2*delta - delta

    (cpuConsumption+cpuConsumptionChange) match {
      case n:Double if (n<0) => cpuConsumption = 0
      case n:Double => cpuConsumption = n
    }

    cpuConsumption
  }
}