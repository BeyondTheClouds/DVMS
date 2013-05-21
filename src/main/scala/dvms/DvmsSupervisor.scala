package dvms

import dvms._
import dvms.ThisIsYourNeighbor
import dvms.ToDvmsActor
import dvms.ToMonitorActor
import factory.{DvmsAbstractFactory, DvmsFactory}
import org.bbk.AkkaArc.util.{NodeRef, INetworkLocation}
import org.bbk.AkkaArc.PeerActor
import akka.actor.{OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy.{Escalate, Stop, Restart, Resume}
import akka.pattern.AskTimeoutException
import scala.concurrent.duration._
import util.parsing.combinator.RegexParsers
import java.util.concurrent.TimeoutException

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:13 PM
 * To change this template use File | Settings | File Templates.
 */

object ActorIdParser extends RegexParsers {
   def chain: Parser[String] = """[^#]*""".r ~ "#" ~> id <~ ".*".r ^^ {
      case i => i.toString()
   }

   def id: Parser[String] = integer ^^ {
      case i => i.toString
   }

   def integer = """[-]?(0|[1-9]\d*)""".r ^^ {
      _.toInt
   }
}

class DvmsSupervisor(location: INetworkLocation, factory: DvmsAbstractFactory) extends PeerActor(location) {

   def this(location: INetworkLocation) = this(location, DvmsFactory)

   val nodeRef: NodeRef = NodeRef(location, self)

   val monitorActor = context.actorOf(Props(factory.createMonitorActor(nodeRef).get), s"Monitor@${location.getId}")
   val dvmsActor = context.actorOf(Props(factory.createDvmsActor(nodeRef).get), s"DVMS@${location.getId}")
   val entropyActor = context.actorOf(Props(factory.createEntropyActor(nodeRef).get), s"Entropy@${location.getId}")

   override def receive = {
      case ToMonitorActor(msg) => monitorActor.forward(msg)
      case ToDvmsActor(msg) => dvmsActor.forward(msg)
      case ToEntropyActor(msg) => entropyActor.forward(msg)
      case msg => super.receive(msg)
   }

   override def onConnection() {
      log.info(s"$location: I am connected and here are my neighbors [${getNeighborHood.mkString(",")}]")

      if (getNeighborHood.size > 1)
         dvmsActor ! ThisIsYourNeighbor(getNeighborHood(1))
   }

   override def onDisconnection() {
      log.info(s"$location: I have been disconnected")
   }

   override def onNeighborChanged(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) {
      log.info(s"$location: one of my neighbors ($oldNeighbor) has changed, here is the new one ($newNeighbor) and here are my neighbors [${getNeighborHood.mkString(",")}]")

      dvmsActor ! YouMayNeedToUpdateYourFirstOut(oldNeighbor, newNeighbor)

      if (getNeighborHood.size > 1 && (newNeighbor.location isEqualTo getNeighborHood(1).location)) {
         dvmsActor ! ThisIsYourNeighbor(getNeighborHood(1))
      }
   }

   override def onNeighborCrashed(neighbor: NodeRef) {
      log.info(s"$location: one of my neighbors ($neighbor) has crashed and here are my neighbors [${getNeighborHood.mkString(",")}]")

      dvmsActor ! FailureDetected(neighbor)
   }

   override val supervisorStrategy =
      OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 1 second) {
         case e: AskTimeoutException => {


            dvmsActor ! AskTimeoutDetected(e)

            Resume
         }

         case e: TimeoutException => {
            Resume
         }

         case _: NullPointerException => Restart
         case _: IllegalArgumentException => Stop
         case _: Exception => Escalate
      }
}
