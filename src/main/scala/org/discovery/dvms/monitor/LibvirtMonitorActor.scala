package org.discovery.dvms.monitor

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

import org.discovery.peeractor.util.NodeRef
import org.discovery.driver.{LibvirtDriver, LibvirtG5kDriver}
import org.discovery.model.IDriver
import scala.collection.JavaConversions._
import org.discovery.dvms.configuration._
import org.discovery.dvms.log.LoggingProtocol.CurrentLoadIs
import org.discovery.dvms.dvms.DvmsModel.PhysicalNode
import org.discovery.dvms.dvms.DvmsModel.ComputerSpecification
import org.discovery.dvms.dvms.DvmsModel.VirtualMachine
import org.discovery.model.network.CpuConsumptions
import MonitorProtocol._
import java.util.UUID
import org.discovery.peeractor.notification.NotificationActorProtocol.TriggerEvent

object LibvirtMonitorDriver {
  val driver: IDriver = DvmsConfiguration.IS_G5K_MODE match {

    case true =>
      new LibvirtG5kDriver("configuration/driver.cfg")
      new LibvirtDriver("configuration/driver.cfg")
    case false =>
      new LibvirtDriver("configuration/driver.cfg")
  }

  driver.connect()

}

class LibvirtMonitorActor(applicationRef: NodeRef) extends AbstractMonitorActor(applicationRef) {

  var lastCpuConsumptions = LibvirtMonitorDriver.driver.getCpuConsumptions()
  var listOfVms = LibvirtMonitorDriver.driver.getRunningVms

  updateVmsData()

  var lastCheckingUUID: Option[UUID] = None
  var lastViolationReportedUUID: Option[UUID] = None

  def getVmsWithConsumption(): PhysicalNode = {

    val result = PhysicalNode(applicationRef, listOfVms.par.map(vm => {

      val vmName = vm.getName
      val listOfConsumptions = lastCpuConsumptions.get(vmName)
      val userCpuConsumption = listOfConsumptions.get(CpuConsumptions.US)
      val stealCpuConsumption = listOfConsumptions.get(CpuConsumptions.ST)


      VirtualMachine(
        vmName,
        userCpuConsumption + stealCpuConsumption,
        ComputerSpecification(
          VirtualMachineConfiguration.getNumberOfCpus,
          VirtualMachineConfiguration.getRamCapacity,
          VirtualMachineConfiguration.getCpuCoreCapacity
        )
      )
    }).toList,
      LibvirtMonitorDriver.driver.getMigrationUrl()
      ,
      ComputerSpecification(
        HardwareConfiguration.getNumberOfCpus,
        HardwareConfiguration.getRamCapacity,
        HardwareConfiguration.getCpuCapacity
      )
    )

    result
  }

  def uploadCpuConsumption(): Double = {
    /* Send the CPU consumption to loggingActor */
    applicationRef.ref ! CurrentLoadIs(ExperimentConfiguration.getCurrentTime(), cpuConsumption)
    cpuConsumptionValue
  }

  def uploadCpuConsumption(tickNumber: Int): Double = uploadCpuConsumption()

  var tickCount: Int = 0;
  var cpuConsumptionValue: Double = 0.0

  override def receive = {

    case UpdateCpuConsumptions(consumptions) =>
      lastCpuConsumptions = consumptions

    case Tick() => {

      tickCount += 1

      val tickNumber = tickCount

      cpuConsumption = uploadCpuConsumption(tickNumber)

      log.info(s"the new consumption is : $cpuConsumption")

      if (cpuConsumption > G5kNodes.getCurrentNodeInstance().getCPUCapacity) {

        (lastCheckingUUID, lastViolationReportedUUID) match {
          case (Some(checkingUUID), Some(violationReportedUUID))
            if (checkingUUID != violationReportedUUID) =>
            log.info(s"the cpu consumption is under violation")

            // triggering CpuViolation event
            applicationRef.ref ! TriggerEvent(new MonitorEvent.CpuViolation())
          case _ =>
        }

        lastViolationReportedUUID = lastCheckingUUID
      }
    }

    case msg => super.receive(msg)
  }

  def updateVmsData(): Unit = {
    try {

      val driver: IDriver = DvmsConfiguration.IS_G5K_MODE match {

        case true =>
          new LibvirtG5kDriver("configuration/driver.cfg")
          new LibvirtDriver("configuration/driver.cfg")
        case false =>
          new LibvirtDriver("configuration/driver.cfg")
      }

      driver.connect()

      val newConsumptions = driver.getCpuConsumptions
      val newListOfVms = driver.getRunningVms

      log.info(s"consumptions: $newConsumptions")
      log.info(s"vms: {${

        newListOfVms.foldLeft("")((a, b) => b.getName + "," + a)

      }}")

      lastCpuConsumptions = newConsumptions.filter(consumption => consumption._2.size() >= 8)
      listOfVms = newListOfVms.filter(vm => lastCpuConsumptions.contains(vm.getName))
      lastCheckingUUID = Some(UUID.randomUUID())

      val listOfConsumptions: List[Double] = lastCpuConsumptions.keySet.map(key =>
        lastCpuConsumptions.get(key).get(CpuConsumptions.US) + lastCpuConsumptions.get(key).get(CpuConsumptions.ST)
      ).toList

      cpuConsumptionValue = listOfConsumptions.foldLeft(0.0)((a, b) => a + b)
      log.info(s"consumptions_value: $cpuConsumptionValue")

    } catch {
      case e: Throwable =>
        e.printStackTrace()
    }

    Thread.sleep(500)
  }

  // Yes, there a thread :(
  // (following the KISS principle: "Keep It Simple Stupid!")
  val updatingThread = new Thread(new Runnable {
    def run() {
      while (true) {
        updateVmsData()
      }
    }
  })
  updatingThread.start()

}
