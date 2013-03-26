package dvms.entropy

import org.bbk.AkkaArc.util.NodeRef
import concurrent.Await
import dvms.dvms.ToMonitorActor
import dvms.monitor.{UpdateConfiguration, GetCpuLoad}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:11 PM
 * To change this template use File | Settings | File Templates.
 */




class FakeEntropyActor(applicationRef:NodeRef) extends AbstractEntropyActor(applicationRef) {

  def computeAndApplyReconfigurationPlan(nodes:List[NodeRef]):Boolean = {
    var nodeLoad:Double = 0.0
    log.info("computing reconfiguration plan")
    nodes.foreach(n => {
      //            if(n.location isEqualTo applicationRef.location) {
      val nodeCpuLoad = Await.result(n.ref ? ToMonitorActor(GetCpuLoad()), 1 second).asInstanceOf[Double]
      nodeLoad += nodeCpuLoad
      log.info(s"get CPU load of $n: $nodeCpuLoad%")
      //            }
    })

    log.info(s"computed load: ${nodeLoad/nodes.size}")

    if (nodeLoad/nodes.size <= 100) {

      nodes.foreach(n => {
        n.ref ! ToMonitorActor(UpdateConfiguration(nodeLoad/nodes.size))
      })

      true
    } else {
      false
    }
  }
}
