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
import org.discovery.peeractor.util.INetworkLocation

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

   case class AskingMigration(time: Double, name: String, from: Long, to: Long) extends LoggingMessage

  case class StartingMigration(time: Double, name: String, from: Long, to: Long) extends LoggingMessage

  case class FinishingMigration(time: Double, name: String, from: Long, to: Long) extends LoggingMessage

  case class AbortingMigration(time: Double, name: String, from: Long, to: Long) extends LoggingMessage
}

class LoggingActor(location: INetworkLocation) extends Actor {

   val file = new File("dvms.log")
   val writer = new PrintWriter(file)

   val origin: Long = location.getId

   override def receive = {

      case ComputingSomeReconfigurationPlan(time: Double) =>
         writer.write(s"""{"event": "computing_reconfiguration_plan", "origin": "$origin", "time": "$time"}\n""")
         writer.flush()

      case ApplyingSomeReconfigurationPlan(time: Double) =>
         writer.write(s"""{"event": "applying_reconfiguration_plan", "origin": "$origin", "time": "$time"}\n""")
         writer.flush()

      case ApplicationSomeReconfigurationPlanIsDone(time: Double) =>
        writer.write(s"""{"event": "applying_reconfiguration_plan_is_done", "origin": "$origin", "time": "$time"}\n""")

      case IsBooked(time: Double) =>
        writer.write(s"""{"event": "is_booked", "origin": "$origin", "time": "$time"}\n""")
         writer.flush()

      case IsFree(time: Double) =>
        writer.write(s"""{"event": "is_free", "origin": "$origin", "time": "$time"}\n""")
         writer.flush()

      case AskingMigration(time: Double, vm: String, from: Long, to: Long) =>
        writer.write(s"""{"event": "ask_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
        writer.flush()

      case StartingMigration(time: Double, vm: String, from: Long, to: Long) =>
        writer.write(s"""{"event": "start_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
        writer.flush()

      case FinishingMigration(time: Double, vm: String, from: Long, to: Long) =>
        writer.write(s"""{"event": "finish_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
        writer.flush()

      case AbortingMigration(time: Double, vm: String, from: Long, to: Long) =>
        writer.write(s"""{"event": "abort_migration", "origin": "$origin", "time": "$time",  "vm": "$vm", "from": "$from",  "to": "$to"}\n""")
        writer.flush()

      case CurrentLoadIs(time: Double, load: Double) =>
        writer.write(s"""{"event": "cpu_load", "origin": "$origin", "time": "$time",  "value": "$load"}\n""")
         writer.flush()


      case ViolationDetected(time: Double) =>
        writer.write(s"""{"event": "overload", "origin": "$origin", "time": "$time"}\n""")
         writer.flush()

      case UpdateMigrationCount(time: Double, count: Int) =>
        writer.write(s"""{"event": "migration_count", "origin": "$origin", "time": "$time", "value": "$count"}\n""")
         writer.flush()

      case _ =>
   }

}
