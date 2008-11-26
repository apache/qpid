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

if [ -z "$JAVA" ]; then
    JAVA=java
fi

if [ -z "$QPIDMC_HOME" ]; then
    export QPIDMC_HOME=$(dirname $(dirname $(readlink -f $0)))
    export PATH=${PATH}:${QPIDMC_HOME}/bin
fi

# Test if we're running on cygwin.
cygwin=false
if [[ "$(uname -a | fgrep Cygwin)" != "" ]]; then
  cygwin=true
fi

if $cygwin; then
  QPIDMC_HOME=$(cygpath -w $QPIDMC_HOME)
fi


## If this is to be run on different platform other than windows then following parameters should be passed
## qpidmc.sh <windowing system>
## eg. qpidmc.sh motif

if [ $# -eq 1 ] ; then
    QPIDMC_WS=$1
else
    # If the WS is not set via QPIDMC_WS then query uname for the WS
    if [ -z "$QPIDMC_WS" ] ; then
       echo "Usage qpidmc.sh <windowing system>
       echo "Alternatively set QPIDMC_WS to the windowing system you wish to use
       exit 1
    fi
fi

# If the OS is not set via QPIDMC_OS then query uname for the OS
if [ -z "$QPIDMC_OS" ] ; then
    QPIDMC_OS=`uname | tr A-Z a-z`
else
    # Force OS to be lower case
    QPIDMC_OS=`echo $QPIDMC_OS | tr A-Z a-z`
fi

# If the ARCH is not set via QPIDMC_ARCH then query uname for the arch,
if [ -z "$QPIDMC_ARCH" ] ; then
    QPIDMC_ARCH=`uname -i`
fi

# Note that it sometimes returns i386 which needs to be changed to x86
if [ "$QPIDMC_ARCH" == "i386" ] ; then
    QPIDMC_ARCH="x86"
fi


"$JAVA" -Xms40m -Xmx256m -Declipse.consoleLog=true -jar $QPIDMC_HOME/eclipse/startup.jar org.eclipse.core.launcher.Main -name "Qpid Management Console" -showsplash 600 -configuration "file:$QPIDMC_HOME/configuration" -os $QPIDMC_OS -ws $QPIDMC_WS -arch $QPIDMC_ARCH
