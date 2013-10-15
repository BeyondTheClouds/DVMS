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

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/6/13
 * Time: 11:30 AM
 * To change this template use File | Settings | File Templates.
 */

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

   }

}
