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

********************************************** Introduction ***********************************************

This directory provides a set of Java and JavaScript based tools that allow interaction with the Qpid brokers.
The tools are based on QMF2 (Qpid Management Framework v2) and work with the C++ broker by default. In order
to enable QMF2 support in the Java Broker you must compile the QMF plugin (see the directory
qpid-broker-plugins-management-qmf2 and contained README.txt).

In order to build the Java QMF2 API, the Tools and the Java Broker QMF2 plugin simply do:

mvn clean package

This will create the jar files for the various modules, and additionally create packaged release archives for
the combined tools package and the broker plugin which can be extracted for installation and use.


There is fairly comprehensive JavaDoc available, which you can generate in a couple of ways:

mvn javadoc:aggregate  - Builds the Javadoc for all the modules combined, output located at:
target/site/apidocs/index.html

mvn javadoc:javadoc    - Builds the Javadoc for each module in turn individually, output located at:
<module>/target/site/apidocs/index.html


N.B. At the moment the QMF2 API and tools use the "traditional" Qpid AMQP 0.10 JMS API. The intention is that
over time this will migrate to AMQP 1.0 and move from being QMF2 based to using the AMQP 1.0 Management Spec.
However there is no concrete schedule for this migration at this time.

************************************************* The API *************************************************

The tools are build on a Java JMS implementation of the QMF2 API specified at
https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal

Once built as described earlier, the API jar is included in the overall tools bundle described below, and
additionally the API jar itself file will be placed in:
qpid-qmf2/target

There is fairly comprehensive JavaDoc available, see earlier for build instructions.

Looking at the source code for the tools (see "The Tools" below) might be a quicker way to get started.

The source code for the Java QMF2 API can be found under:
qpid-qmf2/src/main/java/org/apache/qpid/qmf2/console
qpid-qmf2/src/main/java/org/apache/qpid/qmf2/agent
qpid-qmf2/src/main/java/org/apache/qpid/qmf2/common

console: contains the classes for the QMF2 "console", which is what most of the tools make use of
agent: contains the classes for the QMF2 "agent", which it what exposes management services, this is
       what the Java Broker plugin uses to "externalise" its management model as QMF.
common: contains classes common to both the console and the agent.

************************************************ The Tools ************************************************

A number of Java based tools are provided, and additionally a web based GUI with underlying REST api which
are described later, utilising the core components from the API outlined above.

Once built as described earlier, the tools jar is included in the overall tools release tar.gz placed in:
qpid-qmf2-tools/target

There are executable shell scripts included in the tools bundle that should allow the tools to be run fairly
easily. To use them, extract the tar.gz release to your preferred installation location, and open the
included bin/ directory.

The source code for the Java QMF2 Tools can be found under:
qpid-qmf2-tools/src/main/java/org/apache/qpid/qmf2/tools


For more details of the available tools, see the README.txt in the qpid-qmf2-tools folder, which is also
included in the tar.gz release.

************************************************* The GUI *************************************************

Included in the tools package, there is a fairly comprehensive Web based GUI available for Qpid that works
with the C++ Broker and also the Java Broker if the QMF management plugin has been installed (see the
qpid-broker-plugins-management-qmf2 directory and contained README.txt).

The GUI is in the form of a pure client side "single page" Web App written in JavaScript that uses the
QpidRestAPI to proxy the QMF API, and also serve up the GUI.

There is comprehensive JavaDoc for the QpidRestAPI (see earlier for build instructions), where
the most useful classes to look at are:
QpidRestAPI: This describes the various command line options available.
QpidServer: This provides documentation for the actual REST API itself, in effect the REST mapping for QMF

QpidRestAPI provides a fairly complete REST mapping for QMF, it was primarily written as the back-end to
the GUI, but there's no reason why it couldn't be used in its own right.


For more details of the GUI, see the README.txt in the qpid-qmf2-tools folder, which is also included
in the tar.gz release.
