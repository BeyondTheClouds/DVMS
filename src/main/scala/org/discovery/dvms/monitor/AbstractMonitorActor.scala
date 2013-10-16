package org.discovery.dvms.monitor

import org.discovery.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import org.discovery.AkkaArc.notification.{TriggerEvent}
import scala.concurrent.duration._
import org.discovery.dvms.dvms.DvmsModel._
import org.discovery.AkkaArc.PeerActorProtocol.ToNotificationActor
import org.discovery.AkkaArc.notification._
import org.discovery.dvms.monitor.MonitorProtocol._

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:12 PM
 * To change this template use File | Settings | File Templates.
 */

object MonitorEventsTypes {
   case class OnCpuViolation() extends EventType
}

object MonitorEvent {
   class CpuViolation() extends Event {
      def getType: EventType = MonitorEventsTypes.OnCpuViolation()
   }
}



abstract class AbstractMonitorActor(applicationRef: NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

   case class Tick()

   var cpuConsumption: Double = 0


   def getVmsWithConsumption(): PhysicalNode

   def uploadCpuConsumption(): Double

   override def receive = {
      case Tick() => {

         cpuConsumption = uploadCpuConsumption()

         log.info(s"the new consumption is : $cpuConsumption")

         if (cpuConsumption > 100) {
            log.info(s"the cpu consumption is under violation")

            // triggering CpuViolation event
            applicationRef.ref ! ToNotificationActor(TriggerEvent(new MonitorEvent.CpuViolation()))
         }
      }

      case GetVmsWithConsumption() => sender ! getVmsWithConsumption()

      case GetCpuConsumption() => {
         log.info(s"send cpu consumption $cpuConsumption")
         sender ! cpuConsumption
      }

      case UpdateConfiguration(newLoad) => {
         cpuConsumption = newLoad
      }

      case msg => {
         applicationRef.ref ! msg
      }
   }

   context.system.scheduler.schedule(0 milliseconds,
      1 second,
      self,
      Tick())
}
