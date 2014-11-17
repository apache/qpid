#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

This folder contains test definitions and chart definitions for the tests measuring throughput on scaling participants.
They could be useful for testing performance in HA scenarios.

The tests can be executed with:

perfetsts> java -cp './target/*:./target/dependency/*:./etc/' -Dqpid.amqp.version=0-91 -Dqpid.disttest.duration=50000 \
    -Dqpid.dest_syntax=BURL org.apache.qpid.disttest.ControllerRunner jndi-config=./etc/perftests-jndi.properties \
    test-config=./etc/ha/testdefs/ScalingParticipants.js distributed=false writeToDb=false

Graphs can be built with:

perfetsts> java -cp './visualisation-jfc/target/dependency/*:./visualisation-jfc/target/*' -Djava.awt.headless=true \
          -Dlog4j.configuration=file://./etc/log4j.properties -DcsvCurrentDir=. -DcsvBaselineDir=. -DbaselineName=na \
          org.apache.qpid.disttest.charting.ChartingUtil chart-defs=./etc/ha/1001-ScalingParticipants.chartdef
