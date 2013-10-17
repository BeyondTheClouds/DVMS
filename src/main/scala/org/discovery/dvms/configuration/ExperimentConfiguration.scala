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
     
         
 object ExperimentConfiguration {

    var experimentStartTime: Double = 0.0
    var migrationCount: Int = 0

    def startExperiment() {

       val nowAsMilliseconds: Double = System.currentTimeMillis()
       val nowAsSeconds: Double = nowAsMilliseconds/1000

       experimentStartTime = nowAsSeconds
    }

    def getCurrentTime(): Double = {

       val nowAsMilliseconds: Double = System.currentTimeMillis()
       nowAsMilliseconds/1000 - experimentStartTime
    }

    def incrementMigrationCount(newMigrationCount: Int) {
       migrationCount += newMigrationCount
    }

    def getMigrationCount():Int = {
       migrationCount
    }

}
