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

##LOGDIR=$QPID_HOME/logs
LOGDIR=../logs
date=`date +"%y%m%d%H%M%S"`
LOGFILE=$LOGDIR/perftest.log.$date

## create the log dir
if [ ! -d $LOGDIR ]; then
    mkdir $LOGDIR
fi

echo "********** Running the test **************"
echo "creating logfile $LOGFILE"
echo

./serviceRequestingClient.sh $@ 2>&1 | tee $LOGFILE

echo "********** End of test ******************"
echo