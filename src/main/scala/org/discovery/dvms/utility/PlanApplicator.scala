package org.discovery.dvms.utility

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

import org.discovery.DiscoveryModel.model.ReconfigurationModel.{MakeMigration, ReconfigurationSolution}
import org.discovery.peeractor.util.NodeRef
import org.discovery.dvms.log.LoggingProtocol.AskingMigration
import org.discovery.dvms.configuration.ExperimentConfiguration
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.discovery.dvms.entropy.EntropyProtocol.MigrateVirtualMachine
import scala.concurrent.Await

trait PlanApplicator {

  def applySolution(app: NodeRef, solution: ReconfigurationSolution, nodes: List[NodeRef])

}

class FakePlanApplicator extends PlanApplicator {

  def applySolution(app: NodeRef, solution: ReconfigurationSolution, nodes: List[NodeRef]) {
    
  }

}

class LibvirtPlanApplicator extends PlanApplicator {

  def applySolution(app: NodeRef, solution: ReconfigurationSolution, nodes: List[NodeRef]) {

    import scala.collection.JavaConversions._


    solution.actions.keySet().foreach(key => {
      solution.actions.get(key).foreach(action => {


        action match {
          case MakeMigration(from, to, vmName) =>
            println(s"preparing migration of $vmName")


            var fromNodeRef: Option[NodeRef] = None
            var toNodeRef: Option[NodeRef] = None

            nodes.foreach(nodeRef => {
              println(s"check ${nodeRef.location.getId} == ( $from | $to ) ?")

              if (s"${nodeRef.location.getId}" == from) {
                fromNodeRef = Some(nodeRef)
              }

              if (s"${nodeRef.location.getId}" == to) {
                toNodeRef = Some(nodeRef)
              }
            })

            (fromNodeRef, toNodeRef) match {
              case (Some(from), Some(to)) =>
                println(s"send migrate message {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName}")

                (fromNodeRef, toNodeRef) match {
                  case (Some(from), Some(to)) =>
                    app.ref ! AskingMigration(ExperimentConfiguration.getCurrentTime(), from.location.getId, to.location.getId)
                  case _ =>
                }

                var migrationSucceed = false

                implicit val timeout = Timeout(300 seconds)
                val future = (from.ref ? MigrateVirtualMachine(vmName, to.location)).mapTo[Boolean]

                try {
                  migrationSucceed = Await.result(future, timeout.duration)
                } catch {
                  case e: Throwable =>
                    e.printStackTrace()
                    migrationSucceed = false
                }

                println(s"migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName} : result => $migrationSucceed")
              case _ =>
                println(s"migrate {from:$fromNodeRef, to: $toNodeRef, vmName: $vmName} : failed")
            }
          case otherAction =>
            println(s"unknownAction $otherAction")
        }
      })
    })
  }
}
