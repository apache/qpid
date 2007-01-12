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

if [[ $# < 3 ]] ; then
 echo "usage: ./volumetestServiceRequestingClient.sh <brokerdetails> <logfile full path> <number of messages> [<message size 4096b default>]"
 exit 1
fi

thehosts=$1
logname=$2
messageCount=$3
messageSize=$4

## create the log dir
if [ ! -d $QPID_HOME/logs ]; then
    echo "hello"
    mkdir $QPID_HOME/logs
fi

echo "********** Running the test **************"
echo "..."
echo
./serviceRequestingClient.sh $thehosts $messageCount $messageSize
## check for the status of test execution
if [ $? -ne 0 ]; then
    exit 1;
fi

LOGFILE=$logname
FILE_SENT=$QPID_HOME/logs/sentMessageItedifiers.txt
FILE_RECEIVED=$QPID_HOME/logs/receivedMessageItedifiers.txt

echo
## check if the logfile is present
if [ ! -f $LOGFILE ]; then
    echo "logfile $LOGFILE does not exist"
    echo "please check the logfile path in log4j config file for serviceRequestingClient"
    exit 1;
fi

## delete the old files
if [ -f $FILE_SENT ]; then
    rm $FILE_SENT
fi

if [ -f $FILE_RECEIVED ]; then
    rm $FILE_RECEIVED
fi

##echo "logfile=$LOGFILE"
echo "************* Analyzing the log *************"
echo "..."

n=`wc -l < $LOGFILE`
i=1
while [ "$i" -le "$n" ]
do
    ## get the sent and received message identifiers
    line=`cat $LOGFILE | head -$i | tail -1`
    `echo $line | grep "Sent Message Identifier" |cut -d" " -f4 >> $FILE_SENT`
    `echo $line | grep "Received Message Identifier" |cut -d" " -f4 >> $FILE_RECEIVED`
    
    ##show if any exception
    line=`echo $line | grep "Exception"`
    if [ `echo $line | wc -w` -gt 0  ]; then
        echo "Exception occured:"
        echo $line
    fi
    
    i=`expr $i + 1`
done



## get the message identifiers, which are sent but not received back
notReceivedMessageCount=`comm -23 $FILE_SENT $FILE_RECEIVED | wc -l`

echo
echo "**** Result ****"
messagesSent=`cat $FILE_SENT | wc -l`
echo "$messagesSent messages were sent"

if [ $notReceivedMessageCount -gt 0 ];
then
    echo "Total $notReceivedMessageCount messages not received back"
    echo "please check the log $LOGFILE for errors";
else
    echo "$messagesSent messages were sent and received back successfully"
fi

## delete the temp files created
if [ -f $FILE_SENT ]; then
    rm $FILE_SENT
fi

if [ -f $FILE_RECEIVED ]; then
    rm $FILE_RECEIVED
fi
