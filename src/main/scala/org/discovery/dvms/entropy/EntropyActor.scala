package org.discovery.dvms.entropy

import org.discovery.AkkaArc.util.NodeRef
import scala.concurrent.duration._
import akka.pattern.ask
import entropy.plan.choco.ChocoCustomRP
import entropy.configuration.{SimpleConfiguration, SimpleVirtualMachine, SimpleNode, Configuration}
import entropy.plan.durationEvaluator.MockDurationEvaluator
import concurrent.{Future, Await}
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.monitor.GetVmsWithConsumption
import scala.collection.JavaConversions._
import org.discovery.dvms.entropy.EntropyService

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/12/13
 * Time: 6:18 PM
 * To change this template use File | Settings | File Templates.
 */
class EntropyActor(applicationRef: NodeRef) extends AbstractEntropyActor(applicationRef) {

   val planner: ChocoCustomRP = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
   planner.setTimeLimit(2);

   def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

      val initialConfiguration: Configuration = new SimpleConfiguration();

      // building the entropy configuration

      val physicalNodesWithVmsConsumption = Await.result(Future.sequence(nodes.map({
         n =>
            n.ref ? ToMonitorActor(GetVmsWithConsumption())
      })).mapTo[List[PhysicalNode]], 1 second)



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

      EntropyService.computeAndApplyReconfigurationPlan(initialConfiguration, physicalNodesWithVmsConsumption)
   }
}
