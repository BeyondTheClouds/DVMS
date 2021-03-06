package org.discovery.dvms.configuration

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


import java.util.Properties
import java.io.FileInputStream


object DvmsConfiguration {

   val DVMS_CONFIG_NAME: String = "configuration/dvms.cfg"

   val properties: Properties = new Properties();

   try {
      val in: FileInputStream = new FileInputStream(DVMS_CONFIG_NAME)
      properties.load(in)
      in.close
   }

   val FACTORY_NAME: String = getPropertyOrDefault("dvms.factory", "fake");
   val OVERLAY: String      = getPropertyOrDefault("overlay", "chord");
   var IS_G5K_MODE: Boolean = getBooleanPropertyOrDefault("g5k.mode.enabled", false);

  val IS_MONITORING_ACTIVATED: Boolean = getBooleanPropertyOrDefault("vivaldi.monitoring.activated", true);
  val MONITORING_URL: String = getPropertyOrDefault("vivaldi.monitoring.url", "http://localhost:8000/");


   implicit def stringToBoolean(value: String): Boolean = value.toBoolean

   def getPropertyOrDefault(key: String, defaultValue: String): String = properties.getProperty(key) match {
      case null => defaultValue
      case value:String => value
   }

   def getBooleanPropertyOrDefault(key: String, defaultValue: Boolean): Boolean = properties.getProperty(key) match {
      case null => defaultValue
      case value:String => value.toBoolean
   }

   def getIntPropertyOrDefault(key: String, defaultValue: Int): Int = properties.getProperty(key) match {
      case null => defaultValue
      case value:String => value.toInt
   }
}
