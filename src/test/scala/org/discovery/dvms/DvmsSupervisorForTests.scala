package org.discovery.dvms

import factory.{DvmsAbstractFactory, FakeDvmsFactory}
import org.discovery.AkkaArc.util.INetworkLocation
import akka.pattern.pipe

/* ============================================================
 * Discovery Project - AkkaArc
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
     

object DvmsSupervisorForTestsProtocol {
   case class GetRingSize()
}

class DvmsSupervisorForTests(location: INetworkLocation, factory: DvmsAbstractFactory) extends DvmsSupervisor(location, factory) {


   def this(location: INetworkLocation) = this(location, FakeDvmsFactory)
   import org.discovery.AkkaArc.overlay.chord.ChordActor._

   override def receive = {
      case DvmsSupervisorForTestsProtocol.GetRingSize() =>
         overlayService.ringSize() pipeTo sender

      case msg =>
         super.receive(msg)
   }
}
