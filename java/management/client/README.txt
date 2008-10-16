
*** FEATURES INCLUDED ***

- Content indication response message ('i') handler;
- Content indication response message ('c') handler;
- Schema response ('s') handler;
- Optional properties management;
- Properties validation against using constraints defined in the schema (max, min, maxlen);
- Schema-hash / class-hash management. QMan is able to work with classes having different schema inside the same package.
- Method invocation (see open issue below for this point) with arguments validation using constraints define in the schema;
- Graceful shutdown (At shutdown all management clients are shutdown and corresponding resources are released)
- Connection Pool : for each connected broker you can define (using configuration) a dedicated connecton pool. Properties of connection pool are : max capacity, initial capacity, max wait timeout. If you want for example to open all connections at startup set the initial capacity = max capacity.
- Each class is responsible to request and build its own schema. So, for example, if a runtime, the remote broker adds a new class and sends instrumentation or configuration data, a corresponding (empty) class definition will be created on QMan; immediately after that, the just created class will request its schema.

*** OPEN POINTS ***

1) Logging should be better : I mean, all messages should be prefixed with an id like <QMAN-xxxxxx>. At the moment not all messages are in this format.
2) Code used for sending messages should be better (see QpidClass.schemaRequest() and QpidClass.methodRequest());
3) Overloaded methods : At the moment each method is defined using its name as identifier and that thing doesn't work for overloaded methods...
4) Default handlers (opcode handlers) must be hard-coded. Only add-on handler must be configurable. At the moment you can add & remove handler using configuration but in this way it's possible to remove the default handlers from the configuration and therefore QMan won't work at all!
5) Method invocation : there is an echo() method on broker object instance that is throwing an exception while getting the response. It's strange because other method invocations are working (solicitAck(), close(), purge()). I'm working on that...
5) Return values : are not handled at the moment : command messages must be sent synchronously in order to get the response message and pack the return value(s) in a composite structure used a return object; At the moment the corresponding handler for 'm' message is simply log (debug level) the content of the message, including status text and status code.
6) It's not clear how to represent events on QMan (Is it useful?)
7) Offline testcase must be made for all QMan features. There's still a lot of code duplication too :(
8) Online testcase are missing.
9) Performance test cases (using something like JPerf) are missing.
10) ManagementDecoder & ManagementEncoder : I don't like very much those classes. For my opinionthe additional behaviour should be added on the
corresponding BBDecoder & BBEncoder.

*** Configuration file (org.apache.qpid.management.config.xml) ****

1) RESPONSE MESSAGE HANDLERS

An handler is responsible to handle a message response with a specific opcode. You can configure handlers under the /configuration/message-handlers.
First, you can configure an handler for response messages coming from management queue and / or method-reply queue.
The first category can be defined under the /configuration/message-handlers/method-reply-queue while the second one under /configuration/message-handlers/management-queue.
An handler is configured defining two parameters

<opcode>x</opcode> --> the opcode
<class-name>xxx.xxx.xx.xxx</class-name> --> the implementation class

2) TYPE MAPPINGS
A type mapping declares a mapping between a code (defined in management specification) and a concrete implementor of org.apache.qpid.management.domain.model.type.Type.
At the moment concrete implementors are:

org.apache.qpid.management.domain.model.type.AbsTime.java
org.apache.qpid.management.domain.model.type.Binary.java
org.apache.qpid.management.domain.model.type.Boolean.java
org.apache.qpid.management.domain.model.type.DeltaTime.java
org.apache.qpid.management.domain.model.type.Map.java
org.apache.qpid.management.domain.model.type.ObjectReference.java
org.apache.qpid.management.domain.model.type.Str16.java
org.apache.qpid.management.domain.model.type.Str8.java
org.apache.qpid.management.domain.model.type.Type.java
org.apache.qpid.management.domain.model.type.Uint16.java
org.apache.qpid.management.domain.model.type.Uint32.java
org.apache.qpid.management.domain.model.type.Uint64.java
org.apache.qpid.management.domain.model.type.Uint8.java
org.apache.qpid.management.domain.model.type.Uuid.java

Anyway a type is defined under the /configuration/type-mappings/mapping using specifying properties:

<mapping>
<code>x</code> --> as defined in the management specification
<class-name>xx.xxx.xxx.xx.x</class-name> --> a concrete implementor of org.apache.qpid.management.domain.model.type.Type (see above)
</mapping>

3) ACCESS MODE MAPPINGS
A mapping between a code and a Access mode as defined on management specification.
It is configured under /configuration/access-mode-mappings/mapping using these properties :

<mapping>
<code>x</code> --> as defined in the management specification
<value>x</class-name> --> RC,RW,RO
<mapping>

4) BROKERS
QMan is able to connect with one or more brokers. In order to do that each broker is configured under a dedicated /configuraton/brokers/broker using the following properties :

<broker>
<host>192.168.148.131</host> --> host name
<port>5672</port> --> port
<virtual-host>test</virtual-host> --> virtual host
<user>pippo</user> --> username
<password>pluto</password> --> password
<max-pool-capacity>4</max-pool-capacity> --> connection pool max capacity
<initial-pool-capacity>4</initial-pool-capacity> --> connetion pool initial capacity
<max-wait-timeout>-1</max-wait-timeout> --> wait time timeout (-1 stands for forever)
<broker>

*** DEPENDENCIES ***
qpid-common-incubating-M3.jar (already part of qpid)
qpid-client-incubating-M3.jar (already part of qpid)
slf4j-api-1.4.0.jar (already part of qpid)
slf4j-log4j12-1.4.0.jar (already part of qpid)
mina-filter-ssl-1.0.1.jar (already part of qpid)
mina-core-1.0.1.jar (already part of qpid)
log4j-1.2.12.jar (already part of qpid)
commons-pool-1.4.jar (not part of qpid but attached to this post)


*** PREREQUISITES ****

You should have in your classpath a log4j.xml configuration file with a category defined as this :

<category name="org.apache.qpid.management">
<priority value="INFO"/>
</category>

it should be better if output is redirected to a file using a FileAppender.

Now after set the classpath with the mentioned dependencies run :

> java -Dcom.sun.management.jmxremote org.apache.qpid.management.domain.services.QMan

If you open the jconsole ($JAVA_HOME/bin/jconsole) you will be able to see (under a Q-MAN domain) all the objects of the connected broker(s) as MBeans.
