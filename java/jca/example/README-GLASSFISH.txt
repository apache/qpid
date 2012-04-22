Qpid JCA Example - Glassfish 3.x

Overview
========
This document explains the steps required to configure and
deploy the Qpid JCA examples for the Glassfish 3.x environment.

The Glassfish environment provides two methods for deploying
JEE application components: the web-based administration console
and the asadmin command line utility. This document only explains
the command line utility. Please see the Glassfish 3.x
documentation for the web-based console approach.

Requirements
============
In order to deploy the Qpid JCA adapter, as well as the example application,
the GLASSFISH_HOME environemnt variable must be set. The environment variable
should point to the root of your Glassfish installation.

In order to automatically deploy the Qpid JCA Adapter from the build system,
the QPID_JCA_HOME environment variable needs to be set.
The environment variable should point to the directory which contains the
Qpid JCA Adapter. If building from the source tree, by default this can be found at

QPID_ROOT/java/build/lib

If installing from RPM or other binary distribution, this can vary by OS platform.

The Qpid JCA examples assume the Apache Geronimo application server as the default
target platform. This can be modified in two ways:

1) Modify the build.xml file and change the target.platform property:

Example:

    <!-- Valid target platforms are currently geronimo, jboss, jboss7, glassfish -->
    <property name="target.platform" value="glassfish"/>

2) Set the target.platform property via the command line:

Example:

ant -Dtarget.platform=glassfish <target>

Note, if you choose the second method, all command line invocations must include
the target.platform variable. For the remainder of this document, we will assume
the second approach.

Prior to deploying any application component, the Glassfish application server
should be started and available for requests.


Deploy and configure the Qpid JCA Adapter
==============================
Once the above requirements are satisfied the command

ant -Dtarget.platform=glassfish deploy-rar

will attempt to invoke the asadmin utility to deploy and configure the
Qpid JCA adapter. Once this step completes succesfully the Qpid JCA adapter
is deployed, configured and ready for use.


Deploy the Qpid JCA Examples
============================
After the Qpid JCA adapter is deployed, executing the command

ant -Dtarget.platform=glassfish deploy-ear

will attempt to deploy the Qpid JCA example EAR into the Glassfish environment.
Once the above command executes successfully, the Qpid JCA example application
is deployed, configured and ready for use.

The build-glassfish-properties.xml file contains Glassfish specific configuration options
and Ant targets that can be used to suit your development requirements. Executing

ant -Dtarget.platform=glassfish -p

will list the appropriate targets and provide a simple description for each.

The README.txt file in this directory provides the necessary instructions for using
the Qpid JCA adapter and example application which is consistent across all supported
JEE platforms.

