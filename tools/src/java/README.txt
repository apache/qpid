/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

This is a Java JMS implementation of the QMF2 API specified at
https://cwiki.apache.org/qpid/qmfv2-api-proposal.html

The Qpid Java jar needs to be on your classpath - I tend to use qpid-all.jar but client only jars should be OK too.

QMF2 support is now available for the Qpid Java Broker see README-Java-Broker.txt for details.



*********************************************** Important!! ***********************************************
*  If your version of Qpid is older than 0.12 the QMF2 API won't work unless your setup is as follows:    *
*********************************************** Important!! ***********************************************

For those who are running with Qpid 0.12 or above the patch described below isn't necessary.
The default "api" ant target in build.xml builds everything except the patch, which is the preferred
approach for later Qpid versions, though using the patched version of the older AMQMessageDelegate_0_10.java
still works with Qpid 0.12 (but not with later Qpid versions).


To be clear, if you are using Qpid Java jars 0.12 or above you do not need to use the patch described below
even if you are talking to an earlier broker, however do note that if you are talking to a broker < Qpid 0.10
you need to set "--mgmt-qmf2 yes" when you start up qpidd if you want to get QMF2 Events and heartbeats pushed.
This is particularly important to note if you are using the Qpid GUI, as in default mode its updates are
triggered by the QMF2 heartbeats. If "--mgmt-qmf2 yes" isn't set on a 0.8 broker you'll see "Broker Disconnected"
flash briefly every 30 seconds or so as timeouts occur. Creating a QMF Console Connecton in the GUI with
"Disable Events" selected uses a timed poll rather than a heartbeat so it may be better to do that for cases
where access to the broker configuration is not available.

***********************************************************************************************************

Note 1: This uses QMF2 so requires that the "--mgmt-qmf2 yes" option is applied to the broker (this is
        the default from Qpid 0.10 onwards)
Note 2: In order to use QMF2 the app-id field needs to be set. There appears to be no way to set the AMQP
        0-10 specific app-id field on a message which the broker's QMFv2 implementation currently requires.

Gordon Sim has put together a patch for org.apache.qpid.client.message.AMQMessageDelegate_0_10
Found in client/src/main/java/org/apache/qpid/client/message/AMQMessageDelegate_0_10.java
 
public void setStringProperty(String propertyName, String value) throws JMSException
{
       checkPropertyName(propertyName);
       checkWritableProperties();
       setApplicationHeader(propertyName, value);

       if ("x-amqp-0-10.app-id".equals(propertyName))
       {
           _messageProps.setAppId(value.getBytes());
       }
}
 
The jira "https://issues.apache.org/jira/browse/QPID-3302." covers this.


This has been fixed in Qpid 0.12, but I've included a patched version of AMQMessageDelegate_0_10.java
in the build directory so that people using earlier versions can get up and running (the QMF2 library
was initially developed using Qpid 0.10).


The "api-patched" ant target in build.xml creates a qpid-client-patch.jar in addition to the qmf2.jar and qmf2test.jar

It is assumed that the qpid-clientxxx.jar is already on your CLASSPATH so one would do:

CLASSPATH=../../build/lib/qpid-client-patch.jar:$CLASSPATH:../../build/lib/qmf2.jar:../../build/lib/qmf2test.jar

to put the patched AMQMessageDelegate_0_10 ahead of the unpatched one. This is already done for the scripts that
call the various test and tool classes.






