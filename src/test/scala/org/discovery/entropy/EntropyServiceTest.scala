package org.discovery.entropy

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.{BeforeAndAfterEach, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.discovery.AkkaArc.util.{NodeRef, INetworkLocation, FakeNetworkLocation}
import com.typesafe.config.ConfigFactory
import org.discovery.dvms.entropy.EntropyService
import entropy.configuration.{SimpleVirtualMachine, SimpleNode, SimpleConfiguration, Configuration}
import org.discovery.dvms.dvms.DvmsModel.{VirtualMachine, PhysicalNode}
import org.discovery.dvms.dvms.DvmsModel.ComputerSpecification
import scala.collection.JavaConversions._
import org.discovery.dvms.configuration.DvmsConfiguration


class EntropyServiceTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

   DvmsConfiguration.IS_G5K_MODE = false

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   implicit val timeout = akka.util.Timeout(1 seconds)

   def this() = this(ActorSystem("MySpec", ConfigFactory.parseString( """
     prio-dispatcher {
       mailbox-type = "org.discovery.dvms.utility.DvmsPriorityMailBox"
     }
                                                                      """)))

   override def beforeEach() {
      Thread.sleep(1000)
   }

   override def afterAll() {
      system.shutdown()
   }

   "EntropyService must" must {


      "compute a reconfiguration plan with success" in {

         val initialConfiguration: Configuration = new SimpleConfiguration();


         val physicalNodesWithVmsConsumption: List[PhysicalNode] = List(
            PhysicalNode(
               NodeRef(1, system.deadLetters),
               List(
                  VirtualMachine(s"vm-1-1", 25, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-1-2", 25, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-1-3", 25, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-1-4", 25, ComputerSpecification(1, 1024, 100))
               ),
               s"node-1",
               ComputerSpecification(1, 8192, 400)
            ),
            PhysicalNode(
               NodeRef(2, system.deadLetters),
               List(
                  VirtualMachine(s"vm-2-1", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-2-2", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-2-3", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-2-4", 100, ComputerSpecification(1, 1024, 100))
               ),
               s"node-2",
               ComputerSpecification(1, 8192, 400)
            ),
            PhysicalNode(
               NodeRef(3, system.deadLetters),
               List(
                  VirtualMachine(s"vm-3-1", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-3-2", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-3-3", 100, ComputerSpecification(1, 1024, 100)),
                  VirtualMachine(s"vm-3-4", 100, ComputerSpecification(1, 1024, 100))
               ),
               s"node-3",
               ComputerSpecification(1, 8192, 400)
            )
         )


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

         EntropyService.computeAndApplyReconfigurationPlan(
            initialConfiguration,
            physicalNodesWithVmsConsumption
         )
      }

      "compute a reconfiguration plan unsuccessfully" in {

         val initialConfiguration: Configuration = new SimpleConfiguration();

         val physicalNodesWithVmsConsumption: List[PhysicalNode] = List(1, 2, 3, 4) map {
            x: Int => PhysicalNode(
               NodeRef(x, system.deadLetters),
               List(1, 2, 3, 4, 5) map {
                  y: Int => VirtualMachine(s"vm-${x * 4 + y}", 100, ComputerSpecification(1, 1024, 100))
               },
               s"node-${x}",
               ComputerSpecification(1, 8192, 400)
            )
         }



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

         EntropyService.computeAndApplyReconfigurationPlan(
            initialConfiguration,
            physicalNodesWithVmsConsumption
         )
      }

   }
}