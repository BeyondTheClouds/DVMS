package org.discovery.dvms.service

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


import org.discovery.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import scala.concurrent.duration._
import org.discovery.dvms.service.ServiceProtocol._
import org.discovery.AkkaArc.overlay.chord.ChordService

class ServiceActor(applicationRef: NodeRef, overlayService: ChordService) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   override def receive = {

      case GetOverlaySize()  =>
         val senderSave = sender
         for {
            size <- overlayService.ringSize()
         } yield {
            senderSave ! size
         }

      case MigrateVm(vmName, ip, port)  =>
         sender ! ServiceProtocol.CannotExecuteAction(Some("Not yet implemented!"))

      case msg => applicationRef.ref.forward(msg)
   }
}