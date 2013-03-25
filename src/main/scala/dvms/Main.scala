package dvms

import akka.actor.{ActorSystem, Props}
import org.bbk.AkkaArc.util.{FakeNetworkLocation}
import scala.concurrent.duration._
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import java.util.IllegalFormatConversionException
import akka.util.Timeout
import org.bbk.AkkaArc.InitCommunicationWithHim

object Main extends App {

   override def main(args: Array[String]) {
      implicit val timeout = Timeout(1 seconds)
      implicit val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())


      try {


         val system = ActorSystem("DvmsSystem")


         val exampleApplication1 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(1))))
         val exampleApplication2 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(2))))
         val exampleApplication3 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(3))))
         val exampleApplication4 = system.actorOf(Props(new DvmsSupervisor(FakeNetworkLocation(4))))

         exampleApplication2 ! InitCommunicationWithHim(exampleApplication1)
         exampleApplication3 ! InitCommunicationWithHim(exampleApplication1)
         exampleApplication4 ! InitCommunicationWithHim(exampleApplication1)

         while (true) {
            Thread.sleep(1000)
         }



      }
      catch {
         case e:IllegalFormatConversionException => {
            e.printStackTrace()
         }
         case e:Exception => {
            e.printStackTrace()
         }
      }
   }
}