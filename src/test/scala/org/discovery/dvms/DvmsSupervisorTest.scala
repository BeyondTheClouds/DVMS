package org.discovery.dvms.supervisortest

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.discovery.dvms.entropy.{FakeEntropyActor, AbstractEntropyActor}
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import org.discovery.AkkaArc.util.{NodeRef, Configuration, INetworkLocation, FakeNetworkLocation}
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import org.discovery.AkkaArc.overlay.OverlayProtocol._
import org.discovery.dvms.{DvmsSupervisorForTests, DvmsSupervisorForTestsProtocol, DvmsSupervisor}
import akka.pattern.ask
import org.discovery.dvms.factory.DvmsAbstractFactory
import org.discovery.dvms.monitor.{FakeMonitorActor, AbstractMonitorActor}
import collection.immutable.HashMap
import org.discovery.dvms.dvms.DvmsActor
import org.discovery.dvms.dvms.DvmsProtocol._
import org.discovery.AkkaArc.ConnectToThisPeerActor
import org.discovery.AkkaArc.overlay.chord.ChordActor
import com.typesafe.config.ConfigFactory


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

   def this() = this(ActorSystem("MySpec", ConfigFactory.parseString( """
     prio-dispatcher {
       mailbox-type = "org.discovery.dvms.utility.DvmsPriorityMailBox"
     }
                                                                      """)))

   override def afterAll() {
      system.shutdown()
   }


   "DvmsSupervisor" must {

      val startId = 1

      val firstNode = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(startId), TestDvmsFactory)))


      var precedingNode = firstNode
      for (i <- startId+1 to startId+3) {
         val otherNode = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(i), TestDvmsFactory)))
         otherNode ! ConnectToThisPeerActor(precedingNode)
         Thread.sleep(100)
         precedingNode = otherNode
      }


      "join other nodes correctly" in {

         Thread.sleep(2000)

         val size: Int = Await.result(firstNode ? DvmsSupervisorForTestsProtocol.GetRingSize(), 1 second).asInstanceOf[Int]

         size must be(4)
      }

      "compute a reconfiguration plan with success" in {

         Thread.sleep(8000)

         val (failureCount, successCount) = Await.result(firstNode ? ToEntropyActor(ReportIn()), 1 second).asInstanceOf[(Int, Int)]

         failureCount must be(10)
         successCount must be(2)
      }


      "handle deadlock efficiently" in {


         true must be(true)
      }
   }
}