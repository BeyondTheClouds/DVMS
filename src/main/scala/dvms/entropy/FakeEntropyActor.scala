package dvms.entropy

import org.bbk.AkkaArc.util.NodeRef
import concurrent.{Future, Await}
import dvms.dvms.{AskTimeoutDetected, ToDvmsActor, ToEntropyActor, ToMonitorActor}
import dvms.monitor.{UpdateConfiguration, GetCpuLoad}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.{AskTimeoutException, ask}

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


      var isCorrect:Boolean = false

      try {

         val listOfLoad = Await.result(Future.sequence(nodes.map({n => (n.ref ? ToMonitorActor(GetCpuLoad()))})).mapTo[List[Double]], 1 second)
         nodeLoad = listOfLoad.foldLeft(0.0)((a,b) => a+b)

         log.info(s"computed load: ${nodeLoad/nodes.size}")

         if (nodeLoad/nodes.size <= 100) {

            nodes.foreach(n => {
               n.ref ! ToMonitorActor(UpdateConfiguration(nodeLoad/nodes.size))
            })

            isCorrect = true

         } else {

            isCorrect = false
         }
      } catch {
         case e:AskTimeoutException => {
            isCorrect = false
            applicationRef.ref ! ToDvmsActor(AskTimeoutDetected(e))
         }
         case e:Exception =>
      }

      return isCorrect
   }
}
