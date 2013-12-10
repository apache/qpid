Qpid JCA Resource Adapter

Apache Geronimo 2.x Installation and Configuration Instructions

Overview
========
The Qpid Resource Adapter is a JCA 1.5 compliant resource adapter that allows
for JEE integration between EE applications and AMQP 0.10  message brokers.

The adapter provides both outbound and inbound connectivity and
exposes a variety of options to fine tune your messaging applications.
Currently the adapter only supports C++ based brokers and has only been tested with Apache Qpid C++ broker.


The following document explains how to configure the resource adapter for deployment in Geronimo 2.x

Configuration and Deployment
============================

The Apache Geronimo 2.x application server requires the use of an RA deployment plan to deploy and configure
a resource adapter. A sample deployment plan has been provided as geronimo-ra.xml which is included in the
META-INF directory of the qpid-ra-<version>.rar file. If you need to modify this file, simply extract
the RAR file, edit the geronimo-ra.xml file and recompress the file.

Please refer to the general README.txt file for a description of each configuration property
the adapter supports for resource adapter, managedconnectionfatory and activationspec level configuration.



