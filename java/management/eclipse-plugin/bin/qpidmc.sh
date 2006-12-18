#!/bin/bash
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

"$JAVA_HOME/bin/java" -Xms40m -Xmx256m -Declipse.consoleLog=true -jar $QPIDMC_HOME/startup.jar org.eclipse.core.launcher.Main -launcher $QPIDMC_HOME/qpidmc.exe -name "Qpid Management Console" -showsplash 600 -data $QPIDMC_HOME/data -configuration "file:$QPIDMC_HOME/configuration"