package org.discovery.dvms.log

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


import akka.actor.Actor
import org.discovery.dvms.log.LoggingProtocol._
import java.io.{File, PrintWriter}
import org.discovery.AkkaArc.util.INetworkLocation

trait LoggingMessage

object LoggingProtocol {

   case class ComputingSomeReconfigurationPlan(time: Double) extends LoggingMessage

   case class ApplyingSomeReconfigurationPlan(time: Double) extends LoggingMessage

   case class ApplicationSomeReconfigurationPlanIsDone(time: Double) extends LoggingMessage

   case class IsBooked(time: Double) extends LoggingMessage

   case class IsFree(time: Double) extends LoggingMessage

   case class CurrentLoadIs(time: Double, load: Double) extends LoggingMessage

   case class ViolationDetected(time: Double) extends LoggingMessage

   case class UpdateMigrationCount(time: Double, count: Int) extends LoggingMessage
}

class LoggingActor(location: INetworkLocation) extends Actor {

   val writer = new PrintWriter(new File("dvms.log"))

   override def receive = {

      case ComputingSomeReconfigurationPlan(time: Double) =>
         writer.write(s"id, ${location.getId}, SERVICE, $time, compute")

      case ApplyingSomeReconfigurationPlan(time: Double) =>
         writer.write(s"id, ${location.getId}, SERVICE, $time, reconfigure")

      case ApplicationSomeReconfigurationPlanIsDone(time: Double) =>

      case IsBooked(time: Double) =>
         writer.write(s"id, ${location.getId}, SERVICE, $time, booked")
      case IsFree(time: Double) =>
         writer.write(s"id, ${location.getId}, SERVICE, $time, free")

      case CurrentLoadIs(time: Double, load: Double) =>
         writer.write(s"id, ${location.getId}, LOAD, $time, $load")

      case ViolationDetected(time: Double) =>
         writer.write(s"id, ${location.getId}, PM, ${time}, violation-det")

      case UpdateMigrationCount(time: Double, count: Int) =>
         writer.write(s"id, ${location.getId}, NB_MIG, $time, $count")

      case _ =>
   }

}
