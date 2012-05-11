Qpid JCA Example - JBoss AS 7

Overview
========
This document explains the steps required to configure and
deploy the Qpid JCA examples for the JBoss AS 7 environment.
General information about the example can be found in the
README-EXAMPLE.txt file.

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

JBOSS_HOME/<server-profile>/deployments

directory. By default, the example assumes the JBoss 'standalone' server profile will
be used. This can be modified in the build-jboss7-properties.xml file.

The Qpid JCA examples assume the Apache Geronimo application server as the default
target platform. This can be modified in two ways:

1) Modify the build.xml file and change the target.platform property:

Example:

    <!-- Valid target platforms are currently geronimo, jboss, jboss7, glassfish -->
    <property name="target.platform" value="jboss7"/>

2) Set the target.platform property via the command line:

Example:

ant -Dtarget.platform=jboss7 <target>

Note, if you choose the second method, all command line invocations must include
the target.platform. For the remainder of this document, we will assume the second
approach.

As opposed to earlier versions of JBoss AS, JBoss AS 7 uses a different approach for
configuring JCA resources. JBoss AS 7 provides support for different server profiles
each configured through an XML based configuration file. In order to configure the
Qpid JCA adapter for the JBoss AS 7 environment, a complete XML configuration has been
provided in the conf/ directory as conf/qpid-standalone.xml. In order to configure this
file execute the following command:

ant -Dtarget.platform=jboss7 generate

The result of this command will produce a file build/gen/qpid-standalone.xml file. To
deploy this file copy the file to:

JBOSS_HOME/standalone/configuration

directory.

Prior to deploying any application component, the JBoss application server
should be started and available for requests. In order to use the qpid-standalone.xml
file generated in the previous step, when starting JBoss AS 7 invoke the following:

./standalone.sh -c qpid-standalone.xml


Note, the above method completely replaces the default messaging provider in JBoss AS 7.
Currently there is no way to have two separate messaging providers deployed within the same
server. It assumed that this will be addressed in a later version of JBoss AS 7.


Deploy the Qpid JCA Adapter
==============================
Once the above requirements are satisfied the command

ant -Dtarget.platform=jboss7 deploy-rar

will copy the Qpid JCA adapter to JBoss AS 7 server deploy directory.

Once the above commands execute successfully, the Qpid JCA adapter is deployed, configured
and ready for use.


Deploy the Qpid JCA Examples
============================
After the Qpid JCA adapter is deployed, executing the command

ant -Dtarget.platform=jboss7 deploy-ear

will attempt to deploy the Qpid JCA example EAR into the JBoss AS 7 environment.
Once the above command executes successfully, the Qpid JCA example application is
deployed, configured and ready for use.

Note, currently there is an issue with 'hot-deployment' in the JBoss AS 7 environment.
If you need to re-deploy the EAR file, restarting JBoss AS 7 is required.

The build-jboss7-properties.xml file contains JBoss AS 7 specific configuration options
and Apache Ant targets that can be used to suit your development requirements.

The README.txt file in this directory provides the necessary instructions for using the Qpid JCA
adapter and example application which is consistent across all supported JEE platforms.






