package dvms


import akka.actor._
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import dvms._
import dvms.DvmsPartition
import dvms.ToDvmsActor
import entropy.{AbstractEntropyActor, FakeEntropyActor}
import factory.DvmsAbstractFactory
import monitor.{AbstractMonitorActor, FakeMonitorActor}
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.BeforeAndAfterAll
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
import java.util.Date


object DvmsDeadlockTest {

}


object TestData {

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   val hashLoad: HashMap[INetworkLocation, List[Double]] = HashMap(
      (intToLocation(1) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(2) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(3) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(4) -> List(110, 20, -1, -1, -1, -1, -1)),
      (intToLocation(5) -> List(110, -1, 30, -1, -1, -1, -1)),
      (intToLocation(6) -> List(110, 20, -1, -1, -1, -1, -1)),
      (intToLocation(7) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(8) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(9) -> List(110, 20, -1, -1, -1, -1, -1)),
      (intToLocation(10) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(11) -> List(110, -1, -1, -1, -1, -1, -1)),
      (intToLocation(12) -> List(110, 20, -1, -1, -1, -1, -1))
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

case class SetCurrentPartition(partition: DvmsPartition)

case class SetFirstOut(firstOut: NodeRef)

case class BeginTransmission()

class TestDvmsActor(applicationRef: NodeRef) extends DvmsActor(applicationRef) {

   override def receive = {
      case msg@SetCurrentPartition(partition) => {
         currentPartition = Some(partition)
         lastPartitionUpdateDate = Some(new Date())
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
      Some(new TestDvmsActor(nodeRef))
   }

   def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor] = {
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

   "Deadlock resolver" must {
      "resolve a linear deadlock" in {

         def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)).withDispatcher("prio-dispatcher"))


         // create the links
         node2 ! InitCommunicationWithHim(node1)
         node3 ! InitCommunicationWithHim(node1)
         node4 ! InitCommunicationWithHim(node1)
         node5 ! InitCommunicationWithHim(node1)
         node6 ! InitCommunicationWithHim(node1)
         node7 ! InitCommunicationWithHim(node1)
         node8 ! InitCommunicationWithHim(node1)


         Thread.sleep(500)


         val node1Ref = quickNodeRef(1, node1)
         val node2Ref = quickNodeRef(2, node2)
         val node3Ref = quickNodeRef(3, node3)
         val node4Ref = quickNodeRef(4, node4)
         val node5Ref = quickNodeRef(5, node5)
         val node6Ref = quickNodeRef(6, node6)
         val node7Ref = quickNodeRef(7, node7)
         val node8Ref = quickNodeRef(8, node8)

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

         Thread.sleep(3000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node5IsOk = Await.result(node5 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node6IsOk = Await.result(node6 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node7IsOk = Await.result(node7 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node8IsOk = Await.result(node8 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

         println(s"1: $node1IsOk")
         println(s"2: $node2IsOk")
         println(s"3: $node3IsOk")
         println(s"4: $node4IsOk")
         println(s"5: $node5IsOk")
         println(s"6: $node6IsOk")
         println(s"7: $node7IsOk")
         println(s"8: $node8IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
           node7IsOk && node8IsOk) must be(true)
      }


      "resolve a nested deadlock (4 nodes)" in {

         def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))


         // create the links
         node2 ! InitCommunicationWithHim(node1)
         node3 ! InitCommunicationWithHim(node1)
         node4 ! InitCommunicationWithHim(node1)


         Thread.sleep(500)


         val node1Ref = quickNodeRef(1, node1)
         val node2Ref = quickNodeRef(2, node2)
         val node3Ref = quickNodeRef(3, node3)
         val node4Ref = quickNodeRef(4, node4)

         // init the partitions
         val partition_1_3 = DvmsPartition(node3Ref, node1Ref, List(node1Ref, node3Ref), Growing())
         val partition_2_4 = DvmsPartition(node4Ref, node2Ref, List(node2Ref, node4Ref), Growing())


         node1 ! ToDvmsActor(SetCurrentPartition(partition_1_3))
         node3 ! ToDvmsActor(SetCurrentPartition(partition_1_3))

         node2 ! ToDvmsActor(SetCurrentPartition(partition_2_4))
         node4 ! ToDvmsActor(SetCurrentPartition(partition_2_4))

         node1 ! ToDvmsActor(SetFirstOut(node2Ref))
         node3 ! ToDvmsActor(SetFirstOut(node4Ref))

         node2 ! ToDvmsActor(SetFirstOut(node3Ref))
         node4 ! ToDvmsActor(SetFirstOut(node1Ref))

         // transmission of ISP to the respectives firstOuts
         node3 ! ToDvmsActor(BeginTransmission())
         node4 ! ToDvmsActor(BeginTransmission())

         Thread.sleep(3000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

         println(s"1: $node1IsOk")
         println(s"2: $node2IsOk")
         println(s"3: $node3IsOk")
         println(s"4: $node4IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node4IsOk) must be(true)
      }


      "resolve a nested deadlock (6 nodes)" in {

         def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)).withDispatcher("prio-dispatcher"))


         // create the links
         node2 ! InitCommunicationWithHim(node1)
         node3 ! InitCommunicationWithHim(node1)
         node4 ! InitCommunicationWithHim(node1)
         node5 ! InitCommunicationWithHim(node1)
         node6 ! InitCommunicationWithHim(node1)


         Thread.sleep(500)


         val node1Ref = quickNodeRef(1, node1)
         val node2Ref = quickNodeRef(2, node2)
         val node3Ref = quickNodeRef(3, node3)
         val node4Ref = quickNodeRef(4, node4)
         val node5Ref = quickNodeRef(5, node5)
         val node6Ref = quickNodeRef(6, node6)

         // init the partitions
         val partition_1_3_5 = DvmsPartition(node5Ref, node1Ref, List(node1Ref, node3Ref, node5Ref), Growing())
         val partition_2_4_6 = DvmsPartition(node6Ref, node2Ref, List(node2Ref, node4Ref, node6Ref), Growing())


         node1 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))
         node3 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))
         node5 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))

         node2 ! ToDvmsActor(SetCurrentPartition(partition_2_4_6))
         node4 ! ToDvmsActor(SetCurrentPartition(partition_2_4_6))
         node6 ! ToDvmsActor(SetCurrentPartition(partition_2_4_6))

         node1 ! ToDvmsActor(SetFirstOut(node2Ref))
         node3 ! ToDvmsActor(SetFirstOut(node4Ref))
         node5 ! ToDvmsActor(SetFirstOut(node6Ref))

         node2 ! ToDvmsActor(SetFirstOut(node3Ref))
         node4 ! ToDvmsActor(SetFirstOut(node5Ref))
         node6 ! ToDvmsActor(SetFirstOut(node1Ref))

         // transmission of ISP to the respectives firstOuts
         node5 ! ToDvmsActor(BeginTransmission())
         node6 ! ToDvmsActor(BeginTransmission())

         Thread.sleep(3000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node5IsOk = Await.result(node5 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node6IsOk = Await.result(node6 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

         println(s"1: $node1IsOk")
         println(s"2: $node2IsOk")
         println(s"3: $node3IsOk")
         println(s"4: $node4IsOk")
         println(s"5: $node5IsOk")
         println(s"6: $node6IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk) must be(true)
      }

      "resolve a nested deadlock (9 nodes)" in {

         def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node9 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(9), TestDvmsFactory)).withDispatcher("prio-dispatcher"))


         // create the links
         node2 ! InitCommunicationWithHim(node1)
         node3 ! InitCommunicationWithHim(node1)
         node4 ! InitCommunicationWithHim(node1)
         node5 ! InitCommunicationWithHim(node1)
         node6 ! InitCommunicationWithHim(node1)
         node7 ! InitCommunicationWithHim(node1)
         node8 ! InitCommunicationWithHim(node1)
         node9 ! InitCommunicationWithHim(node1)


         Thread.sleep(500)


         val node1Ref = quickNodeRef(1, node1)
         val node2Ref = quickNodeRef(2, node2)
         val node3Ref = quickNodeRef(3, node3)
         val node4Ref = quickNodeRef(4, node4)
         val node5Ref = quickNodeRef(5, node5)
         val node6Ref = quickNodeRef(6, node6)
         val node7Ref = quickNodeRef(7, node7)
         val node8Ref = quickNodeRef(8, node8)
         val node9Ref = quickNodeRef(9, node9)

         // init the partitions
         val partition_1_3_5 = DvmsPartition(node5Ref, node1Ref, List(node1Ref, node3Ref, node5Ref), Growing())
         val partition_2_6_8 = DvmsPartition(node8Ref, node2Ref, List(node2Ref, node6Ref, node8Ref), Growing())
         val partition_4_7_9 = DvmsPartition(node9Ref, node4Ref, List(node4Ref, node7Ref, node9Ref), Growing())


         node1 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))
         node3 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))
         node5 ! ToDvmsActor(SetCurrentPartition(partition_1_3_5))

         node2 ! ToDvmsActor(SetCurrentPartition(partition_2_6_8))
         node6 ! ToDvmsActor(SetCurrentPartition(partition_2_6_8))
         node8 ! ToDvmsActor(SetCurrentPartition(partition_2_6_8))

         node4 ! ToDvmsActor(SetCurrentPartition(partition_4_7_9))
         node7 ! ToDvmsActor(SetCurrentPartition(partition_4_7_9))
         node9 ! ToDvmsActor(SetCurrentPartition(partition_4_7_9))

         node1 ! ToDvmsActor(SetFirstOut(node2Ref))
         node3 ! ToDvmsActor(SetFirstOut(node4Ref))
         node5 ! ToDvmsActor(SetFirstOut(node6Ref))

         node2 ! ToDvmsActor(SetFirstOut(node3Ref))
         node6 ! ToDvmsActor(SetFirstOut(node7Ref))
         node8 ! ToDvmsActor(SetFirstOut(node9Ref))

         node4 ! ToDvmsActor(SetFirstOut(node5Ref))
         node7 ! ToDvmsActor(SetFirstOut(node8Ref))
         node9 ! ToDvmsActor(SetFirstOut(node1Ref))

         // transmission of ISP to the respectives firstOuts
         node5 ! ToDvmsActor(BeginTransmission())
         node8 ! ToDvmsActor(BeginTransmission())
         node9 ! ToDvmsActor(BeginTransmission())

         Thread.sleep(6000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node5IsOk = Await.result(node5 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node6IsOk = Await.result(node6 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node7IsOk = Await.result(node7 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node8IsOk = Await.result(node8 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node9IsOk = Await.result(node9 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

         println(s"1: $node1IsOk")
         println(s"2: $node2IsOk")
         println(s"3: $node3IsOk")
         println(s"4: $node4IsOk")
         println(s"5: $node5IsOk")
         println(s"6: $node6IsOk")
         println(s"7: $node7IsOk")
         println(s"8: $node8IsOk")
         println(s"9: $node9IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
           node7IsOk && node8IsOk && node9IsOk) must be(true)
      }

      "resolve a nested deadlock (12 nodes)" in {

         def quickNodeRef(l: Int, ref: ActorRef): NodeRef = NodeRef(FakeNetworkLocation(l), ref)

         val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node5 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(5), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node6 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(6), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node7 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(7), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node8 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(8), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node9 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(9), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node10 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(10), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node11 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(11), TestDvmsFactory)).withDispatcher("prio-dispatcher"))
         val node12 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(12), TestDvmsFactory)).withDispatcher("prio-dispatcher"))


         // create the links
         node2 ! InitCommunicationWithHim(node1)
         node3 ! InitCommunicationWithHim(node1)
         node4 ! InitCommunicationWithHim(node1)
         node5 ! InitCommunicationWithHim(node1)
         node6 ! InitCommunicationWithHim(node1)
         node7 ! InitCommunicationWithHim(node1)
         node8 ! InitCommunicationWithHim(node1)
         node9 ! InitCommunicationWithHim(node1)
         node10 ! InitCommunicationWithHim(node1)
         node11 ! InitCommunicationWithHim(node1)
         node12 ! InitCommunicationWithHim(node1)


         Thread.sleep(500)


         val node1Ref = quickNodeRef(1, node1)
         val node2Ref = quickNodeRef(2, node2)
         val node3Ref = quickNodeRef(3, node3)
         val node4Ref = quickNodeRef(4, node4)
         val node5Ref = quickNodeRef(5, node5)
         val node6Ref = quickNodeRef(6, node6)
         val node7Ref = quickNodeRef(7, node7)
         val node8Ref = quickNodeRef(8, node8)
         val node9Ref = quickNodeRef(9, node9)
         val node10Ref = quickNodeRef(10, node10)
         val node11Ref = quickNodeRef(11, node11)
         val node12Ref = quickNodeRef(12, node12)

         // init the partitions
         val partition_1_5_9 = DvmsPartition(node9Ref, node1Ref, List(node1Ref, node5Ref, node9Ref), Growing())
         val partition_2_6_10 = DvmsPartition(node10Ref, node2Ref, List(node2Ref, node6Ref, node10Ref), Growing())
         val partition_3_7_11 = DvmsPartition(node11Ref, node3Ref, List(node3Ref, node7Ref, node11Ref), Growing())
         val partition_4_8_12 = DvmsPartition(node12Ref, node4Ref, List(node4Ref, node8Ref, node12Ref), Growing())


         node1 ! ToDvmsActor(SetCurrentPartition(partition_1_5_9))
         node5 ! ToDvmsActor(SetCurrentPartition(partition_1_5_9))
         node9 ! ToDvmsActor(SetCurrentPartition(partition_1_5_9))

         node2 ! ToDvmsActor(SetCurrentPartition(partition_2_6_10))
         node6 ! ToDvmsActor(SetCurrentPartition(partition_2_6_10))
         node10 ! ToDvmsActor(SetCurrentPartition(partition_2_6_10))

         node3 ! ToDvmsActor(SetCurrentPartition(partition_3_7_11))
         node7 ! ToDvmsActor(SetCurrentPartition(partition_3_7_11))
         node11 ! ToDvmsActor(SetCurrentPartition(partition_3_7_11))

         node4 ! ToDvmsActor(SetCurrentPartition(partition_4_8_12))
         node8 ! ToDvmsActor(SetCurrentPartition(partition_4_8_12))
         node12 ! ToDvmsActor(SetCurrentPartition(partition_4_8_12))

         node1 ! ToDvmsActor(SetFirstOut(node2Ref))
         node5 ! ToDvmsActor(SetFirstOut(node6Ref))
         node9 ! ToDvmsActor(SetFirstOut(node10Ref))

         node2 ! ToDvmsActor(SetFirstOut(node3Ref))
         node6 ! ToDvmsActor(SetFirstOut(node7Ref))
         node10 ! ToDvmsActor(SetFirstOut(node11Ref))

         node3 ! ToDvmsActor(SetFirstOut(node4Ref))
         node7 ! ToDvmsActor(SetFirstOut(node8Ref))
         node11 ! ToDvmsActor(SetFirstOut(node12Ref))

         node4 ! ToDvmsActor(SetFirstOut(node5Ref))
         node8 ! ToDvmsActor(SetFirstOut(node9Ref))
         node12 ! ToDvmsActor(SetFirstOut(node1Ref))

         // transmission of ISP to the respectives firstOuts
         node9 ! ToDvmsActor(BeginTransmission())
         node10 ! ToDvmsActor(BeginTransmission())
         node11 ! ToDvmsActor(BeginTransmission())
         node12 ! ToDvmsActor(BeginTransmission())

         Thread.sleep(6000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node5IsOk = Await.result(node5 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node6IsOk = Await.result(node6 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node7IsOk = Await.result(node7 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node8IsOk = Await.result(node8 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node9IsOk = Await.result(node9 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node10IsOk = Await.result(node10 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node11IsOk = Await.result(node11 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node12IsOk = Await.result(node12 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]

         println(s"1: $node1IsOk")
         println(s"2: $node2IsOk")
         println(s"3: $node3IsOk")
         println(s"4: $node4IsOk")
         println(s"5: $node5IsOk")
         println(s"6: $node6IsOk")
         println(s"7: $node7IsOk")
         println(s"8: $node8IsOk")
         println(s"9: $node9IsOk")
         println(s"10: $node10IsOk")
         println(s"11: $node11IsOk")
         println(s"12: $node12IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node4IsOk && node5IsOk && node6IsOk &&
           node7IsOk && node8IsOk && node9IsOk && node10IsOk && node11IsOk && node12IsOk) must be(true)
      }

   }
}