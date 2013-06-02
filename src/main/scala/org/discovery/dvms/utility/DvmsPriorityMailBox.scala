package org.discovery.dvms.utility

import akka.actor.ActorSystem.Settings
import com.typesafe.config.Config
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.actor.PoisonPill


/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 4/16/13
 * Time: 3:52 PM
 * To change this template use File | Settings | File Templates.
 */
// We inherit, in this case, from UnboundedPriorityMailbox
// and seed it with the priority generator
class DvmsPriorityMailBox(settings: Settings, config: Config)
  extends UnboundedPriorityMailbox(
     // Create a new PriorityGenerator, lower prio means more important
     PriorityGenerator {
        // 'highpriority messages should be treated first if possible
        case 'highpriority ⇒ 0

        // 'lowpriority messages should be treated last if possible
        case 'lowpriority ⇒ 2

        // PoisonPill when no other left
        case PoisonPill ⇒ 3

        // We default to 1, which is in between high and low
        case otherwise ⇒ 1
     })