package dvms


import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import dvms._
import dvms.DvmsPartition
import dvms.ToDvmsActor
import dvms.ToEntropyActor
import entropy.{AbstractEntropyActor, FakeEntropyActor}
import factory.DvmsAbstractFactory
import monitor.{AbstractMonitorActor, FakeMonitorActor}
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
import akka.util.Timeout
import scala.concurrent.duration._
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import akka.pattern.ask
import collection.immutable.HashMap
import org.bbk.AkkaArc.util.{NodeRef, INetworkLocation}
import org.bbk.AkkaArc.util.Configuration
import org.bbk.AkkaArc.InitCommunicationWithHim
import scala.Some
import org.bbk.AkkaArc.util.FakeNetworkLocation


object DvmsDeadlockTest {

}



object TestData {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  val hashLoad:HashMap[INetworkLocation, List[Double]] = HashMap(
    (intToLocation(1) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(2) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(3) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(4) -> List(110, 20, -1, -1, -1, -1, -1)),
    (intToLocation(5) -> List(110, -1, 30, -1, -1, -1, -1)),
    (intToLocation(6) -> List(110, 20, -1, -1, -1, -1, -1)),
    (intToLocation(7) -> List(110, -1, -1, -1, -1, -1, -1)),
    (intToLocation(8) -> List(110, -1, -1, -1, -1, -1, -1))
  )
}


class TestMonitorActor(nodeRef:NodeRef) extends FakeMonitorActor(nodeRef) {

  var count:Int = -1

  override def uploadCpuLoad():Double = {

    count = count + 1

    if(TestData.hashLoad(nodeRef.location).size > count) {

      TestData.hashLoad(nodeRef.location)(count) match {
        case -1 =>
        case n:Double => {
          cpuLoad = n
        }
      }
    }

    cpuLoad
  }
}

case class ReportIn()
case class SetCurrentPartition(partition:DvmsPartition)
case class SetFirstOut(firstOut:NodeRef)

case class BeginTransmission()

class TestDvmsActor(applicationRef:NodeRef) extends DvmsActor(applicationRef) {

  override def receive = {
    case msg@SetCurrentPartition(partition) => {
      currentPartition = Some(partition)
    }
    case msg@SetFirstOut(node) => {
      firstOut = Some(node)
    }
    case BeginTransmission() => {
      firstOut.get.ref ! ToDvmsActor(TransmissionOfAnISP(currentPartition.get))
    }
    case ReportIn() => (currentPartition, firstOut) match {
      case (None, None) => sender ! true
      case _ => sender ! false
    }
    case msg => super.receive(msg)
  }
}

object TestEntropyActor {
  var failureCount:Int = 0
  var successCount:Int = 0
}

class TestEntropyActor(nodeRef:NodeRef) extends FakeEntropyActor(nodeRef) {

  override def computeAndApplyReconfigurationPlan(nodes:List[NodeRef]):Boolean = {

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
    case ReportIn() => sender ! (TestEntropyActor.failureCount, TestEntropyActor.successCount)
    case msg => {
      super.receive(msg)
    }
  }
}

object TestDvmsFactory extends DvmsAbstractFactory {
  def createMonitorActor(nodeRef:NodeRef):Option[AbstractMonitorActor] = {
    Some(new TestMonitorActor(nodeRef))
  }

  def createDvmsActor(nodeRef:NodeRef):Option[DvmsActor] = {
    Some(new TestDvmsActor(nodeRef))
  }

  def createEntropyActor(nodeRef:NodeRef):Option[AbstractEntropyActor] = {
    Some(new TestEntropyActor(nodeRef))
  }
}


class DvmsDeadlockTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {

  implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  implicit val timeout = akka.util.Timeout(1 seconds)

  Configuration.debug = true

  def this() = this(ActorSystem("MySpec"))

  override def afterAll() {
    system.shutdown()
  }

  "Deadlock" must {
    "be resolved" in {

      def quickNodeRef(l:Int, ref:ActorRef):NodeRef = NodeRef(FakeNetworkLocation(l), ref)

      val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)))
      val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)))
      val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)))
      val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)))
      val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)))
      val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)))
      val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)))
      val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)))


      // create the links
      node2 ! InitCommunicationWithHim(node1)
      node3 ! InitCommunicationWithHim(node1)
      node4 ! InitCommunicationWithHim(node1)
      node5 ! InitCommunicationWithHim(node1)
      node6 ! InitCommunicationWithHim(node1)
      node7 ! InitCommunicationWithHim(node1)
      node8 ! InitCommunicationWithHim(node1)

      val node1Ref = quickNodeRef(1 ,node1)
      val node2Ref = quickNodeRef(2 ,node2)
      val node3Ref = quickNodeRef(3 ,node3)
      val node4Ref = quickNodeRef(4 ,node4)
      val node5Ref = quickNodeRef(5 ,node5)
      val node6Ref = quickNodeRef(6 ,node6)
      val node7Ref = quickNodeRef(7 ,node7)
      val node8Ref = quickNodeRef(8 ,node8)

      // init the partitions
      val partition_1_2 = DvmsPartition(node2Ref, node1Ref, List(node1Ref, node2Ref), Growing())
      val partition_3_4 = DvmsPartition(node4Ref, node3Ref, List(node3Ref, node4Ref), Growing())
      val partition_5_6 = DvmsPartition(node6Ref, node5Ref, List(node5Ref, node6Ref), Growing())
      val partition_7_8 = DvmsPartition(node8Ref, node7Ref, List(node7Ref, node8Ref), Growing())

      node1 ! ToDvmsActor(SetCurrentPartition(partition_1_2))
      node2 ! ToDvmsActor(SetCurrentPartition(partition_1_2))

      node3 ! ToDvmsActor(SetCurrentPartition(partition_3_4))
      node4 ! ToDvmsActor(SetCurrentPartition(partition_3_4))

      node5 ! ToDvmsActor(SetCurrentPartition(partition_5_6))
      node6 ! ToDvmsActor(SetCurrentPartition(partition_5_6))

      node7 ! ToDvmsActor(SetCurrentPartition(partition_7_8))
      node8 ! ToDvmsActor(SetCurrentPartition(partition_7_8))

      node1 ! ToDvmsActor(SetFirstOut(node3Ref))
      node2 ! ToDvmsActor(SetFirstOut(node3Ref))

      node3 ! ToDvmsActor(SetFirstOut(node5Ref))
      node4 ! ToDvmsActor(SetFirstOut(node5Ref))

      node5 ! ToDvmsActor(SetFirstOut(node7Ref))
      node6 ! ToDvmsActor(SetFirstOut(node7Ref))

      node7 ! ToDvmsActor(SetFirstOut(node1Ref))
      node8 ! ToDvmsActor(SetFirstOut(node1Ref))

      // transmission of ISP to the respectives firstOuts
      node2 ! ToDvmsActor(BeginTransmission())
      node4 ! ToDvmsActor(BeginTransmission())
      node6 ! ToDvmsActor(BeginTransmission())
      node8 ! ToDvmsActor(BeginTransmission())

      Thread.sleep(8000)

      val node1StillStucked = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node2StillStucked = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node3StillStucked = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node4StillStucked = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node5StillStucked = Await.result(node5 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node6StillStucked = Await.result(node6 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node7StillStucked = Await.result(node7 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
      val node8StillStucked = Await.result(node8 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

      println(s"1: $node1StillStucked")
      println(s"2: $node2StillStucked")
      println(s"3: $node3StillStucked")
      println(s"4: $node4StillStucked")
      println(s"5: $node5StillStucked")
      println(s"6: $node6StillStucked")
      println(s"7: $node7StillStucked")
      println(s"8: $node8StillStucked")

      (node1StillStucked && node2StillStucked && node3StillStucked && node4StillStucked && node5StillStucked &&node6StillStucked &&
        node7StillStucked  && node8StillStucked) must be (true)
    }
  }
}