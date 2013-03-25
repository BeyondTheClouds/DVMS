package dvms.monitor

import org.bbk.AkkaArc.util.NodeRef
import akka.actor.{ActorLogging, Actor}
import akka.util.Timeout
import concurrent.ExecutionContext
import java.util.concurrent.Executors
import util.Random
import org.bbk.AkkaArc.notification.{SimpleEvent, TriggerEvent, ToNotificationActor}
import scala.concurrent.duration._

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 1:12 PM
 * To change this template use File | Settings | File Templates.
 */

class FakeMonitorActor(applicationRef:NodeRef) extends AbstractMonitorActor(applicationRef) {

  def uploadCpuLoad():Double = {
    val cpuLoadChange = random.nextDouble()*2*delta - delta

    (cpuLoad+cpuLoadChange) match {
      case n:Double if (n<0) => cpuLoad = 0
      case n:Double => cpuLoad = n
    }

    cpuLoad
  }
}