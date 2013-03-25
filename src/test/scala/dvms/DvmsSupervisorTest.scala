package org.bbk.AkkaArc

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import util.{Configuration, INetworkLocation, FakeNetworkLocation}
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.{ExecutionContext}
import java.util.concurrent.Executors
import dvms.DvmsSupervisor


object DvmsSupervisorTest {

}

class DvmsSupervisorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit val timeout = akka.util.Timeout(1 seconds)

  Configuration.debug = true

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    system.shutdown()
  }

  "ExampleApplication" must {
    "must execute correctly" in {
      val exampleApplication1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1))))


      for(i <- 2 to 5) {
        val exampleApplicationI = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(i))))
        exampleApplicationI ! InitCommunicationWithHim(exampleApplication1)
      }

      Thread.sleep(22000)


    }
  }
}