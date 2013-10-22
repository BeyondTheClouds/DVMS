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

   "Dvms using LibvirtDriver" should {

      object LibvirtDvmsFactory extends DvmsAbstractFactory {
         def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor] = {
            Some(new LibvirtMonitorActor(nodeRef))
         }

         def createDvmsActor(nodeRef: NodeRef): Option[DvmsActor] = {
            Some(new TestDvmsActor(nodeRef))
         }

         def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor] = {
            Some(new TestEntropyActor(nodeRef))
         }

         def createLoggingActor(nodeRef: NodeRef): Option[LoggingActor] = {
            Some(new TestLogginActor(nodeRef.location))
         }
      }

      "succesfully use the libvirt driver" in {

         //         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), LibvirtDvmsFactory)).withDispatcher("prio-dispatcher"))
         //
         //         while (true) {
         //            Thread.sleep(1000);
         //         }
         //
         //         1 must be(1)
      }

      "testing scala language (may not be run as test)" in {

         class Toto {
            val toto:Int = 43
            def get = 19
         }
         def foo(x: {val toto:Int}) = 123 + x.toto

//         println(foo(new {def get = 10}))
         println(foo(new Toto))
      }
   }

}
