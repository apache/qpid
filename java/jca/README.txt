Qpid JCA Resource Adapter Installation/Configuration Instructions

Overview
========
The Qpid Resource Adapter is a JCA 1.5 compliant resource adapter that allows
for JEE integration between EE applications and AMQP 0.10  message brokers.

The adapter provides both outbound and inbound connectivity and
exposes a variety of options to fine tune your messaging applications. Currently
the adapter only supports C++ based brokers and has only been tested with Apache Qpid C++ broker.

The following document explains general configuration information for the Qpid JCA RA. Details for
specific application server platforms are provided in separate README files typically designated as
README-<server-platform>.txt.

Configuration
=============
As per the JCA specification, there are four main components of a JCA resource adapter:

    The ResourceAdapter JavaBean
    The ManagedConnectionFactory JavaBean
    The ActivationSpec JavaBean
    Administered Objects

Each of these components provide configuration options in the form of properties. The Resource Adapter
JavaBean provides a set of global configuration options while the ManagedConnectionFactory JavaBean allows
for the configuration of outbound connectivity options. The ActivationSpec JavaBean provides configuration
options for inbound connectivity.

When a ManagedConnectionFactory JavaBean or ActivationSpec JavaBean are deployed they can choose to inherit
the configuration properties from the ResourceAdapter or provide specific properties which in turn will override
the defaults.

While some of the properties from the three componets are specific to the JCA adapter, a majority of the
properties directly correspond the the Qpid JMS client. As such, it is strongly encouraged your familiarize
yourself with the correct syntax, configuration options for the JMS client as well as the JCA adapter. Similarly,
familiarity with the 1.5 JCA specification is encouraged though not strictly required.

The ResourceAdapter JavaBean
============================

The ResourceAdapter JavaBean provides global configuration options for both inbound and outbound connectivity.
The set of ResourceAdapter properties are described below. The ResourceAdapter properties can be found in the META-INF/ra.xml
deployment descriptor which is provided with the adapter. Note, deploying a ResourceAdapter, ManagedConnectionFactory
or ActivationSpec is application server specific. As such, this document provides an explanation of these properties
but not how they are configured as this is environment specific.

ResourceAdapter JavaBean Properties
===================================

ClientID
   The unique client identifier. From the JMS API this is used only in the context of durable subscriptions.
Default: client_id

SetupAttempts
    The number of attempts the ResourceAdapter will make to successfully setup an inbound activation on deployment, or when an exception
    occurs at runtime.
Default: 5

SetupInterval
    The interval in milliseconds the adapter will wait between setup attempts.
Default: 5000

UseLocalTx
    Whether or not to use local transactions instead of XA.
Default: false

DefaultUserName
    The default user name to use.
Default: guest

DefaultPassword
    The default password to use.
Default: guest

Host
    The hostname/ip address of the broker.
Default: localhost

Port
    The port of the broker.
Default: 5672

Path
   The virtual path for the connection factory.
Default: test

ConnectionURL
   The full connection URL to the broker.
Default:amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'

TransactionManagerLocatorClass
    The class responsible for locating the transaction manager within a specific application server. This is a ResourceAdapter
    Java Bean specific property and is application server specific. As such, it is currently commented out. Two examples have
    been provided.
Default: none

TransactionManagerLocatorMethod
    The specific method on the class above used to acquire a reference to the platform specific transaction manager.
    This is a ResourceAdapter Java Bean specific property and is application server specific.
    As such, it is currently commented out. Two examples have been provided.
Default:none

Note, if you require XA support, both the TransactionManagerLocatorClass and the TransactionManagerLocatorMethod
properties MUST be set. While application servers typically provide a mechanism to do this in the form of a specific
deployment descriptor, or GUI console, the ra.xml file can also be modified directly.

The ManagedConnectionFactory JavaBean
=====================================

The ManagedConnectionFactory JavaBean provides outbound connectivity for the Qpid JCA adapter. In addition to most of the properties
inherited from the ResourceAdapter JavaBean, the ManagedConnectionFactory JavaBean provides specific properties only applicable
to outbound connectivity.

sessionDefaulType
    The default type of Session. Currently unused.
Default: java.jms.Queue

useTryLock
    Multi-purpose property used to specify both that a lock on the underlying managed connection should be used, as well as the
    wait duration to aquire the lock. Primarily used for transaction management. A null or zero value will atttempt to acquire
    the lock without a duration. Anything greater than zero will wait n number of seconds before failing to acquire the lock.
Default:0

KeyStorePassword
    The KeyStore password for SSL
Default:none

KeyStorePath
    The path to the KeyStore.
Default:none

CertType
    The type of certificate.
Default:SunX509

The ActivationSpec JavaBean
===========================
The ActivationSpec JavaBean provides inbound connectivity for the Qpid JCA adapter. In addition to most of the properties
inherited from the ResourceAdapter JavaBean, the ActivationSpec JavaBean provides specific properties only applicable
to inbound connectivity. As opposed to the ResourceAdapter and ManagedConnectionFactory JavaBean a majority of the
ActivationSpec JavaBean properties have no default value. It is expected that these will be provided via Java annotations
or deployment descriptor configuration properties. The Qpid JCA adapter provides inbound connectivity in conjunction
with the Message Driven Bean architecture from the EJB 3.x specification.

UseJNDI
    Whether or not to attempt looking up an inbound destination from JNDI. If false, an attempt will be made to construct
    the destination from the other ActivationSpec properties.
Default: true

Destination
    The name of the destination on which to listen for messages.
Default:none

DestinationType
    The type of destination on which to listen. Valid values are javax.jms.Queue or java.jms.Topic.
Default:none

MessageSelector
    The JMS properties that will be used in selecting specific message. Please see the JMS specification for further details.
Default:none

AcknowlegeMode
    Whether or not the client or consumer will acknowledge any messages it receives. Ignored in a transacted scenario.
    Please see the JMS specification for more details.
Default:AUTO_ACKNOWLEDGE

SubscriptionDurablity
    Whether or not the subscription is durable.
Default:none

SubscriptionName
    The name of the subscription.
Default:none

MaxSession
    The maximum number of sessions that this activation supports.
Default:15

TransactionTimeout
    The timeout for the XA transaction for inbound messages.
Default:0

PrefetchLow
    Qpid specific -- TODO more explanation

PrefetchHigh
    Qpid specific -- TODO more explanation


Administered Objects
======================
The JCA specification provides for administered objects. Ass per the specification, administered objects are
JavaBeans that specific to the messaging provider. The Qpid JCA Resource Adapter provides two administered
objects that can be used to configure JMS destinations and a specialized JMS Connection Factory respectively.
Both these administered objects have properities to support configuration and deployment.

QpidDestinationProxy
====================
    The QpidDestinationProxy allows a developer, deployer or adminstrator to create destinations (queues or topic) and
    bind these destinations into JNDI. The following lists the properties applicable to the QpidDestinationProxy

destinationType
    The type of destination to create. Valid values are QUEUE or TOPIC.

destinationAddress
    The address string of the destination. Please see the Qpid Java JMS client documentation for valid values.

QpidConnectionFactoryProxy
    The QpidConnectionFactoryProxy allows for a non-JCA ConnectionFactory to be bound into the JNDI tree. This
    ConnectionFactory can in turn be used outside of the application server. Typically a ConnectionFactory of
    this sort is used by Swing or other two-tier clients not requiring JCA. The QpidConnectionFactoryProxy provides
    one property

connectionURL
    This is the url used to configure the connection factory. Please see the Qpid Java Client documentation for
    further details.


Transaction Support
===================
The Qpid JCA Resource Adapter provides three levels of transaction support: XA, LocalTransactions and NoTransaction.
Typical usage of the Qpid JCA Resource adapter implies the use of XA transactions, though there are certain scenarios
where this is not preferred. Transaction support configuration is application server specific and as such, is explained
in the corresponding documentation for each supported application server. However, there are two limitations with
the Qpid JCA adapter at this time:

1) Currently, the Qpid C++ broker does not support he use of XA within the context of clustered brokers. As such, if
you are running in a cluster, you will need to configure the adapter to use LocalTransactions.

2)XARecovery is currently not implemented. In the case of a system failure, in doubt transactions will have to be
manually resolved by and administrator or otherwise qualified personnel.

Conclusion
==========
The above documentation provides a general description of the capabilities and configuration properites of the
Qpid JCA Resource Adapter. As previously mentioned, deploying an adapter in an application server requires
specific descriptors and configuration mechanisms. Please see the accompanying doc for your application server
for further details.


