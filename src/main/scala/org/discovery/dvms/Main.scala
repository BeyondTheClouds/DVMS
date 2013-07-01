package org.discovery.dvms

/* ============================================================
 * Discovery Project - AkkaArc
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

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.discovery.AkkaArc.util.{Configuration, NetworkLocation}
import scala.concurrent.duration._
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import java.util.IllegalFormatConversionException
import akka.util.Timeout
import collection.mutable
import org.discovery.AkkaArc.{PeerActor, ConnectToThisPeerActor}

object Main extends App {

   override def main(args: Array[String]) {
      implicit val timeout = Timeout(1 seconds)
      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

      var argumentHashMap = new mutable.HashMap[String, String]()

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

      //      Configuration.debug = debug.toBoolean

      Configuration.debug = true
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
         val location = NetworkLocation(ip, port.toInt)
         println(s"Adding new actor: $port with and id:${location.getId}")


         if(argumentHashMap.contains("remote_ip") && argumentHashMap.contains("remote_port")) {

            val remote_ip = argumentHashMap("remote_ip")
            val remote_port = argumentHashMap("remote_port")

            println(s"Initiate the communication with $remote_ip@$remote_port")
            val remotePeer = system.actorFor(s"akka.tcp://DvmsSystem@$remote_ip:$remote_port/user/peer")

            val peer = system.actorOf(Props(new DvmsSupervisor(location)), name=name)
            peer ! ConnectToThisPeerActor(remotePeer)
         } else {

            val peer = system.actorOf(Props(new DvmsSupervisor(location)), name=name)
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
}