package org.discovery.akka

import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import akka.pattern.ask
import org.discovery.dvms.monitor.MonitorProtocol.Tick
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import akka.routing.RoundRobinRouter


case object Msg
case object StartFault

class Foo(var remote: Option[ActorRef]) extends Actor with ActorLogging {

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  implicit val askTimeout = Timeout(5 seconds)

  def this() = this(None)

  override def receive = {

    case StartFault =>
      subActor ! new Exception("Voluntar crash")

    case Tick() =>
      for {
        subActorsMessage: String <- (subActor ? "What is your message?").mapTo[String]
      } yield {
        log.info(s"subactor: $subActorsMessage")
      }

    case msg =>

      remote match {
        case None =>
          remote = Some(sender)
        case _ =>
      }

      remote match {
        case Some(actorRef) =>
          log.info(s"received $msg")
          Thread.sleep(500)
          actorRef ! Msg
        case _ =>
      }
  }

  class SubActor extends Actor with ActorLogging {

    @throws(classOf[Exception])
    override def receive = {
      case exception: Exception =>
        throw exception

      case msg =>
        log.info(s"$msg")
        sender ! "cool"

    }


  }

  val stopStrategy = OneForOneStrategy() {
    case _ ⇒ {
      log.info("caught error!")
      Stop
    }
  }

  val subActor = context.system.actorOf(Props(new SubActor()).withRouter(
    RoundRobinRouter(1, supervisorStrategy = stopStrategy)), self.path.name+"-sub")




  context.system.scheduler.schedule(0 milliseconds,
    1 second,
    self,
    Tick())

}

object Main extends App {


  val config = ConfigFactory.parseString("""
    akka.loglevel = "DEBUG"
    akka.actor.debug {
      receive = on
      lifecycle = on
    }
                                         """)

  val system = ActorSystem("FaultToleranceSample", config)

  val foo1 = system.actorOf(Props(new Foo()), s"Foo1")
  val foo2 = system.actorOf(Props(new Foo(Some(foo1))), s"Foo2")

  foo2 ! Msg

  lazy val waitForTwoSecondsFuture = Future {
    Thread.sleep(2000)
  }

  for {
    result <- waitForTwoSecondsFuture
  } yield {
    println("start fault?")
    foo2 ! StartFault
  }

  println("before cool")

  system.awaitTermination(8 seconds)

}