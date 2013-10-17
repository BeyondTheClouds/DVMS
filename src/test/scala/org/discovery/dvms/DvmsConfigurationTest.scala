package org.discovery.dvms

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import configuration.DvmsConfiguration
import org.scalatest.{BeforeAndAfterEach, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.discovery.AkkaArc.util.INetworkLocation
import org.discovery.AkkaArc.util.Configuration
import org.discovery.AkkaArc.util.FakeNetworkLocation
import com.typesafe.config.ConfigFactory


class DvmsConfigurationTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll with BeforeAndAfterEach {

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   implicit val timeout = akka.util.Timeout(1 seconds)

   Configuration.debug = true

   def this() = this(ActorSystem("MySpec", ConfigFactory.parseString( """
     prio-dispatcher {
       mailbox-type = "org.discovery.dvms.utility.DvmsPriorityMailBox"
     }
                                                                      """)))

   override def beforeEach() {
      Thread.sleep(1000)
   }

   override def afterAll() {
      system.shutdown()
   }

   "Configuration must" must {


      "parse correctly config/dvms.cfg" in {

         println(DvmsConfiguration.DVMS_CONFIG_NAME)
         println(DvmsConfiguration.FACTORY_NAME)
         println(DvmsConfiguration.IS_G5K_MODE)

      }

   }
}