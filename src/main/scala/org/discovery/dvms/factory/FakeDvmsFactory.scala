package org.discovery.dvms.factory

import org.discovery.dvms.monitor.{FakeMonitorActor, AbstractMonitorActor}
import org.discovery.dvms.entropy.{FakeEntropyActor, AbstractEntropyActor}
import org.discovery.AkkaArc.util.NodeRef
import org.discovery.dvms.dvms.DvmsActor


/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 3:53 PM
 * To change this template use File | Settings | File Templates.
 */

object FakeDvmsFactory extends DvmsAbstractFactory {

   def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor] = {
      Some(new FakeMonitorActor(nodeRef))
   }

   def createDvmsActor(nodeRef: NodeRef): Option[DvmsActor] = {
      Some(new DvmsActor(nodeRef))
   }

   def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor] = {
      Some(new FakeEntropyActor(nodeRef))
   }
}
