package dvms.entropy

import org.bbk.AkkaArc.util.NodeRef
import dvms.scheduling.ComputingState
import java.util
import scala.concurrent.duration._
import akka.pattern.{ask}
import entropy.vjob.{DefaultVJob, VJob}
import entropy.plan.choco.ChocoCustomRP
import entropy.configuration.{SimpleVirtualMachine, SimpleNode, SimpleManagedElementSet, Configuration}
import entropy.plan.{TimedReconfigurationPlan, PlanException}
import entropy.plan.durationEvaluator.MockDurationEvaluator
import dvms.configuration.{VirtualMachineConfiguration, HardwareConfiguration, DVMSManagedElementSet, DVMSVirtualMachine}
import concurrent.{Future, Await}
import dvms.dvms.{PhysicalNode, VirtualMachine, ToMonitorActor}
import dvms.monitor.GetVmsWithConsumption

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/12/13
 * Time: 6:18 PM
 * To change this template use File | Settings | File Templates.
 */
class EntropyActor(applicationRef:NodeRef) extends AbstractEntropyActor(applicationRef) {

   val planner:ChocoCustomRP =  new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
   planner.setTimeLimit(2);

   def computeAndApplyReconfigurationPlan(nodes:List[NodeRef]):Boolean = {

      var res:ComputingState = ComputingState.VMRP_SUCCESS;
      val initialConfiguration:Configuration = null;


      // building the entropy configuration

      val physicalNodesWithVmsConsumption = Await.result(Future.sequence(nodes.map({n =>
         n.ref ? ToMonitorActor(GetVmsWithConsumption())
      })).mapTo[List[PhysicalNode]], 1 second)

      physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {

         val entropyNode = new SimpleNode(physicalNodeWithVmsConsumption.ref.location.toString,
            HardwareConfiguration.getNumberOfCpus,
            HardwareConfiguration.getCpuCoreCapacity,
            HardwareConfiguration.getRamCapacity);
         initialConfiguration.addOnline(entropyNode);

         physicalNodeWithVmsConsumption.machines.foreach(vm => {
            val entropyVm = new SimpleVirtualMachine(vm.name,
               VirtualMachineConfiguration.getNumberOfCpus,
               0,
               VirtualMachineConfiguration.getRamCapacity,
               VirtualMachineConfiguration.getCpuCoreCapacity,
               VirtualMachineConfiguration.getRamCapacity);
            initialConfiguration.setRunOn(entropyVm, entropyNode);
         })
      })

//      for(DVMSNode node : getNodesConsidered()){
//         val entropyNode = new SimpleNode(node.getName(),
//            node.getNbOfCPUs(),
//            node.getCPUCapacity(),
//            node.getMemoryTotal());
//         initialConfiguration.addOnline(entropyNode);
//
//         vmsHosted = node.getVirtualMachines();
//
//         for(DVMSVirtualMachine vm : vmsHosted){
//            entropyVm = new SimpleVirtualMachine(vm.getName(),
//               vm.getNbOfCPUs(),
//               0,
//               vm.getMemoryConsumption(),
//               vm.getCPUConsumption(),
//               vm.getMemoryConsumption());
//            initialConfiguration.setRunOn(entropyVm, entropyNode);
//         }
//      }


      val vjobs:util.List[VJob] = new util.ArrayList[VJob]();
      val v:VJob = new DefaultVJob("v1");
      v.addVirtualMachines(initialConfiguration.getRunnings());
      vjobs.add(v);

      var reconfigurationPlan:TimedReconfigurationPlan = null;

      try {
         reconfigurationPlan = planner.compute(initialConfiguration,
         initialConfiguration.getRunnings(),
         initialConfiguration.getWaitings(),
         initialConfiguration.getSleepings(),
         new SimpleManagedElementSet(),
         initialConfiguration.getOnlines(),
         initialConfiguration.getOfflines(), vjobs);
      } catch {
         case e:PlanException => {
            e.printStackTrace();
            res = ComputingState.VMRP_FAILED ;
         }
      }

      if(reconfigurationPlan != null){
         if(reconfigurationPlan.getActions().isEmpty())
            res = ComputingState.NO_RECONFIGURATION_NEEDED;

//         reconfigurationPlanCost = reconfigurationPlan.getDuration();
//         newConfiguration = reconfigurationPlan.getDestination();
//         nbMigrations = computeNbMigrations();
//         reconfigurationGraphDepth = computeReconfigurationGraphDepth();
      }

      res == ComputingState.VMRP_FAILED
   }
}
