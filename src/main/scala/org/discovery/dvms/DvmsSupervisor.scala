package org.discovery.dvms

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

import dvms.DvmsMessage
import entropy.{EntropyService, EntropyMessage}
import factory.{LibvirtDvmsFactory, DvmsAbstractFactory, FakeDvmsFactory}
import log.LoggingMessage
import monitor.MonitorMessage
import org.discovery.peeractor.util.{NodeRef, INetworkLocation}
import org.discovery.peeractor.PeerActor
import akka.actor.{OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy.Restart
import scala.concurrent.duration._
import configuration.{ExperimentConfiguration, DvmsConfiguration}
import org.discovery.peeractor.overlay.OverlayServiceFactory
import org.discovery.peeractor.notification.ChordServiceWithNotificationFactory

class DvmsSupervisor(location: INetworkLocation, factory: DvmsAbstractFactory, overlayFactory: OverlayServiceFactory = ChordServiceWithNotificationFactory) extends PeerActor(location, overlayFactory) {


  def this(location: INetworkLocation, overlayFactory: OverlayServiceFactory = ChordServiceWithNotificationFactory) = this(
    location,
    DvmsConfiguration.FACTORY_NAME match {
      case "libvirt" => LibvirtDvmsFactory
      case _ => FakeDvmsFactory
    },
    overlayFactory
  )

  val nodeRef: NodeRef = NodeRef(location, self)

  val monitorActor = context.actorOf(Props(factory.createMonitorActor(nodeRef).get), s"Monitor@${location.getId}")
  val dvmsActor = context.actorOf(Props(factory.createDvmsActor(nodeRef, overlayService).get), s"DVMS@${location.getId}")
  val entropyActor = context.actorOf(Props(factory.createEntropyActor(nodeRef).get), s"Entropy@${location.getId}")
  val loggingActor = context.actorOf(Props(factory.createLoggingActor(nodeRef).get), s"Logging@${location.getId}")

  EntropyService.setLoggingActorRef(loggingActor)

  // Register the start time of the experiment
  ExperimentConfiguration.startExperiment()

  override def receive = {
    case msg: MonitorMessage => monitorActor.forward(msg)
    case msg: DvmsMessage => dvmsActor.forward(msg)
    case msg: EntropyMessage => entropyActor.forward(msg)
    case msg: LoggingMessage => loggingActor.forward(msg)

    case msg =>
      super.receive(msg)
  }

  override def onConnection() {}

  override def onDisconnection() {}

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 1 second) {
      case e: Exception =>
        e.printStackTrace()
        Restart
    }
}
