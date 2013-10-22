package org.discovery.dvms

import dvms.DvmsMessage
import dvms.DvmsProtocol._
import entropy.EntropyMessage
import factory.{LibvirtDvmsFactory, DvmsAbstractFactory, FakeDvmsFactory}
import log.LoggingMessage
import monitor.MonitorMessage
import org.discovery.AkkaArc.util.{NodeRef, INetworkLocation}
import org.discovery.AkkaArc.PeerActor
import akka.actor.{OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy.{Escalate, Stop, Restart, Resume}
import akka.pattern.AskTimeoutException
import scala.concurrent.duration._
import util.parsing.combinator.RegexParsers
import java.util.concurrent.TimeoutException
import configuration.{ExperimentConfiguration, DvmsConfiguration}
import org.discovery.AkkaArc.DvmsSupervisorForTestsProtocol.GetRingSize

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

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

   import org.discovery.AkkaArc.overlay.chord.ChordActor._

   def this(location: INetworkLocation) = this(
      location,
      DvmsConfiguration.FACTORY_NAME match {
         case "libvirt" => LibvirtDvmsFactory
         case _ => FakeDvmsFactory
      }
   )

   val nodeRef: NodeRef = NodeRef(location, self)

   val monitorActor = context.actorOf(Props(factory.createMonitorActor(nodeRef).get), s"Monitor@${location.getId}")
   val dvmsActor = context.actorOf(Props(factory.createDvmsActor(nodeRef).get), s"DVMS@${location.getId}")
   val entropyActor = context.actorOf(Props(factory.createEntropyActor(nodeRef).get), s"Entropy@${location.getId}")
   val loggingActor = context.actorOf(Props(factory.createLoggingActor(nodeRef).get), s"Logging@${location.getId}")

   // Register the start time of the experiment
   ExperimentConfiguration.startExperiment()

   override def receive = {
      case msg: MonitorMessage   => monitorActor.forward(msg)
      case msg: DvmsMessage      => dvmsActor.forward(msg)
      case msg: EntropyMessage   => entropyActor.forward(msg)
      case msg: LoggingMessage   => loggingActor.forward(msg)

      case msg: GetRingSize      =>
         val senderSave = sender
         for {
            size <- overlayService.ringSize()
         } yield {
            senderSave ! size
         }

      case msg => super.receive(msg)
   }

   override def onConnection() {
      for {
         neighbours <- overlayService.getNeighbourHood()
      } yield {
         log.info(s"$location: I am connected and here are my neighbors [${neighbours.mkString(",")}]")

         if (neighbours.size > 1)
            dvmsActor ! ThisIsYourNeighbor(neighbours(1))
      }
   }

   override def onDisconnection() {
      log.info(s"$location: I have been disconnected")
   }

   override def onNeighborChanged(oldNeighbor: Option[NodeRef], newNeighbor: NodeRef) {

      for {
         neighbours <- overlayService.getNeighbourHood()
      } yield {
         log.info(s"$location: one of my neighbors ($oldNeighbor) has changed, here is the new one ($newNeighbor) and here are my neighbors [${neighbours.mkString(",")}]")

         dvmsActor ! YouMayNeedToUpdateYourFirstOut(oldNeighbor, newNeighbor)

         if (neighbours.size > 1 && (newNeighbor.location isEqualTo neighbours(1).location)) {
            dvmsActor ! ThisIsYourNeighbor(neighbours(1))
         }
      }
   }

   override def onNeighborCrashed(neighbor: NodeRef) {
      for {
         neighbours <- overlayService.getNeighbourHood()
      } yield {
         log.info(s"$location: one of my neighbors ($neighbor) has crashed and here are my neighbors [${neighbours.mkString(",")}]")

         dvmsActor ! FailureDetected(neighbor)
      }
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
