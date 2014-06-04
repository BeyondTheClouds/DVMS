package org.discovery.dvms

import akka.actor._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.discovery.dvms.log.LoggingActor
import org.discovery.peeractor.util.FakeNetworkLocation
import org.discovery.dvms.log.LoggingProtocol._


object LoggingTest extends App {


  val config = ConfigFactory.parseString("""
    akka.loglevel = "DEBUG"
    akka.actor.debug {
      receive = on
      lifecycle = on
    }
                                         """)

  val system = ActorSystem("FaultToleranceSample", config)

  val loggingActor = system.actorOf(Props(new LoggingActor(FakeNetworkLocation(44))), s"loggingActor")

  loggingActor ! ComputingSomeReconfigurationPlan(1)
  loggingActor ! ApplyingSomeReconfigurationPlan(2)
  loggingActor ! ApplicationSomeReconfigurationPlanIsDone(3)
  loggingActor ! IsBooked(4)
  loggingActor ! IsFree(5)
  loggingActor ! CurrentLoadIs(6, 56.29)
  loggingActor ! ViolationDetected(7)
  loggingActor ! UpdateMigrationCount(8, 22)
  loggingActor ! AskingMigration(9, "vm44", 29, 44)
  loggingActor ! StartingMigration(10, "vm56", 44, 35)
  loggingActor ! FinishingMigration(11, "vm29", 35, 22)
  loggingActor ! AbortingMigration(12, "vm22", 22, 29)

  system.awaitTermination(8 seconds)

}