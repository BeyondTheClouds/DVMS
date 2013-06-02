package org.discovery.dvms

import akka.actor.{ActorSystem, Props}
import factory.{LibvirtDvmsFactory, FakeDvmsFactory, DvmsAbstractFactory}
import java.util.{Properties, IllegalFormatConversionException}
import org.discovery.AkkaArc.InitCommunicationWithHim
import collection.mutable
import org.discovery.AkkaArc.util.{Configuration, NetworkLocation}
import com.typesafe.config.ConfigFactory
import java.io.FileInputStream

object Main extends App {

   var argumentHashMap = mutable.HashMap[String, String]()

   args.foreach(arg => {
      val argArray = arg.split("=")
      argArray.size match {
         case 1 => argumentHashMap += (argArray(0) -> "")
         case 2 => argumentHashMap += (argArray(0) -> argArray(1))
         case 3 => throw new Exception(s"Invalid argument: $arg")
      }

   })


   var aborted = false

   val ip = argumentHashMap.contains("ip") match {
      case true => argumentHashMap("ip")
      case false => throw new Exception("please specify <ip> argument")
   }
   val port = argumentHashMap.contains("port") match {
      case true => argumentHashMap("port")
      case false => throw new Exception("please specify <port> argument")
   }
   val debug = argumentHashMap.contains("debug") match {
      case true => argumentHashMap("debug")
      case false => "false"
   }

   Configuration.debug = debug.toBoolean
   Configuration.firstId = -1

   println(s"launching a chord node: $ip@$port")
   try {

      val customConf = ConfigFactory.parseString(s"""

akka {
    actor {
      provider = "akka.remote.RemoteActorRefProvider"
    }
    remote {
      enabled-transports = ["akka.remote.netty.tcp"]
      netty.tcp {
        hostname = "$ip"
        port = $port
      }

      netty {
         use-passive-connections = on
      }

      netty.udp = {
        hostname = "$ip"
        port = $port
      }
      netty.udp {
         transport-protocol = udp
      }
   }
  }

""")

      val system = ActorSystem("DvmsSystem", ConfigFactory.load(customConf))
      val name = "peer"
      val location = NetworkLocation(ip, port.toInt, name)
      println(s"Adding new actor: $port with and id:${location.getId}")

      val properties:Properties=new Properties();
      val in:FileInputStream =new FileInputStream("configuration/dvms.cfg");
      try {
         properties.load(in);
         in.close();
      }

      val factory:DvmsAbstractFactory = properties.getProperty("dvms.factory", "fake") match {
         case s:String if(s.equals("fake")) => FakeDvmsFactory
         case s:String if(s.equals("libvirt")) => LibvirtDvmsFactory
         case _ => FakeDvmsFactory
      }


      if(argumentHashMap.contains("remote_ip") && argumentHashMap.contains("remote_port")) {

         val remote_ip = argumentHashMap("remote_ip")
         val remote_port = argumentHashMap("remote_port")

         println(s"Initiate the communication with $remote_ip@$remote_port")
         val remotePeer = system.actorFor(s"akka.tcp://DvmsSystem@$remote_ip:$remote_port/user/peer")

         val peer = system.actorOf(Props(new DvmsSupervisor(location, factory)), name=name)
         peer ! InitCommunicationWithHim(remotePeer)
      } else {

         val peer = system.actorOf(Props(new DvmsSupervisor(location, factory)), name=name)
      }




   }
   catch {
      case e:IllegalFormatConversionException => {
         println(s"It seems that the given port <$port> cannot be converted as a number")
         e.printStackTrace()
         aborted = true
      }
      case e:Exception => {
         e.printStackTrace()
         aborted = true
      }
   }

   if(aborted) {
      println(s"An error occured during the creation of $ip@$port")
   }
}