package org.discovery.dvms.utility

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

import akka.actor.ActorSystem.Settings
import com.typesafe.config.Config
import akka.dispatch.{PriorityGenerator, UnboundedPriorityMailbox}
import akka.actor.PoisonPill

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