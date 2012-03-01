Qpid JCA Resource Adapter

JBoss EAP 5.x Installation and Configuration Instructions

Overview
========
The Qpid Resource Adapter is a JCA 1.5 compliant resource adapter that allows
for JEE integration between EE applications and AMQP 0.10  message brokers.

The adapter provides both outbound and inbound connectivity and
exposes a variety of options to fine tune your messaging applications.
Currently the adapter only supports C++ based brokers and has only been tested with Apache Qpid C++ broker.

The following document explains how to configure the resource adapter for deployment in JBoss EAP 5.x.


Deployment
==========
To deploy the Qpid JCA adapter for either JBoss EAP, simply copy the qpid-ra-<version>.rar file
to your JBoss deploy directory. By default this can be found at JBOSS_ROOT/server/<server-name>/deploy,
where JBOSS_ROOT denotes the root directory of your JBoss installation and <server-name> denotes the
name of your deployment server. A successful adapter installation will be accompanied by the
following INFO message:

    INFO  [QpidResourceAdapter] Qpid resource adaptor started

At this point the adapter is deployed and ready for configuration.

Configuration Overview
======================
The standard configuration mechanism for 1.5 JCA adapters is the ra.xml
deployment descriptor. Like other EE based descriptors this file can be found
in the META-INF directory of the provided EE artifact (ie .rar file). A majority
of the properties in the ra.xml will seem familiar to anyone who has worked with
Apache Qpid in a standalone environment. A reasonable set of configuration defaults
have been provided.

The resource adapter configuration properties provide generic properties for both
inbound and outbound connectivity. These properties can be overridden when deploying
managed connection factories as well as inbound activations using the standard JBoss
configuration artifacts, the *-ds.xml file and MDB activation spec . A sample *-ds.xml file,
qpid-jca-ds.xml, can be found in your Qpid JCA resource adapter directory.

The general README.txt file provides a detailed description of all the properties associated
with the Qpid JCA Resource adapter. Please consult this file for further explanation of
how configuration properties are treated within the Qpid JCA adapter.

ConnectionFactory Configuration
======================================
As per the JCA specification, the standard outbound-connectivity component is the
ConnectionFactory. In EAP 5.x ConnectionFactories are configured
via the *-ds.xml file. As previously mentioned, a sample *-ds.xml file, qpid-jca-ds.xml
hasbeen provided with your distribution. This file can be easily modified to suit
your development/deployment needs. The following describes the ConnectionFactory
portion of the sample file.

XA ConnectionFactory
====================
  <tx-connection-factory>
    <jndi-name>QpidJMSXA</jndi-name>
    <xa-transaction/>
    <rar-name>qpid-ra-<ra-version>.rar</rar-name>
    <connection-definition>org.apache.qpid.ra.QpidRAConnectionFactory</connection-definition>
    <config-property name="connectionURL">amqp://guest:guest@/test?brokerlist='tcp://localhost:5672?sasl_mechs='ANONYMOUS''</config-property>
    <max-pool-size>20</max-pool-size>
  </tx-connection-factory>

The QpidJMSXA connection factory defines an XA capable ManagedConnectionFactory. You will need to insert your particular rar version for
the rar-name property. The jndi-name and connectionURL property are both configurable and can be modified for your environment. After deployment
the ConnectionFactory will be bound into JNDI under the name

java:<jndi-name>

For the previous example, this would resolve to

java:QpidJMSXA

Local ConnectionFactory
=======================
  <tx-connection-factory>
    <jndi-name>QpidJMS</jndi-name>
    <rar-name>qpid-ra-0.10.rar</rar-name>
    <local-transaction/>
    <config-property name="useLocalTx" type="java.lang.Boolean">true</config-property>
    <config-property name="connectionURL">amqp://anonymous:@client/test?brokerlist='tcp://localhost:5672?sasl_mechs='ANONYMOUS''</config-property>
    <connection-definition>org.apache.qpid.ra.QpidRAConnectionFactory</connection-definition>
    <max-pool-size>20</max-pool-size>
  </tx-connection-factory>

The QpidJMS connection factory defines a non XA connection factory. Typically this is used as a specialized ConnectionFactory where either XA
is not desired, or you are running with a clustered Qpid Broker configuration which at this time, does not support XA. The configuration
properties mirror those of the XA ConnectionFactory.

Admininstered Object Configuration
==================================
Destinations (queues, topics) are configured in EAP via JCA standard Administered Objects (AdminObjects). These objects
are placed within the *-ds.xml file alongside your ConnectionFactory configurations. The sample file qpid-jca-ds.xml
provides two such objects

  <mbean code="org.jboss.resource.deployment.AdminObject"
         name="qpid.jca:name=HelloQueue">
     <attribute name="JNDIName">Hello</attribute>
     <depends optional-attribute-name="RARName">jboss.jca:service=RARDeployment,name='qpid-ra-0.10.rar'</depends>
     <attribute name="Type">javax.jms.Destination</attribute>
     <attribute name="Properties">
        destinationType=QUEUE
        destinationAddress=amq.direct
     </attribute>
  </mbean>

The above XML defines a JMS Queue which is bound into JNDI as

queue/HelloQueue

This destination can be retrieved from JNDI and be used for the consumption or production of messages. The desinationAddress property
can be customized for your environment. Please see the Qpid Java Client documentation for specific configuration options. 

  <mbean code="org.jboss.resource.deployment.AdminObject"
         name="qpid.jca:name=HelloTopic">
     <attribute name="JNDIName">HelloTopic</attribute>
     <depends optional-attribute-name="RARName">jboss.jca:service=RARDeployment,name='qpid-ra-0.10.rar'</depends>
     <attribute name="Type">javax.jms.Destination</attribute>
     <attribute name="Properties">
        destinationType=TOPIC
        destinationAddress=amq.topic
     </attribute>
  </mbean>


The above XML defines a JMS Topic which is bound into JNDI as

HelloTopic

This destination can be retrieved from JNDI and be used for the consumption or production of messages. The desinationAddress property
can be customized for your environment. Please see the Qpid Java Client documentation for specific configuration options.


  <mbean code="org.jboss.resource.deployment.AdminObject"
         name="qpid.jca:name=QpidConnectionFactory">
     <attribute name="JNDIName">QpidConnectionFactory</attribute>
     <depends optional-attribute-name="RARName">jboss.jca:service=RARDeployment,name='qpid-ra-0.10.rar'</depends>
     <attribute name="Type">javax.jms.ConnectionFactory</attribute>
     <attribute name="Properties">
        connectionURL=amqp://anonymous:@client/test?brokerlist='tcp://localhost:5672?sasl_mechs='ANONYMOUS''
     </attribute>
  </mbean>

The above XML defines a ConnectionFactory that can be used external to EAP 5.x. Typically this connection factory
is used by standalone or 'thin' clients that do not require an application server. This object is bound into
the EAP 5.x JNDI tree as

QpidConnectionFactory

ActivationSpec Configuration
============================
The standard method for inbound communication is the MessageDrivenBean architecture with is configured
via the ActivationSpec mechanism. Please see the general README.tx file for an explanation of the
QpidActivationSpec, as well as general inbound connectivity options.

An ActivationSpec can either be configured via the Java Annotation mechanism, or in the ejb-jar.xml deployment
descriptor.

Summary
=======
The above description for the Qpid JCA adapter for EAP 5.x is just a general guide for deploying and configuring
the Qpid JCA adapter. The sample file provided can be easily modified and it is expected you will do so to
conform to your own environment.

