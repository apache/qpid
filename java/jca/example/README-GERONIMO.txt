Qpid JCA Example - Apache Geronimo 2.x

Overview
========
This document explains the steps required to configure and
deploy the Qpid JCA examples for the Apache Geronimo environment.
General information can be found in the README.txt file.

The Apache Geronimo environment provides two methods for deploying
JEE application components: the web-based administration console and the
command line based deployment environment. This document only explains
the command line environment. Please see the Apache Geronimo 2.x
documentation for the web-based console.

Requirements
============
In order to deploy the Qpid JCA adapter, as well as the example application,
the GERONIMO_HOME environemnt variable must be set. The environment variable
should point to the root of your Apache Geronimo 2.x install directory.

In order to automatically deploy the Qpid JCA Adapter, the QPID_JCA_HOME
environment variable needs to be set. The environment variable should point
to the directory which contains the Qpid JCA Adapter. If building from the
source tree, by default this can be found at

QPID_ROOT/java/build/lib

If installing from RPM or other binary distribution, this can vary by platform.

Prior to deploying any application component, the Apache Geronimo application
should be started and available for requests.


Deploy the Qpid JCA Adapter
==============================
Once the above requirements are satisfied the command

ant deploy-rar

will attempt to use the Apache Geronimo deployer to install the Qpid JCA
adapter into the Apache Geronimo environment. Any errors will be reported
back to the client for further examination. Once the above command executes
successfully, the Qpid JCA adapter has been deployed, configured and is ready
for use.


Deploy the Qpid JCA Examples
============================
After the Qpid JCA adapter is deployed, executing the command

ant deploy-ear

will attempt to use the Apache Geronimo deploy to install the Qpid JCA
example EAR into the Apache Geronimo environment. Any errors will be reported
back to the client for further examination. Once the above command executes
successfully, the Qpid JCA example application is deployed, configured and ready
for use.

The build-geronimo-properties.xml file contain Apache Geronimo specific values
and Ant targets that can be used to suit your development requirements.

The README.txt file in this directory provides the necessary instructions for using the Qpid JCA
adapter and example application.






