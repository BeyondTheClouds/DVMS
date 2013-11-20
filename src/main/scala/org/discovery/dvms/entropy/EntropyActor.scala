package org.discovery.dvms.entropy

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

import org.discovery.AkkaArc.util.{NetworkLocation, NodeRef}
import scala.concurrent.duration._
import akka.pattern.ask
import entropy.plan.choco.ChocoCustomRP
import entropy.configuration.{SimpleConfiguration, SimpleVirtualMachine, SimpleNode, Configuration}
import entropy.plan.durationEvaluator.MockDurationEvaluator
import concurrent.{Future, Await}
import org.discovery.dvms.dvms.DvmsModel._
import scala.collection.JavaConversions._
import org.discovery.dvms.monitor.MonitorProtocol._
import org.discovery.dvms.entropy.EntropyProtocol.MigrateVirtualMachine
import org.discovery.dvms.monitor.LibvirtMonitorDriver
import org.discovery.model._
import org.discovery.driver.Node

class EntropyActor(applicationRef: NodeRef) extends AbstractEntropyActor(applicationRef) {

   val planner: ChocoCustomRP = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
   planner.setTimeLimit(2);

   def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

      val initialConfiguration: Configuration = new SimpleConfiguration();

      // building the entropy configuration

      val physicalNodesWithVmsConsumption = Await.result(Future.sequence(nodes.map({
         n =>
            n.ref ? GetVmsWithConsumption()
      })).mapTo[List[PhysicalNode]], 1 second)

      physicalNodesWithVmsConsumption.foreach(physicalNodeWithVmsConsumption => {

         val entropyNode = new SimpleNode(s"${physicalNodeWithVmsConsumption.ref.location.getId}",
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

      val result = EntropyService.computeAndApplyReconfigurationPlan(initialConfiguration, physicalNodesWithVmsConsumption)

      result.keySet().foreach( key => {
         if(key != "result"){

            val nodeName: String = key

            result.get(key).foreach( vmName => {

               nodes.foreach( nodeRef => {
                  if(s"${nodeRef.location.getId}" == nodeName) {
                     println(s"migrating $vmName to $nodeName")

                     nodeRef.ref ! MigrateVirtualMachine(vmName, nodeRef.location)
                  }
               })


            })
         }
      })

      result.get("result")(0).toBoolean;
   }

   override def receive = {

      case MigrateVirtualMachine(vmName, destination) => {
         log.info(s"migratring $vmName to ${destination.getId}")

         destination match {
            case destinationAsNetworkLocation: NetworkLocation =>
               val vm:IVirtualMachine = LibvirtMonitorDriver.driver.findByName(vmName)

               // TODO: following parameters are hard coded! that is bad :(
               val destinationNode: INode = new Node(destinationAsNetworkLocation.ip, "root", 22)
               LibvirtMonitorDriver.driver.migrate(vm, destinationNode)


               log.info(s"[Administration] Please check  if $vmName is now located on ${destination.getId}!")

            case _ =>

         }
      }

      case msg => super.receive(msg)
   }
}
