package dvms.factory

import dvms.monitor.{AbstractMonitorActor}
import org.bbk.AkkaArc.util.NodeRef
import dvms.entropy.{AbstractEntropyActor}
import dvms.dvms.DvmsActor

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 4:56 PM
 * To change this template use File | Settings | File Templates.
 */


trait DvmsAbstractFactory {

  def createMonitorActor(nodeRef:NodeRef):Option[AbstractMonitorActor]
  def createDvmsActor(nodeRef:NodeRef):Option[DvmsActor]
  def createEntropyActor(nodeRef:NodeRef):Option[AbstractEntropyActor]
}
