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

class CpuViolation() extends SimpleEvent("cpuViolation")
case class UpdateConfiguration(newLoad:Double)
case class GetCpuLoad()

abstract class AbstractMonitorActor(applicationRef:NodeRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(2 seconds)
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  case class Tick()

  var cpuLoad:Double = 50
  val delta:Double = 17
  val seed:Long = applicationRef.location.getId
  val random:Random = new Random(seed)

  def uploadCpuLoad():Double

  override def receive = {
    case Tick() => {

      uploadCpuLoad()

      log.info(s"the new load is : $cpuLoad")

      if (cpuLoad > 100) {
        log.info(s"the cpu load is under violation")

        // triggering CpuViolation event
        applicationRef.ref ! ToNotificationActor(TriggerEvent(new CpuViolation()))
      }
    }

    case GetCpuLoad() => {
      log.info(s"send cpu load $cpuLoad")
      sender ! cpuLoad
    }

    case UpdateConfiguration(newLoad) => {
      cpuLoad = newLoad
    }

    case msg => {
      //         log.warning(s"FakeMonitorActor: received unknown message <$msg>")
      applicationRef.ref ! msg
    }
  }

  context.system.scheduler.schedule(0 milliseconds,
    1 second,
    self,
    Tick())
}
