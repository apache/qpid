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

if [ "$JAVA_HOME" == "" ]; then
    echo "The JAVA_HOME environment variable is not defined";
    exit 0;
fi

if [ "$QPIDMC_HOME" == "" ]; then
    echo "The QPIDMC_HOME environment variable is not defined correctly";
    exit 0;
fi

# Test if we're running on cygwin.
cygwin=false
if [[ "$(uname -a | fgrep Cygwin)" != "" ]]; then
  cygwin=true
fi

if $cygwin; then
  QPIDMC_HOME=$(cygpath -w $QPIDMC_HOME)
fi

os=win32
ws=win32
arch=x86

##echo $os
##echo $ws
##echo $arch

## If this is to be run on different platform other than windows then following parameters should be passed
## qpidmc.sh <operating system> <windowing system> <platform achitecture>
## eg. qpidmc.sh linux motif x86
if [ $# -eq 3 ]; then
    os=$1
    ws=$2
    arch=$3
fi

if [ $os = "SunOS" ]; then
    os="solaris"
elif [ $os = "Linux" ]; then
    os="linux"
fi

"$JAVA_HOME/bin/java" -Xms40m -Xmx256m -Declipse.consoleLog=false -jar $QPIDMC_HOME/eclipse/startup.jar org.eclipse.core.launcher.Main -launcher $QPIDMC_HOME/eclipse/eclipse -name "Qpid Management Console" -showsplash 600 -configuration "file:$QPIDMC_HOME/configuration" -os $os -ws $ws -arch $arch
