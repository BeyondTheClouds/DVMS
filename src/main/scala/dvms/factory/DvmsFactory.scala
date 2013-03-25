package dvms.factory

import dvms.monitor.{FakeMonitorActor, AbstractMonitorActor}
import dvms.entropy.{FakeEntropyActor, AbstractEntropyActor}
import org.bbk.AkkaArc.util.NodeRef


/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 3:53 PM
 * To change this template use File | Settings | File Templates.
 */

object DvmsFactory extends DvmsAbstractFactory {

  def createMonitorActor(nodeRef:NodeRef):Option[AbstractMonitorActor] = {
    Some(new FakeMonitorActor(nodeRef))
  }

  def createEntropyActor(nodeRef:NodeRef):Option[AbstractEntropyActor] = {
    Some(new FakeEntropyActor(nodeRef))
  }
}
