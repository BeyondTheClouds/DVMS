package org.discovery.dvms.monitor

import org.discovery.AkkaArc.util.NodeRef
import org.discovery.dvms.dvms.DvmsModel._
import util.Random

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:12 PM
 * To change this template use File | Settings | File Templates.
 */

class FakeMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {

   val delta: Double = 17
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

      cpuConsumption
   }
}