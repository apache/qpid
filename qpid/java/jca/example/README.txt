Qpid JCA Example

Overview
=======
The Qpid JCA example provides a sample JEE application that demonstrates how to
configure, install and run applications using the Qpid JCA adapter for EE
connectivity and the Apache Qpid C++ Broker. This example code can be used as a
convenient starting point for your own development and deployment
efforts. Currently the example is supported on JBoss EAP 5.x, JBoss 6.x,
Apache Geronimo 2.x and Glassfish 3.1.1.

Example Components
===================
Currently the example application consists of the following components:

Destinations and ConnectionFactories

Any messaging application relies on destinations (queues or topics )
in order to produce or consume messages.The Qpid JCA example provides
five destinations by default:

   HelloTopic
   GoodByeTopic
   HelloGoodByeTopic
   HelloQueue
   GoodByeQueue
   QpidResponderQueue


Similar to destinations, ConnectionFactories are a core component of both JMS
and JCA. ConnectionFactories provide the necessary starting point to make a connection,
establish a session and produce or consume (or both) messages from your JMS provider.

The Qpid JCA example provides three connection factories by default:

    QpidJMSXA
    QpidJMS
    QpidConnectionFactory

Each of these ConnectionFactories varies in their capabilities and the context in which
they should be used. These concepts will be explained later in this document.

The deployment configuration for destinations, and ConnectionFactories varies by platform.
In JBossEAP, the configuration mechanism is a *-ds.xml file. Geronimo 2.2.x has the notion
of a deployment plan in the form of a geronimo-ra.xml file. Similarly, Glassfish 3.1.1 uses
the glassfish-resources.xml file.

The Qpid JCA Example provides a sample qpid-jca-ds.xml, geronimo-ra.xml and glassfish-resources.xml file.
Each file provides reasonable set of defaults to allow you to deploy the Qpid JCA
adapter in the supported environments and get up and running quickly.

EJB 3.x

There are a six EJB 3.x components provided as part of the example.

   QpidHelloSubscriberBean - MessageDrivenBean (MDB)
   QpidGoodByeSubscriberBean - (MDB)
   QpidHelloListenerBean - (MDB)
   QpidGoodByeListenerBean - (MDB)
   QpidJMSResponderBean - (MDB)
   QpidTestBean - Stateless Session Bean (SLSB)

Servlet 2.5

    QpidTestServlet

A sample EE 2.5 servlet is provided allowing testing from a browser versus a JNDI
client

EE EAR archive
    An EAR wrapper for the ejb and web components.


An RMI client used to excercise the EJB 3.x component.

Sample *-ds.xml file
    A sample *-ds.xml file is provided to create destinations and ManagedConnectionFactories
    in the JBoss environment.

Sample geronimo-ra.xml
    A sample geronimo-ra.xml file is provided to create destinations and ManagedConnectionFactories
    in the Geronimo environment. This file is semantically equivalent to the JBoss *-ds.xml artifact.

Sample glassfish-resources.xml
    A sample glassfish-resources.xml file is provided to create JMS destinations and
    ManagedConnectionFactories in the Glassfish environemnt.

A build.xml file
    An ant build.xml file to configure, install and deploy the aforementioned components.


Requirements
============

Depending upon your target platform (eg. JBoss EAP or Geronimo) you will need to set either
the JBOSS_HOME or GERONIMO_HOME property. By default, these properties are assumed to be
set from your environment. This can be modified in the build.xml file.

JBoss EAP 5.x, JBoss 6.x
    To use the sample application you will need to have JBoss EAP 5.x or JBoss 6.x running.

Geronimo 2.x
    To use the sample application you will need to have Geronimo 2.x running.

Apache Qpid Broker
    To run the sample it is assumed you have an Apache Qpid C++ broker configured and running.
    The example code assumes that the broker will run at localhost on port 5672. This can be
    modified within the build.xml file if this does not suit your particular environment.


Quickstart
==========
After satifsying the above requirements you are ready to deploy and run the example application.
The steps to deploy and run in the supported application servers are largely the same, however,
you need to specify the target platform environment to which you are attempting to deploy.

    <property name="target.platform" value="jboss"/>
    <property name="target.platform" value="geronimo"/>
    <property name="target.platform" value="glassfish"/>

or set this property via the command line.

Example:

    ant -Dtarget.platform=jboss <target>

**Note**
Any time you wish to change platforms, this property needs to be modified and a complete clean
and rebuild needs to be performed.

Step 1 -- Package, Deploy and configure the Qpid JCA adapter

The core component of the example is the Qpid JCA adapter. The following lists the steps
for the respective platforms

**Note**

Regardless of platform, if you are building the Qpid JCA adapter from source code
you will need to use to package the RAR file via the Ant build system. To do this, from
the example directory execute

    ant deploy-rar

This task packages the adapter and includes the necessary dependent jar files.


JBoss
    There are no additional steps to package the adapter for JBoss deployment. Simply copy
    the qpid-ra-<qpid.version>rar to your JBoss deploy directory.

    To configure the Qpid JCA Adapter in JBoss the *-ds.xml file mechanism is used. A sample
    file is provided in the conf directory.

    If the defaults are suitable, you can simply execute

    ant deploy-ds

    While any property can be modified in the qpid-jca-ds.xml file, typically you will want to
    change the URL of the broker to which you are trying to connect. Rather than modifying
    the qpid-jca-ds.xml file directly you can modify the

       <property name="broker.url" value="amqp://anonymous:@client/test?brokerlist='tcp://localhost:5672?sasl_mechs='ANONYMOUS''"/>

    line in the build.xml file. This will dynamically insert the broker.url value into the qpid-jca-ds.xml file.

    Once this file is copied to your JBoss deploy directory and you received no exceptions, the adapter is now deployed, configured
    and ready for use.

Geronimo
    By default, the Qpid JCA adapter ships with the geronimo-ra.xml deployment plan embedded
    in the RAR file. This file is located in the META-INF directory alongside the ra.xml file.
    By default the adapter is configured to access a broker located at localhost with the
    default port of 5672. The ANONYMOUS security mechanism is also in use. If this is not
    desirable, you have two approaches to configure the adapter.


    1) Extract the META-INF/ra.xml file from the RAR file, modify and recompress the RAR archive
       with the updated contents.

    2) Use the example build system to package the adapter. The example build.xml file includes
       a target

       package-rar

       that can be used to package the RAR file as well as allowing changes to the geronimo-ra.xml
       file without having to extract the RAR file by hand. The conf/geronimo-ra.xml file is used
       when you use the example build system.

       While any property can be modified in the geronimo-ra.xml file, typically you will want to
       change the URL of the broker to which you are trying to connect. Rather than modifying
       the geronimo-ra.xml file directly you can modify the


       <property name="broker.url" value="amqp://anonymous:@client/test?brokerlist='tcp://10.0.1.44:5672?sasl_mechs='ANONYMOUS''"/>

       line in the build.xml file. This will dynamically insert the broker.url value into the geronimo-ra.xml file.

       Once you have made your modifications you can execute


       ant clean package-rar deploy-rar

       Note, your Geronimo server must be running and your GERONIMO_HOME environment variable must be set. Barring any exceptions, the
       adapter is now deployed and ready for use in Geronimo.


Glassfish
    As previously mentioned, the Glassfish environment uses the glassfish-resources.xml file to configure AdminObjects and ManagedConnectionFactories.
    A sample file is provided. To deploy the file simply execute:

    ant deploy-rar

    If building from the Qpid source tree, this will package and deploy the qpid-ra-<version>.rar file as well as configure the adapter. If you are
    not building from source, the adapter will be configured correctly via the glassfish-resources.xml file.


Step 2 -- Deploy the application component(s).

As previously mentioned, the adapter comes with a variety of EE components for use in your respective application server. You can choose to deploy
these components individually, or as a single EAR archive. This document assumes that you will use the EAR to deploy and run the example.

The command to package and deploy the EAR archive is the same across application servers. Executing the following command

ant deploy-ear

will, depending upon platform, package the EAR and deploy the archive in your respective environment. Once this step is executed, the example
is ready for use.


Step 3 -- Test the Example

The Qpid JCA example provides an EJB application, as well as a Web application. Both can be used to test/run the example:

EJB
If you want to use the EJB application to test the example you can execute

    ant run-client

Running this command will perform a JNDI lookup of the SLSB in either JBoss or Geronimo and send a simple string to the SLSB. The SLSB will receive
this string, construct a JMS message and place this message on a configured queue. The MDB will in turn receive this message and print the contents
to the console.

The main properties involved in this task are

server.host
jndi.context

These vary depending upon which application server you are runnning. These can be modified to suit your environment. Looking at the run-client task you
will see the following:


            <sysproperty key="qpid.ejb.name" value="qpid-jcaex/QpidTestBean/remote"/>

This is the JNDI name of the SLSB component and it varies by application server. Typically you do not have to change this. Also, the task supports another property


            <sysproperty key="qpid.message" value="insert-value-here"/>

You can set this property if you want to modify the message contents being routed through the system.

JMS
If you do not want to use EJB and prefer to test the Qpid JCA adapter using the standard JMS API's, simply set the following property

    <property name="client.use.ejb" value="true"/> <!-- uses JNDI/JMS or JNDI/RMI -->

as

    <property name="client.use.ejb" value="false"/> <!-- uses JNDI/JMS or JNDI/RMI -->


Request/Reply

The EJB/JMS client simply sends a message to a destination and does not receive a response. The Qpid JCA examples includes a request-reply
example to allow you to receive a response. The following command:

ant run-reqresp

will execute this example. A variety of configuration options for both the EJB/JMS and Request/Reply are provided. Please see the build.xml file for more details.

Web
The Qpid JCA Example comes with a web application. To access the web component, simply use a browser of your choice and navigate to

http://<server-host-name>:<server-port>/qpid-jca-web/qpid

where server-host and server-port are the host and port where you are running your application server. By default this is localhost:8080. Similar to the EJB component,
the web application supports a few options:


http://<server-host-name>:<server-port>/qpid-jca-web/qpid?messsage=<yourmessage>

will allow you to customize the message contents that are routed through the system. By default, the Web application posts to a configured queue in the system. If you want to
test XA functionality, or use an alternate approach, you can specify


http://<server-host-name>:<server-port>/qpid-jca-web/qpid?useEJB=true

instead of posting to a queue, the web application will use the local interface of the EJB component to send the message. This is functionally equivalent to running the
RMI client.


Similar to the Request/Reply example, a Request/Reploy Servlet is provided as well. To access this servlet navigate to the above URL:


http://<server-host-name>:<server-port>/qpid-jca-web/qpid-reqresp

A reasonable set of defaults is provided which can be further tuned and configured to suit your development needs.


Summary
=======
While conceptually simple, the Qpid JCA example provides a majority of the component types and messaging patterns you are most likely to use your development efforts.
With the Web and EJB components, you can experiment with various aspects of JCA as well as EE development in general using the Qpid Broker as your messaging provider.
While this documentation highlights the major components and steps needed to take to get the example running, the possiblities for modifcation are numerous. You are
encouraged to experiment with the example as you develop your own messaging applications.


