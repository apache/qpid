Qpid JCA Example - JBoss EAP 5.x, JBoss 5.x, 6.x

Overview
========
This document explains the steps required to configure and
deploy the Qpid JCA examples for both the JBoss EAP 5.x
environment as well as the JBoss 5.x/6.x community edition
(herafter simply referred to as JBoss). General information
can be found in the README.txt file.

Requirements
============
In order to deploy the Qpid JCA adapter, as well as the example application,
the JBOSS_HOME environemnt variable must be set. The environment variable
should point to the root of your JBoss installation.

In order to automatically deploy the Qpid JCA Adapter from the build system,
the QPID_JCA_HOME environment variable needs to be set.
The environment variable should point to the directory which contains the
Qpid JCA Adapter. If building from the source tree, by default this can be found at

QPID_ROOT/java/build/lib

If installing from RPM or other binary distribution, this can vary by platform.

If you do not want to use the build system to deploy the Qpid JCA adapter, you
can simply copy the qpid-ra-0.<version>.rar file to the

JBOSS_HOME/server/<server-name>/deploy

directory. By default, the example assumes the JBoss 'default' server profile will
be used. This can be modified in the build-jboss-properties.xml file.

The Qpid JCA examples assume the Apache Geronimo application server as the default
target platform. This can be modified in two ways:

1) Modify the build.xml file and change the target.platform property:

Example:

    <!-- Valid target platforms are currently geronimo, jboss, jboss7, glassfish -->
    <property name="target.platform" value="jboss"/>

2) Set the target.platform property via the command line:

Example:

ant -Dtarget.platform=jboss <target>

Note, if you choose the second method, all command line invocations must include
the target.platform. For the remainder of this document, we will assume the second
approach.

Prior to deploying any application component, the JBoss application server
should be started and available for requests.




Deploy and configure the Qpid JCA Adapter
==============================
Once the above requirements are satisfied the command

ant -Dtarget.platform= jboss deploy-rar

will copy the Qpid JCA adapter to JBoss server deploy directory.

To configure JCA resources in the JBoss environment, the *-ds.xml configuration file
is used. The command

ant -Dtarget.platform=jboss deploy-ds

will accomplish this task using the defaults provided. Any errors will be reported
in the

JBOSS_HOME/server/<server-name>/log/server.log

file or on the console.

Once the above commands execute successfully, the Qpid JCA adapter is deployed, configured
and ready for use.


Deploy the Qpid JCA Examples
============================
After the Qpid JCA adapter is deployed, executing the command

ant -Dtarget.platform=jboss deploy-ear

will attempt to deploy the Qpid JCA example EAR into the JBoss environment.
Once the above command executes successfully, the Qpid JCA example application is deployed,
configured and ready for use.

Note, if making modifications to either the Qpid JCA adapter or *-ds.xml configuration, the
EAR archive will need to be redeployed. This is a JBoss JCA issue and not due to the Qpid JCA
adapter.

The build-jboss-properties.xml file contains JBoss specific configuration options
and Ant targets that can be used to suit your development requirements.

The README.txt file in this directory provides the necessary instructions for using the Qpid JCA
adapter and example application which is consistent across all supported JEE platforms.





