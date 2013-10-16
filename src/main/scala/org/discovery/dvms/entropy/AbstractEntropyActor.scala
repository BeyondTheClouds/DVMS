package org.discovery.dvms.entropy

import org.discovery.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import scala.concurrent.duration._
import org.discovery.dvms.entropy.EntropyProtocol._

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 3:32 PM
 * To change this template use File | Settings | File Templates.
 */



abstract class AbstractEntropyActor(applicationRef: NodeRef) extends Actor with ActorLogging {

   implicit val timeout = Timeout(2 seconds)
   implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())


   def computeAndApplyReconfigurationPlan(nodes: List[NodeRef]): Boolean

   override def receive = {
      case EntropyComputeReconfigurePlan(nodes) => {
         sender ! computeAndApplyReconfigurationPlan(nodes)
      }

      case msg => {
         applicationRef.ref ! msg
      }
   }
}
