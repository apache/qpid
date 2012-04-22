Qpid JCA Example

Overview
========
The Qpid JCA example provides a sample JEE application that demonstrates how to
configure, install and run applications using the Qpid JCA adapter for JEE
connectivity and the Apache Qpid C++ Broker. This example code can be used as a
convenient starting point for your own development and deployment
efforts.

Example Components
===================
Currently the example application consists of the following components:

Destinations
============
Any messaging application relies on destinations (queues or topics )
in order to produce or consume messages.The Qpid JCA example provides
the following destinations:

   HelloTopic
   GoodByeTopic
   HelloQueue
   GoodByeQueue
   QpidRequestQueue
   QpidResponseQueue

Each destination is configured for the respective application server in which
the examples will be run. Generally the example application allows for sending
messages to a Hello<DestinationType>. Depending on a configuration option, once
a message is received by the Hello component, the Hello component can also be
sent to a GoodBye<DestinationType> component.

The QpidRequest/Response destinations provide the destinations for the
request/reponse messaging pattern.


ConnectionFactories
===================
Similar to destinations, ConnectionFactories are a core component of both JMS
and JCA. ConnectionFactories provide the necessary starting point to make a
connection, establish a session and produce or consume (or both) messages from
your JMS provider.

The Qpid JCA example provides three connection factories by default:

    QpidJMSXA
    QpidJMS
    QpidConnectionFactory

Each of these ConnectionFactories varies in their capabilities and the context in which
they should be used. By default, the Qpid JCA examples use the QpidJMSXA connection factory
which provides for full XA transaction support. The QpidJMS connection factory provides a
local JMS transaction CF and is currently deprecated, though it has been maintained for
backwards compatibility. The QpidConnectionFactory is a specialized ConnectionFactory designed
to be used outside of a JEE application server. This CF has been provided to support non-managed
clients that want to use the JNDI facilities of JEE.

The deployment configuration for destinations, and ConnectionFactories varies by JEE platform.
Please see the application server specific documentation for specific instructions.

EJB 3.x

There are a six EJB 3.x components provided as part of the example.

   QpidHelloSubscriberBean - MessageDrivenBean (MDB)
   QpidGoodByeSubscriberBean - (MDB)
   QpidHelloListenerBean - (MDB)
   QpidGoodByeListenerBean - (MDB)
   QpidJMSResponderBean - (MDB)
   QpidTestBean - Stateless Session Bean (SLSB)

Generally, the name of the EJB component corresponds to the aforementioned destinations
listed above in the consumption of messages.

QpidTestServlet
A sample EE 2.5 servlet is provided allowing testing from a browser versus a RMI or JMS
based client.

QpidTestClient
A Java based client allowing for both RMI or JMS access to the sample application. RMI or
JMS access is based on configuration options.

QpidRequestResponseClient
A Java based client allowing for a request/response application.

EE EAR archive
    An EAR wrapper for the ejb and web components.

A build.xml file
    An ant build.xml file to configure, install and deploy the aforementioned components.

Requirements
============
Apache Qpid Broker
To run the sample it is assumed you have an Apache Qpid C++ broker configured and running.
The example code assumes that the broker will run at localhost on port 5672. The broker
address can be modified in the build-properties.xml file.

Examples
===============
Using the Qpid JCA examples is the same regardless of preferred JEE application server.
The provided clients and supported options are listed below.

QpidTestClient
==============
As previously mentioned, the Qpid JCA example provides an RMI/JMS Java client to test
the application. Executing the command

ant-run client

will execute the QpidTestClient client with the default options.

QpidTestClient Options

client.use.ejb (build.xml)
Setting this value to false will result in the QpidTestClient using JMS to send messages to
the configured destination.
Default:true

client.message
The text content of the JMS message sent to the configured destination.
Default: Hello Qpid World!

client.message.count
The number of messages that will be sent to the configured destionation.
Default: 1

client.use.topic
Setting this value to true will send messages to HelloTopic versus HelloQueue.
Default:false

client.say.goodbye
Setting this value to true will cause the listener on the Hello Queue/Topic to send a
message to the GoodBye Queue/Topic
Default:false


QpidRequestResponseClient
=========================
Similar to the QpidTestClient, the QpidRequestResponseClient is a Java based application that allows for
sending messages to a destination. The QpidRequestResponseClient also provides the capability for the
application to listen for a response. Executing the command

ant run-reqresp

will run the application with the default options.

QpidRequestResponseClient Options

QpidTestServlet
===============
Similar to the QpidTestClient application, the Qpid JCA examples also provides a JEE Servlet to test and run the
application. This Servlet can be used as an alternative, or in conjunction with the Java application. The Servlet
can be reached via the following URL:

http://<server-host-name>:<server-port>/qpid-jca-web/qpid

where server-host and server-port are the host and port where you are running your application server.

QpidTestServlet Options

The QpidTestServlet options can be configured by appending certain values to the above URL. These values are
listed below and generally correspond, both in name and purpose, to the Java application configuration options.

message
The text content of the JMS message sent to the configured destination.
Default: Hello World!

useEJB
Setting this value to true will result in the QpidTestServlet using the QpidTestBean SLSB to
send the message to the configured destination. By default the QpidTestServlet uses JMS.
Default:false

count
The number of messages that will be sent to the configured destionation.
Default: 1

useTopic
Setting this value to true will send messages to HelloTopic versus HelloQueue.
Default:false

sayGoodBye
Setting this value to true will cause the listener on the Hello Queue/Topic to send a
message to the GoodBye Queue/Topic
Default:false

useXA
Setting this value to true will cause the QpidTestServlet to do all activity within the
context of an XA transaction.
Default:false

useTX
Setting this value to true will cause the QpidTestServlet to do all activity within the
context of a JMS transation.
Default:false


Note, the useXA and useTX are mutually exclusive. Setting both to true, the QpidTestServlet
will choose XA over a JMS transaction.

QpidRequestResponseServlet
==========================
Similar to the QpidRequestResponseClient application, the Qpid JCA examples also provides a JEE Servlet
to execute the request/response type messaging pattern. The Servlet can be reached via the following url:

http://<server-host-name>:<server-port>/qpid-jca-web/qpid-reqresp

where server-host and server-port are the host and port where you are running your application server.

QpidRequestResponseServlet Options

message
The text content of the JMS message sent to the configured destination.
Default: Hello World!

count
The number of messages that will be sent to the configured destionation.
Default: 1

useXA
Setting this value to true will cause the QpidTestServlet to do all activity within the
context of an XA transaction.
Default:false

Summary
=======
While conceptually simple, the Qpid JCA examples provide a majority of the component types and messaging patterns
you are most likely to use your development efforts. With the Web and EJB components, you can experiment with
various aspects of JCA as well as EE development in general using the Qpid Broker as your messaging provider.
While this documentation highlights the major components and steps needed to take to get the example running,
the possiblities for modifcation are numerous. You are encouraged to experiment with the example as you work
to develop your own messaging applications.


