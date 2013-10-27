package org.discovery.dvms.service

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
     

trait ServiceMessage

object ServiceProtocol {
   case class GetOverlaySize() extends ServiceMessage
   case class MigrateVm(vmName: String, destinationIp: String, destinationPort: Int) extends ServiceMessage

   case class Benchmark(duration: Int, intensity: Int) extends ServiceMessage

   case class CannotExecuteAction(reason: Option[String]) extends ServiceMessage
}
