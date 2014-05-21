package org.discovery.dvms.monitor

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

import org.discovery.peeractor.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.discovery.peeractor.notification.NotificationActorProtocol._
import scala.concurrent.duration._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.peeractor.notification._
import org.discovery.dvms.monitor.MonitorProtocol._

object MonitorEvent {
   case class CpuViolation() extends Event
}

abstract class AbstractMonitorActor(applicationRef: NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   var cpuConsumption: Double = 0


   def getVmsWithConsumption(): PhysicalNode

   def uploadCpuConsumption(): Double

   override def receive = {
      case Tick() => {

         cpuConsumption = uploadCpuConsumption()

         log.info(s"the new consumption is : $cpuConsumption")

         if (cpuConsumption > 100) {
            log.info(s"the cpu consumption is under violation")

            // triggering CpuViolation event
            applicationRef.ref ! TriggerEvent(new MonitorEvent.CpuViolation())
         }
      }

      case GetVmsWithConsumption() => sender ! getVmsWithConsumption()

      case GetCpuConsumption() => {
         log.info(s"send cpu consumption $cpuConsumption")
         sender ! cpuConsumption
      }

      case UpdateConfiguration(newLoad) => {
         cpuConsumption = newLoad
      }

      case msg => {
         applicationRef.ref ! msg
      }
   }

   context.system.scheduler.schedule(0 milliseconds,
      1 second,
      self,
      Tick())
}
