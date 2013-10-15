package org.discovery.dvms.factory

import org.discovery.dvms.monitor.AbstractMonitorActor
import org.discovery.AkkaArc.util.NodeRef
import org.discovery.dvms.entropy.AbstractEntropyActor
import org.discovery.dvms.dvms.DvmsActor
import org.discovery.dvms.log.LoggingActor

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 3/25/13
 * Time: 4:56 PM
 * To change this template use File | Settings | File Templates.
 */


trait DvmsAbstractFactory {

   def createMonitorActor(nodeRef: NodeRef): Option[AbstractMonitorActor]

   def createDvmsActor(nodeRef: NodeRef): Option[DvmsActor]

   def createEntropyActor(nodeRef: NodeRef): Option[AbstractEntropyActor]

   def createLoggingActor(nodeRef: NodeRef): Option[LoggingActor]
}
