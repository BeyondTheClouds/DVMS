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
import com.typesafe.config.ConfigFactory


object DvmsResiliencyTest {

}

class DvmsResiliencyTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpec with MustMatchers with BeforeAndAfterAll {

   implicit def intToLocation(i: Long): INetworkLocation = new FakeNetworkLocation(i)

   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   implicit val timeout = akka.util.Timeout(1 seconds)

   Configuration.debug = true

   def this() = this(ActorSystem("MySpec", ConfigFactory.parseString("""
     prio-dispatcher {
       mailbox-type = "dvms.utility.DvmsPriorityMailBox"
     }
   """)))

   override def afterAll() {
      system.shutdown()
   }

   "Deadlock resolver" must {

      "handle a combined crash in partitions (ring of 12 nodes)" in {

         def quickNodeRef(l:Int, ref:ActorRef):NodeRef = NodeRef(FakeNetworkLocation(l), ref)

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


         val node1Ref = quickNodeRef(1 ,node1)
         val node2Ref = quickNodeRef(2 ,node2)
         val node3Ref = quickNodeRef(3 ,node3)
         val node4Ref = quickNodeRef(4 ,node4)
         val node5Ref = quickNodeRef(5 ,node5)
         val node6Ref = quickNodeRef(6 ,node6)
         val node7Ref = quickNodeRef(7 ,node7)
         val node8Ref = quickNodeRef(8 ,node8)
         val node9Ref = quickNodeRef(9 ,node9)
         val node10Ref = quickNodeRef(10 ,node10)
         val node11Ref = quickNodeRef(11 ,node11)
         val node12Ref = quickNodeRef(12 ,node12)

         // init the partitions
         val partition_1_2_3_4_10  = DvmsPartition(node10Ref, node1Ref, List(node1Ref, node2Ref, node3Ref, node4Ref, node10Ref), Growing())

         node1 ! ToDvmsActor(SetCurrentPartition(partition_1_2_3_4_10))
         node2 ! ToDvmsActor(SetCurrentPartition(partition_1_2_3_4_10))
         node3 ! ToDvmsActor(SetCurrentPartition(partition_1_2_3_4_10))
         node4 ! ToDvmsActor(SetCurrentPartition(partition_1_2_3_4_10))
         node10 ! ToDvmsActor(SetCurrentPartition(partition_1_2_3_4_10))

         node1 ! ToDvmsActor(SetFirstOut(node5Ref))
         node2 ! ToDvmsActor(SetFirstOut(node5Ref))
         node3 ! ToDvmsActor(SetFirstOut(node5Ref))
         node4 ! ToDvmsActor(SetFirstOut(node5Ref))

         node10 ! ToDvmsActor(SetFirstOut(node11Ref))
//         node10 ! ToDvmsActor(EverythingIsOkToken(partition_1_2_3_4_10.id))


         // killing node4
         println("Killing node4")
         node4.tell(PoisonPill.getInstance, null)

         Thread.sleep(200)

         // transmission of ISP to the respectives firstOuts
         node10 ! ToDvmsActor(BeginTransmission())


         Thread.sleep(10000)

         val node1IsOk = Await.result(node1 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node2IsOk = Await.result(node2 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
         val node3IsOk = Await.result(node3 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
//         val node4IsOk = Await.result(node4 ? ToDvmsActor(ReportIn()), 1 second).asInstanceOf[Boolean]
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
         println(s"5: $node5IsOk")
         println(s"6: $node6IsOk")
         println(s"7: $node7IsOk")
         println(s"8: $node8IsOk")
         println(s"9: $node9IsOk")
         println(s"10: $node10IsOk")
         println(s"11: $node11IsOk")
         println(s"12: $node12IsOk")

         (node1IsOk && node2IsOk && node3IsOk && node5IsOk &&node6IsOk &&
           node7IsOk  && node8IsOk && node9IsOk && node10IsOk  && node11IsOk && node12IsOk) must be (true)
      }

   }
}