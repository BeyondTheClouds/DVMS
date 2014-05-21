package org.discovery.dvms.supervisortest

import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.discovery.dvms.entropy.FakeEntropyActor
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import org.discovery.peeractor.util.{NodeRef, Configuration, INetworkLocation}
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import org.discovery.dvms._
import akka.pattern.ask
import org.discovery.dvms.monitor.FakeMonitorActor
import collection.immutable.HashMap
import com.typesafe.config.ConfigFactory
import org.discovery.peeractor.PeerActorProtocol.ConnectToThisPeerActor
import org.discovery.peeractor.util.FakeNetworkLocation
import org.discovery.dvms.ReportIn
import org.discovery.DiscoveryModel.model.ReconfigurationModel.{ReconfigurationlNoSolution, ReconfigurationSolution, ReconfigurationResult}


object DvmsSupervisorTest {

}


object TestData {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  val hashLoad: HashMap[INetworkLocation, List[Double]] = HashMap(
    (intToLocation(1) -> List(50.0, 50.0, 110.0, -1, -1, 110, -1, -1, -1)),
    (intToLocation(2) -> List(50.0, 50.0, 80.0, -1, -1, -1, -1, -1, -1)),
    (intToLocation(3) -> List(50.0, 50.0, 70.0, -1, -1, -1, -1, -1, -1)),
    (intToLocation(4) -> List(50.0, 50.0, 150.0, -1, -1, 50, -1, -1, -1))
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

//case class ReportIn()


object TestEntropyActor {
  var failureCount: Int = 0
  var successCount: Int = 0
}

class TestEntropyActor(nodeRef: NodeRef) extends FakeEntropyActor(nodeRef) {

  override def computeReconfigurationPlan(nodes: List[NodeRef]): ReconfigurationResult = {

    val result = super.computeReconfigurationPlan(nodes)

    result match {
      case solution: ReconfigurationSolution => {
        TestEntropyActor.successCount += 1
      }
      case ReconfigurationlNoSolution() => {
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

class DvmsSupervisorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit val timeout = akka.util.Timeout(1 seconds)

  Configuration.debug = true

  def this() = this(ActorSystem("MySpec", ConfigFactory.parseString(Configuration.vivaldiConfigurationFormat + "\n" + """
     prio-dispatcher {
       mailbox-type = "org.discovery.dvms.utility.DvmsPriorityMailBox"
     }
                                                                                                                      """)))

  override def afterAll() {
    system.shutdown()
  }


  "DvmsSupervisor" must {

    "join other nodes correctly" in {

      val node1 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node2 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node3 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node4 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))

      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)

      Thread.sleep(2000)

      val size: Int = Await.result(node1 ? DvmsSupervisorForTestsProtocol.GetRingSize(), 1 second).asInstanceOf[Int]

      size must be(4)
    }

    "compute a reconfiguration plan with success" in {

      val node1 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(5), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node2 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(6), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node3 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(7), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
      val node4 = system.actorOf(Props(new DvmsSupervisorForTests(FakeNetworkLocation(8), TestDvmsFactory)).withDispatcher("prio-dispatcher"))

      // create the links
      node2 ! ConnectToThisPeerActor(node1)
      node3 ! ConnectToThisPeerActor(node1)
      node4 ! ConnectToThisPeerActor(node1)

      Thread.sleep(7000)

      val node1IsOk: Boolean = Await.result(node1 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node2IsOk: Boolean = Await.result(node2 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node3IsOk: Boolean = Await.result(node3 ? ReportIn(), 1 second).asInstanceOf[Boolean]
      val node4IsOk: Boolean = Await.result(node4 ? ReportIn(), 1 second).asInstanceOf[Boolean]


      node1IsOk must be(true)
      node2IsOk must be(true)
      node3IsOk must be(true)
      node4IsOk must be(true)
    }
  }
}