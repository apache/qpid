Qpid JCA Resource Adapter

JBoss EAP 6.x Installation and Configuration Instructions

Overview
========
The Qpid Resource Adapter is a JCA 1.5 compliant resource adapter that allows
for JEE integration between EE applications and AMQP 0.10  message brokers.

The adapter provides both outbound and inbound connectivity and
exposes a variety of options to fine tune your messaging applications.
Currently the adapter only supports C++ based brokers and has only been tested with Apache Qpid C++ broker.

The following document explains how to configure the resource adapter for deployment in JBoss EAP 6.x.

Deployment
==========
To deploy the Qpid JCA adapter in the JBoss EAP 6 environment, copy the qpid-ra-<version>.rar file
to your JBoss deployment directory. By default this can be found at

JBOSS_ROOT/<server-config>/deployments

where JBOSS_ROOT denotes the root directory of your JBoss EAP 6.x installation and <server-config> denotes the
particular server configuration for your applicationd development. Currently, JBoss EAP 6 provides two configurations
by default standalone and domain. This documentation assumes the standalone server configuration, though the process
to configure and deploy the Qpid JCA adapter is largely the same between the two. Assuming the standalone configuration
the deployment location above would be

JBOSS_ROOT/standalone/deployments

Note, as opposed to prior versions of EAP, copying a RAR file to the deployment location does not automatically
start and deploy the adapter. A separate manual configuration step is required which is explained in the following
section.

Configuration
=============
The EAP 6.x environment uses an XML based configuration scheme that is fundamentally different than prior versions
of EAP. As previously mentioned, EAP 6.x provides two server configuration types, standalone and domain. Each come with
a different set of configuration files that are tailored to varying types of server environments. Configuration locations
can be found at

JBOSS_ROOT/<server-config>/configuration

The varying XML files are named

<server-config>-full.xml
<server-config>-full-ha.xml
<server-config>.xml

where each XML file denotes the capabilites of the server. This document assumes a minimal server configuration in
the standalone server environment. While each configuration file provides a variety of options, this document is
only concerned with the configuration of the JCA adapter. Please consult the EAP 6.x documentation for other
options and configuration scenarios.

The EAP 6.x infrastructure is built upon the notion of varying subsystem where subsystem generally corresponds
to one particular piece of functionality. Typical examples are EJB, JAXR etc. In order to configure the Qpid JCA adapter
we need to modify the ejb and resource-adapters subsystems in order to tell EAP 6.x about our RAR deployment as well
as the RAR that will provide JMS provider functionality.

Note, JCA in EAP 6.x involves two subsystems, jca and resource-adapters. The former subsytem provides capabilities for
all JCA deployments (the JCA runtime environment) where the resource-adapters subsystem is particular to an invidual JCA
deployment. Here we are only concerned with the latter. Please consult the EAP 6.x documentation for more general JCA
configuration options as well as other subsystems.

Each subsystem is configured in an XML fragment and is versioned separately. Subsystem versions will change over
time so this document may not reflect the most current version, but the Qpid JCA configuration options remain
unchanged across subsystem regardless of version or release date.

The following XML fragment replaces the default messaging provider in the EAP 6.x environment

        <subsystem xmlns="urn:jboss:domain:ejb3:1.2">
            <session-bean>
                <stateless>
                    <bean-instance-pool-ref pool-name="slsb-strict-max-pool"/>
                </stateless>
                <stateful default-access-timeout="5000" cache-ref="simple"/>
                <singleton default-access-timeout="5000"/>
            </session-bean>
            <mdb>
                <resource-adapter-ref resource-adapter-name="qpid-ra-<rar-version>.rar"/>
                <bean-instance-pool-ref pool-name="mdb-strict-max-pool"/>
            </mdb>
            <pools>
                <bean-instance-pools>
                    <strict-max-pool name="slsb-strict-max-pool" max-pool-size="20" instance-acquisition-timeout="5" instance-acquisition-timeout-unit="MINUTES"/>
                    <strict-max-pool name="mdb-strict-max-pool" max-pool-size="20" instance-acquisition-timeout="5" instance-acquisition-timeout-unit="MINUTES"/>
                </bean-instance-pools>
            </pools>
            <caches>
                <cache name="simple" aliases="NoPassivationCache"/>
                <cache name="passivating" passivation-store-ref="file" aliases="SimpleStatefulCache"/>
            </caches>
            <passivation-stores>
                <file-passivation-store name="file"/>
            </passivation-stores>
            <async thread-pool-name="default"/>
            <timer-service thread-pool-name="default">
                <data-store path="timer-service-data" relative-to="jboss.server.data.dir"/>
            </timer-service>
            <remote connector-ref="remoting-connector" thread-pool-name="default"/>
            <thread-pools>
                <thread-pool name="default">
                    <max-threads count="10"/>
                    <keepalive-time time="100" unit="milliseconds"/>
                </thread-pool>
            </thread-pools>
        </subsystem>

The only real lines we are concerned with are

            <mdb>
                <resource-adapter-ref resource-adapter-name="qpid-ra-<rar-version>.rar"/>
                <bean-instance-pool-ref pool-name="mdb-strict-max-pool"/>
            </mdb>

however, the complete fragment is provided for clarity.

The following XML fragment provides a minimal example configuration in the EAP 6 environment. Here we are configuring
an XA aware ManagedConnectionFactory and two JMS destinations (queue and topic)

        <subsystem xmlns="urn:jboss:domain:resource-adapters:1.0">
            <resource-adapters>
                <resource-adapter>
                    <archive>
                        qpid-ra-<rar-version>.rar
                    </archive>
                    <transaction-support>
                        XATransaction
                    </transaction-support>
                    <config-property name="connectionURL">
                        amqp://anonymous:passwd@client/test?brokerlist='tcp://localhost?sasl_mechs='PLAIN''
                    </config-property>
                    <config-property name="TransactionManagerLocatorClass">
                        org.apache.qpid.ra.tm.JBoss7TransactionManagerLocator
                    </config-property>
                    <config-property name="TransactionManagerLocatorMethod">
                        getTm
                    </config-property>
                    <connection-definitions>
                        <connection-definition class-name="org.apache.qpid.ra.QpidRAManagedConnectionFactory" jndi-name="QpidJMSXA" pool-name="QpidJMSXA">
                            <config-property name="connectionURL">
                                amqp://anonymous:passwd@client/test?brokerlist='tcp://localhost?sasl_mechs='PLAIN''
                            </config-property>
                            <config-property name="SessionDefaultType">
                                javax.jms.Queue
                            </config-property>
                        </connection-definition>
                    </connection-definitions>
                    <admin-objects>
                        <admin-object class-name="org.apache.qpid.ra.admin.QpidTopicImpl" jndi-name="java:jboss/exported/GoodByeTopic" use-java-context="false" pool-name="GoodByeTopic">
                            <config-property name="DestinationAddress">
                                amq.topic/hello.Topic
                            </config-property>
                        </admin-object>
                        <admin-object class-name="org.apache.qpid.ra.admin.QpidQueueImpl" jndi-name="java:jboss/exported/HelloQueue" use-java-context="false" pool-name="HelloQueue">
                            <config-property name="DestinationAddress">
                                hello.Queue;{create:always, node:{type:queue, x-declare:{auto-delete:true}}}
                            </config-property>
                        </admin-object>
                    </admin-objects>
                </resource-adapter>
            </resource-adapters>
        </subsystem>



Note, while this document assumes that you are modifying the standalone.xml file directly, an alternative to this approach would be
to make a copy of the file, apply the modifications above and start the EAP instance with the new configuration

JBOSS_HOME/bin/standalone.sh -c your-modified-config.xml

Regardless of the approach that you use, once the modifications have been made you can start your EAP 6.x instance and the Qpid JCA
adapter will be deployed and ready for use. If property deployed and configured, you should see something in the log files or console
resembling the following:

INFO  [org.apache.qpid.ra.QpidResourceAdapter] (MSC service thread 1-4) Qpid resource adapter started


Notes
=====
While the differences between the EAP 5.x and 6.x environments may appear to be dramatic, the configuration options and functionality of the Qpid
JCA adapter are not. The README.txt file outlines general configuration options that remain unchanged between the respective EAP environments.

