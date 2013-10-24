package org.discovery.dvms

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import dvms.DvmsActor
import entropy.AbstractEntropyActor
import factory.DvmsAbstractFactory
import log.LoggingActor
import monitor.{LibvirtMonitorActor, AbstractMonitorActor}
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import org.discovery.AkkaArc.util.{NodeRef, Configuration, FakeNetworkLocation, INetworkLocation}
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import org.discovery.driver.LibvirtDriver
import org.discovery.AkkaArc.overlay.chord.ChordService
import service.ServiceActor

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

object DvmsLibvirtTest {

}

class DvmsLibvirtTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   implicit val timeout = akka.util.Timeout(1 seconds)

   Configuration.debug = true

   def this() = this(ActorSystem("MySpec", ConfigFactory.parseString( """
     prio-dispatcher {
       mailbox-type = "dvms.utility.DvmsPriorityMailBox"
     }
                                                                      """)))

   override def afterAll() {
      system.shutdown()
   }


   "LibvirtDriver" should {
      "successfully load" in {

         val driver: LibvirtDriver = new LibvirtDriver("configuration/driver.cfg")
         driver.connect()
         //         driver.connect("qemu+ssh://root@127.0.0.1:8210/session?socket=/var/run/libvirt/libvirt-sock")

         driver.isConnected must be(true)
      }
   }
}
