package org.discovery.dvms.service

import org.discovery.AkkaArc.util.Configuration
import org.discovery.AkkaArc.util.FakeNetworkLocation
import org.discovery.AkkaArc.PeerActorProtocol.ConnectToThisPeerActor
import akka.actor.{Props, ActorSystem}
import org.discovery.dvms.DvmsSupervisor
import org.discovery.dvms.factory.FakeDvmsFactory

object Main extends App {


  val system = ActorSystem(s"DvmsSystem", Configuration.generateLocalActorConfiguration)

  val node1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1), FakeDvmsFactory)))
  val node2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2), FakeDvmsFactory)))
  val node3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3), FakeDvmsFactory)))
  val node4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4), FakeDvmsFactory)))

  // create the links
  node2 ! ConnectToThisPeerActor(node1)
  node3 ! ConnectToThisPeerActor(node1)
  node4 ! ConnectToThisPeerActor(node1)


  Thread.sleep(2000)

}
