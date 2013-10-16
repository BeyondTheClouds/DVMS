package org.discovery.dvms.entropy

import org.discovery.AkkaArc.util.NodeRef
import concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.pattern.{AskTimeoutException, ask}
import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.monitor.MonitorProtocol._

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
               n.ref ? GetVmsWithConsumption()
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
               n.ref ! UpdateConfiguration(overallCpuConsumption / nodes.size)
            })

            isCorrect = true

         } else {

            isCorrect = false
         }
      } catch {
         case e: AskTimeoutException => {
            isCorrect = false
            applicationRef.ref ! AskTimeoutDetected(e)
         }
         case e: Exception =>
      }

      return isCorrect
   }
}
