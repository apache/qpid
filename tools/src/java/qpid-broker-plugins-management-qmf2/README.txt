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

The Qpid Java Broker by default uses either JMX or an HTTP Management GUI & REST API, however by building
a QMF2 Agent as a Java Broker plugin it's possible to provide better synergy between the C++ and Java Brokers
and allow the Java Broker to be controlled by the Qpid Command Line tools and also via the QMF2 REST API
and GUI thus providing a unified view across a mixture of C++ and Java Brokers.


************************************************ Building**************************************************

To build the Java Broker QMF2 plugin from source, do:

mvn clean package

This will build the main plugin jar, and additionally a tar.gz release assembly which contains all the necessary
dependencies to install the plugin in a broker installation.

To aid during development, you can optionally use the 'copy-broker' profile to extract the broker release archive
into the target/qpid-broker directory and copy the qmf2 broker plugin and dependencies into its lib dir:

mvn clean package -Pcopy-broker

You can then configure the extracted broker as described below.

*********************************************** Installing *************************************************

To install a release of the plugin, extract the release assembly and copy all of the files from the lib/ dir
into either the lib/ or lib/plugins/ directory of your extracted broker installation.

You can then configure the broker as described below.

********************************************** Configuring **************************************************

The Java Broker stores its main configuration in a JSON file (default: $QPID_WORK/config.json), which is typically
managed file via the Web Management GUI. It is IMPORTANT to ensure that the following:

{
    "name" : "qmf2Management",
    "type" : "MANAGEMENT-QMF2",
    "connectionURL" : "amqp://guest:guest@/?brokerlist='tcp://0.0.0.0:5672'"
  }

is added to the "plugins" list of $QPID_WORK/config.json or the Plugin will not be used. Each entry in the file
has an "id" property, but it is not necessary to specify one here as it will get automatically initialised the first
time the broker starts with the updated configuration.

The "connectionURL" property is particularly important. The Plugin connects to AMQP via the JMS Client so
"connectionURL" represents a valid Java ConnectionURL to the Broker so the username/password and any other
ConnectionURL configuration needs to be valid as for any other AMQP Connection to the Broker.


If the QMF GUI is to be used then either the -p option of QpidRestAPI.sh should be used to set the REST Server's
HTTP port to something other than 8080, or the brokers list of "ports" in $QPID_WORK/config.json should be modified from e.g.
{
    "id" :   <UUID>,
    "name" : "HTTP",
    "port" : "${qpid.jmx_port}"
    "protocols" : [ "HTTP" ]
  }

to

{
    "id" :   <UUID>,
    "name" : "HTTP",
    "port" : 9090,
    "protocols" : [ "HTTP" ]
  }

So that the HTTP ports don't conflict.


******************************************** Troubleshooting **********************************************

If all has gone well you should see

[Broker] MNG-1001 : QMF2 Management Startup
[Broker] MNG-1004 : QMF2 Management Ready

in the Broker log messages when you run qpid-server, if you don't see these then there is likely to be a problem.

1. Check your broker/plugin installation directory (e.g. $QPID_HOME/lib or $QPID_HOME/lib/plugins) now contains
at least the following additional jars:
qpid-qmf2-<version>.jar
qpid-broker-plugins-management-qmf2-<version>.jar
qpid-client-<version>.jar
geronimo-jms_1.1_spec-1.0.jar

2. If the jars mentioned above are present and correct in the plugins directory the most likely cause of failure
is incorrect configuration.

Ensure the steps outlined in the Config section have been taken to add the plugin configuration.

Additionally, in the top-level Broker config the "defaultVirtualHost" *MUST* be set to the name of the
default Virtual Host. If this property is not set or is set to a name that doesn't match the name of one of
the Virtual Hosts then the Plugin will not start correctly.


