1)DESCRIPTION
Q-Man is a Management bridge that exposes one (or several) Qpid broker domain model as MBeans that are accessible through the Java Management Extensions (JMX).
The following README content could be read also in http://cwiki.apache.org/confluence/display/qpid/Qman+Tool

2)HOW TO RUN Q-Man

2.1)PREREQUISITES
QMan is a standalone application that is packaged as qpid-management-client-<version>.jar. To run QMan you need to add the following jars in your CLASSPATH:

log4j-1.2.12.jar
slf4j-api-1.4.0.jar
slf4j-log4j12-1.4.0.jar
commons-pool-1.4.jar
commons-codec-1.3.jar
commons-lang-2.2.jar
commons-collections-3.2.jar
commons-configuration-1.2.jar
qpid-client-<version>.jar (were <version> is the current qpid version)
qpid-common-<version>.jar (were <version> is the current qpid version)

alternatively you can run the following script (that add all the qpid jars to the CLASSPATH):

> CLASSPATH=`find <lib-root> -name '*.jar' | tr '\n' ":"`

Where <lib-root> is the directory containing the qpid jars (when qpid is built from source <lib-root> is equal to qpid/java/build/lib)

You should have in your classpath a log4j.xml configuration file too with a category defined as this :

<category name="org.apache.qpid.management">
<priority value="INFO"/>
</category>

2.2) CONFIGURATION
QMan can be connected at run time against any broker. However if you wish to automatically connect to one or several brokers you can do so by providing a configuration file as follows:

<configuration>
  <brokers>
	<broker>
	  <host>localhost</host>
	  <port>5672</port>
	  <virtual-host>test</virtual-host>
	  <user>guest</user>
	  <password>guest</password>
	  <max-pool-capacity>4</max-pool-capacity>
	  <initial-pool-capacity>0</initial-pool-capacity>
	  <max-wait-timeout>-1</max-wait-timeout>
	</broker>
        <broker>
	  <host>myhost</host>
	  <port>5672</port>
	  <virtual-host>test</virtual-host>
	  <user>guest</user>
	  <password>guest</password>
	  <max-pool-capacity>4</max-pool-capacity>
	  <initial-pool-capacity>0</initial-pool-capacity>
	  <max-wait-timeout>-1</max-wait-timeout>
	</broker>
  </brokers>
</configuration>
The configuration above specifies that QMan should connect to two brokers, one on localhos and one on myhost, both listening on port 5672.

The configuration file to use is specified through the JVM parameter "qman-config" that must point onto a valid configuration file.

2.3)RUNNING Q-Man

To run QMan in a console run the following command:

> java -Dcom.sun.management.jmxremote org.apache.qpid.management.domain.services.QMan

Messages similar to those should be displayed:

... [org.apache.qpid.management.domain.services.QMan] <QMAN-000001> : Starting Q-Man...
...
Type "q" to quit.

if you wish to use a configuration file <home>/myconfiguration.xml so QMan establishes a connection with one or several brokers, run the following command:

java -Dqman-config="<home>/myconfiguration.xml" org.apache.qpid.management.domain.services.QMan 


2.4) STOPPING Q-Man
Type "q" In the console from which QMan has been started.

3) Browsing Manageable Beans using JConsole
The jconsole tool (JMX-compliant graphical tool for monitoring a Java virtual machine) can be used for monitoring and QMan Mbeans. for more information see http://java.sun.com/javase/6/docs/technotes/guides/management/jconsole.html

The jconsole executable can be found in JDK_HOME/bin, where JDK_HOME is the directory in which the JDK software is installed. If this directory is in your system path, you can start JConsole by simply typing jconsole in a console. Otherwise, you have to type the full path to the executable file.

As jconsole needs to perform operations invocation you will need to add the QMan jar in jconsole classpath. In a console type:

jconsole -J-Djava.class.path=$CLASSPATH:$JAVA_HOME/lib/jconsole.jar:$JAVA_HOME/lib/tools.jar
Where CLASSPATH contains the QMan jars and JAVA_HOME point on your JDK home.

NOte that in order to see QMan JVM on JConsole you need to add the following command line option to QMan launcher : -Dcom.sun.management.jmxremote

4) Deploying Q-Man on JBoss 
QMan comes with a servlet that can be deployed in any application server. In the following we show how to deploy the qman servlet within JBoss application server.

4.1) PREREQUISITES 
You mus install JBoss:

- Download the latest stable version from: http://www.jboss.org/jbossas/downloads/
- Unzip the download archive in <jboss-home>

4.2) Deploying 
First you need to copy the provided qman.war in <jboss-home>/server/default/deploy/ (note that you can use another server configuration like for example minimal)

Then run JBoss:

Add the following option-Djboss.platform.mbeanserver to JAVA_OPTS (for example: export JAVA_OPTS=-Djboss.platform.mbeanserver)
Execute <jboss-home>/binrun.sh (or run.bat on a windows platform)
Notes:

If you wish to configure QMan via a configuration file so QMan establishes a connection with one or several broker at starting time then add the options -Dqman-config=myconfigFile.xml to JAVA_OPTS.
When Qpid is built form source, the war archive qman.war is located in qpid/java/build/management/client/servlet

Enjoy!


