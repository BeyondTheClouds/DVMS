package dvms.entropy

import org.bbk.AkkaArc.util.NodeRef
import concurrent.{Future, Await}
import dvms.dvms._
import scala.concurrent.duration._
import akka.pattern.{AskTimeoutException, ask}
import dvms.monitor.GetVmsWithConsumption
import dvms.dvms.DvmsProtocol._
import dvms.dvms.DvmsModel._
import dvms.monitor.UpdateConfiguration

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:11 PM
 * To change this template use File | Settings | File Templates.
 */


class FakeEntropyActor(applicationRef: NodeRef) extends AbstractEntropyActor(applicationRef) {

   def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

      log.info("computing reconfiguration plan")


      var isCorrect: Boolean = false

      try {

         val physicalNodesWithVmsConsumption = Await.result(Future.sequence(nodes.map({
            n =>
               n.ref ? ToMonitorActor(GetVmsWithConsumption())
         })).mapTo[List[PhysicalNode]], 1 second)

         var overallCpuConsumption = 0.0;
         physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {
            physicalNodeWithVmsConsumption.machines.foreach(vm => {
               overallCpuConsumption += vm.cpuConsumption
            })
         })

         log.info(s"computed cpu consumption: ${overallCpuConsumption / nodes.size}")

         if (overallCpuConsumption / nodes.size <= 100) {

            nodes.foreach(n => {
               n.ref ! ToMonitorActor(UpdateConfiguration(overallCpuConsumption / nodes.size))
            })

            isCorrect = true

         } else {

            isCorrect = false
         }
      } catch {
         case e: AskTimeoutException => {
            isCorrect = false
            applicationRef.ref ! ToDvmsActor(AskTimeoutDetected(e))
         }
         case e: Exception =>
      }

      return isCorrect
   }
}
