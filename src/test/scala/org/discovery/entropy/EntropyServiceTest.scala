package org.discovery.entropy

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.{BeforeAndAfterEach, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.discovery.peeractor.util.{NodeRef, INetworkLocation, FakeNetworkLocation}
import com.typesafe.config.ConfigFactory
import org.discovery.dvms.entropy.EntropyService
import entropy.configuration.{SimpleVirtualMachine, SimpleNode, SimpleConfiguration, Configuration}
import org.discovery.dvms.dvms.DvmsModel.{VirtualMachine, PhysicalNode, ComputerSpecification}
import scala.collection.JavaConversions._
import org.discovery.dvms.configuration.DvmsConfiguration
import org.discovery.dvms.dvms.DvmsModel.VirtualMachine
import org.discovery.dvms.entropy.EntropyProtocol.MigrateVirtualMachine
import org.discovery.DiscoveryModel.model.ReconfigurationModel.{MakeMigration, ReconfigurationSolution, ReconfigurationlNoSolution}


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


    "compute a reconfiguration plan with success, without needing reconfiguration" in {

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
            vm.cpuConsumption.toInt,
            vm.specs.ramCapacity,
            vm.specs.coreCapacity,
            vm.specs.ramCapacity);
          initialConfiguration.setRunOn(entropyVm, entropyNode);
        })
      })

      EntropyService.computeReconfigurationPlan(
        initialConfiguration,
        physicalNodesWithVmsConsumption
      )
    }

    "compute a reconfiguration plan with success, needing some reconfiguration" in {

      val initialConfiguration: Configuration = new SimpleConfiguration();

      val nodes = List(NodeRef(1, system.deadLetters), NodeRef(2, system.deadLetters))

      val physicalNodesWithVmsConsumption: List[PhysicalNode] = List(
        PhysicalNode(
          nodes(0),
          List(
            VirtualMachine(s"vm-1-1", 81.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-2", 59.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-3", 69.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-4",  0.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-5", 70.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-6",100.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-7", 80.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-8", 60.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-9", 41.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-1-10",50.0, ComputerSpecification(1, 512, 100))
          ),
          s"1",
          ComputerSpecification(8, 16384, 800)
        ),
        PhysicalNode(
          nodes(1),
          List(
            VirtualMachine(s"vm-2-1", 67.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-2", 43.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-3", 80.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-4", 91.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-5", 99.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-6", 78.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-7", 55.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-8", 89.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-9",100.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-2-10",99.0, ComputerSpecification(1, 512, 100))
          ),
          s"2",
          ComputerSpecification(8, 16384, 800)
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
            vm.cpuConsumption.toInt,
            vm.specs.ramCapacity);
          initialConfiguration.setRunOn(entropyVm, entropyNode);
        })
      })

      val result = EntropyService.computeReconfigurationPlan(
        initialConfiguration,
        physicalNodesWithVmsConsumption
      )


      result match {
        case solution: ReconfigurationSolution =>
          solution.actions.keySet().foreach( key => {
            solution.actions.get(key).foreach( migrationModel => {

              migrationModel match {
                case MakeMigration(from, to, vmName) =>
                  println(s"preparing migration of $vmName")


                  var fromNodeRef: Option[NodeRef] = None
                  var toNodeRef: Option[NodeRef] = None

                  nodes.foreach( nodeRef => {
                    println(s"check ${nodeRef.location.getId} == ( $from | $to ) ?")

                    if(s"${nodeRef.location.getId}" == from) {
                      fromNodeRef = Some(nodeRef)
                    }

                    if(s"${nodeRef.location.getId}" == to) {
                      toNodeRef = Some(nodeRef)
                    }
                  })


                  (fromNodeRef, toNodeRef) match {
                    case (Some(from), Some(to)) =>
                      from.ref ! MigrateVirtualMachine(vmName, to.location)
                    case _ =>
                      println(s"cannot migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName}")
                  }
                case otherAction =>

                  println(s"Unknown reconfiguration action $otherAction")
              }





            })
          })

          true

        case ReconfigurationlNoSolution() =>
          false
      }


    }

    "compute a reconfiguration plan with success, needing some reconfiguration (double actions)" in {

      val initialConfiguration: Configuration = new SimpleConfiguration();

      val nodes = List(NodeRef(25770743371786l, system.deadLetters), NodeRef(25770709817354l, system.deadLetters), NodeRef(25770693040138l, system.deadLetters))

      val physicalNodesWithVmsConsumption: List[PhysicalNode] = List(
        PhysicalNode(
          nodes(0),
          List(
            VirtualMachine(s"vm-5",  81.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-15", 91.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-25", 10.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-35", 80.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-45", 49.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-55", 66.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-65", 50.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-75", 30.0, ComputerSpecification(1, 512, 100))
          ),
          s"25770743371786",
          ComputerSpecification(6, 32768, 600)
        ),
        PhysicalNode(
          nodes(1),
          List(
            VirtualMachine(s"vm-4",  19.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-14", 93.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-24", 99.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-44", 61.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-54", 39.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-64", 99.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-62", 91.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-53", 38.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-67", 80.0, ComputerSpecification(1, 512, 100))
          ),
          s"25770709817354",
          ComputerSpecification(6, 32768, 600)
        ),
        PhysicalNode(
          nodes(2),
          List(
            VirtualMachine(s"vm-3",  59.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-23", 69.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-33", 69.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-43", 69.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-63",100.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-73", 90.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-83", 90.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-11", 60.0, ComputerSpecification(1, 512, 100)),
            VirtualMachine(s"vm-81", 91.0, ComputerSpecification(1, 512, 100))
          ),
          s"25770693040138",
          ComputerSpecification(6, 32768, 600)
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
            vm.cpuConsumption.toInt,
            vm.specs.ramCapacity);
          initialConfiguration.setRunOn(entropyVm, entropyNode);
        })
      })

      val result = EntropyService.computeReconfigurationPlan(
        initialConfiguration,
        physicalNodesWithVmsConsumption
      )


      result match {
        case solution: ReconfigurationSolution =>
          solution.actions.keySet().foreach( key => {
            solution.actions.get(key).foreach( migrationModel => {

              migrationModel match {
                case MakeMigration(from, to, vmName) =>
                  println(s"preparing migration of $vmName")


                  var fromNodeRef: Option[NodeRef] = None
                  var toNodeRef: Option[NodeRef] = None

                  nodes.foreach( nodeRef => {
                    println(s"check ${nodeRef.location.getId} == ( $from | $to ) ?")

                    if(s"${nodeRef.location.getId}" == from) {
                      fromNodeRef = Some(nodeRef)
                    }

                    if(s"${nodeRef.location.getId}" == to) {
                      toNodeRef = Some(nodeRef)
                    }
                  })


                  (fromNodeRef, toNodeRef) match {
                    case (Some(from), Some(to)) =>
                      from.ref ! MigrateVirtualMachine(vmName, to.location)
                    case _ =>
                      println(s"cannot migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName}")
                  }
                case otherAction =>

                  println(s"Unknown reconfiguration action $otherAction")
              }





            })
          })

          true

        case ReconfigurationlNoSolution() =>
          false
      }


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

      EntropyService.computeReconfigurationPlan(
        initialConfiguration,
        physicalNodesWithVmsConsumption
      ) match {
        case solution: ReconfigurationSolution =>
          assert(false)
        case ReconfigurationlNoSolution() =>
          assert(true)
      }
    }
  }
}