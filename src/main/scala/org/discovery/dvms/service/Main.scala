package org.discovery.dvms.service

import org.discovery.AkkaArc.util.Configuration
import org.discovery.AkkaArc.util.FakeNetworkLocation
import org.discovery.AkkaArc.PeerActorProtocol.ConnectToThisPeerActor
import akka.actor.{Props, ActorSystem}
import org.discovery.dvms.DvmsSupervisor
import org.discovery.dvms.factory.FakeDvmsFactory
import org.discovery.AkkaArc.overlay.vivaldi.VivaldiServiceFactory

object Main extends App {


  val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

  val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), FakeDvmsFactory, VivaldiServiceFactory)))
  val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), FakeDvmsFactory, VivaldiServiceFactory)))
  val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), FakeDvmsFactory, VivaldiServiceFactory)))
  val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), FakeDvmsFactory, VivaldiServiceFactory)))

  // create the links
  node2 ! ConnectToThisPeerActor(node1)
  node3 ! ConnectToThisPeerActor(node1)
  node4 ! ConnectToThisPeerActor(node1)


  Thread.sleep(2000)

}
