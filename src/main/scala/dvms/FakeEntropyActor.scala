package dvms

import org.bbk.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.{Await, ExecutionContext}
import java.util.concurrent.Executors
import scala.concurrent.duration._
import akka.pattern.ask

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:11 PM
 * To change this template use File | Settings | File Templates.
 */


case class EntropyComputeReconfigurePlan(nodes:List[NodeRef])

class FakeEntropyActor(applicationRef:NodeRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(2 seconds)
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  override def receive = {
    case EntropyComputeReconfigurePlan(nodes) =>  {
      var nodeLoad:Double = 0.0
      log.info("computing reconfiguration plan")
      nodes.foreach(n => {
        //            if(n.location isEqualTo applicationRef.location) {
        val nodeCpuLoad = Await.result(n.ref ? ToMonitorActor(GetCpuLoad()), 1 second).asInstanceOf[Double]
        nodeLoad += nodeCpuLoad
        log.info(s"get CPU load of $n: $nodeCpuLoad%")
        //            }
      })

      if(nodeLoad/nodes.size <= 100 && nodes.size >= 4) {
        nodes.foreach(n => {
          n.ref ! ToMonitorActor(UpdateConfiguration(nodeLoad/nodes.size))
        })
        sender ! true
      } else {
        sender ! false
      }
    }

    case msg => {
      applicationRef.ref ! msg
    }
  }
}
