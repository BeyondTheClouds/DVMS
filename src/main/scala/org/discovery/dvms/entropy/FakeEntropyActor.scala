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

import org.discovery.AkkaArc.util.NodeRef
import concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.pattern.{AskTimeoutException, ask}
import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.dvms.monitor.MonitorProtocol._
import org.discovery.dvms.entropy.EntropyModel.{EntropySolution, EntropyNoSolution}

class FakeEntropyActor(applicationRef: NodeRef) extends AbstractEntropyActor(applicationRef) {

   def computReconfigurationPlan(nodes: List[NodeRef]): EntropyComputationResult = {

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

      isCorrect match {
         case true =>
            EntropySolution(List())
         case false =>
            EntropyNoSolution()
      }
   }
}
