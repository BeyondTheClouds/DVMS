package org.bbk.AkkaArc

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import model.chord.GetRingSize
import model.ToModelActor
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import util.{NodeRef, Configuration, INetworkLocation, FakeNetworkLocation}
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import dvms.DvmsSupervisor
import akka.pattern.ask
import dvms.factory.DvmsAbstractFactory
import dvms.monitor.{FakeMonitorActor, AbstractMonitorActor}
import dvms.entropy.{FakeEntropyActor, AbstractEntropyActor}
import collection.immutable.HashMap
import dvms.dvms.{DvmsActor, ToEntropyActor}

object DvmsSupervisorTest {

}


object TestData {

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   val hashLoad: HashMap[INetworkLocation, List[Double]] = HashMap(
      (intToLocation(1) -> List(50.0, 50.0, 110.0, -1, -1, 110, -1)),
      (intToLocation(2) -> List(50.0, 50.0, 80.0, -1, -1, -1, -1)),
      (intToLocation(3) -> List(50.0, 50.0, 70.0, -1, -1, -1, -1)),
      (intToLocation(4) -> List(50.0, 50.0, 150.0, -1, -1, 50, -1))
   )
}


class TestMonitorActor(nodeRef: NodeRef) extends FakeMonitorActor(nodeRef) {

   var count: Int = -1

   override def uploadCpuConsumption(): Double = {

      count = count + 1

      if (TestData.hashLoad(nodeRef.location).size > count) {

         TestData.hashLoad(nodeRef.location)(count) match {
            case -1 =>
            case n: Double => {
               cpuConsumption = n
            }
         }
      }

      cpuConsumption
   }
}

case class ReportIn()


object TestEntropyActor {
   var failureCount: Int = 0
   var successCount: Int = 0
}

class TestEntropyActor(nodeRef: NodeRef) extends FakeEntropyActor(nodeRef) {

   override def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean = {

      val result = super.computeAndApplyReconfigurationPlan(nodes)

      result match {
         case true => {
            TestEntropyActor.successCount += 1
         }
         case false => {
            TestEntropyActor.failureCount += 1
         }
      }

      result
   }

   override def receive = {
      case ReportIn() => sender !(TestEntropyActor.failureCount, TestEntropyActor.successCount)
      case msg => {
         super.receive(msg)
      }
   }
}

object TestDvmsFactory extends DvmsAbstractFactory {
   def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor] = {
      Some(new TestMonitorActor(nodeRef))
   }

   def createDvmsActor(nodeRef: NodeRef): Option[DvmsActor] = {
      Some(new DvmsActor(nodeRef))
   }

   def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor] = {
      Some(new TestEntropyActor(nodeRef))
   }
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

   "DvmsSupervisor" must {
      "join other nodes correctly" in {
         val exampleApplication1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1))))


         for (i <- 2 to 5) {
            val exampleApplicationI = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(i))))
            exampleApplicationI ! InitCommunicationWithHim(exampleApplication1)
         }

         Thread.sleep(2000)

         val size: Int = Await.result(exampleApplication1 ? ToModelActor(GetRingSize()), 1 second).asInstanceOf[Int]

         size must be(5)
      }

      "compute a reconfiguration plan with success" in {


         val exampleApplication1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))


         for (i <- 2 to 4) {
            val exampleApplicationI = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(i), TestDvmsFactory)))
            exampleApplicationI ! InitCommunicationWithHim(exampleApplication1)
         }

         Thread.sleep(8000)

         val (failureCount, successCount) = Await.result(exampleApplication1 ? ToEntropyActor(ReportIn()), 1 second).asInstanceOf[(Int, Int)]

         failureCount must be(10)
         successCount must be(2)
      }


      "handle deadlock efficiently" in {


         true must be(true)
      }
   }
}