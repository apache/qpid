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

# Set classpath to include Qpid jar with all required jars in manifest
QPID_LIBS=$QPID_TOOLS/lib/qpid-incubating.jar

die() {
  if [[ $1 = -usage ]]; then
    shift
    usage=true
  else
    usage=false
  fi
  echo "$@"
  $usage && echo
  $usage && usage
  exit 1
}

cygwin=false
if [[ "$(uname -a | fgrep Cygwin)" != "" ]]; then
  cygwin=true
fi

if $cygwin; then
  QPID_TOOLS=$(cygpath -w $QPID_TOOLS)
fi

# Set other variables used by the qpid-run script before calling
export JAVA=java \
       JAVA_VM=-server \
       JAVA_OPTS=-Dlog4j.configuration=file:$QPID_TOOLS/etc/mstool-log4j.xml \
       QPID_CLASSPATH=$QPID_LIBS

if [ -z "$QPID_TOOLS" ]; then
  die "QPID_TOOLS be set"
fi

. qpid-run org.apache.qpid.tools.messagestore.MessageStoreTool "$@"
