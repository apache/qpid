
Documentation
--------------
All of our user documentation for the Qpid Java components can be accessed on our wiki at:

http://cwiki.apache.org/confluence/display/qpid/Qpid+Java+Documentation

This includes a Getting Started Guide and FAQ as well as detailed developer documentation.
However, here's a VERY quick guide to running the installed Qpid broker, once you have installed it somewhere !


Running the Broker
------------------

To run the broker, set the QPID_HOME environment variable to
distribution directory and add $QPID_HOME/bin to your PATH. Then run
the qpid-server shell script or qpid-server.bat batch file to start
the broker. By default, the broker will use $QPID_HOME/etc to find
the configuration files. You can supply a custom configuration using
the -c argument.

For example:

qpid-server -c ~/etc/config.xml

You can get a list of all command line arguments by using the -h argument.


Developing
----------

In order to build Qpid you need Ant 1.6.5. Use ant -p to list the
available targets. The default ant target, build, creates a working
development-mode distribution in the build directory. To run the
scripts in build/bin set QPID_HOME to the build directory and put
${QPID_HOME}/bin on your PATH. The scripts in that directory include
the standard ones in the distribution and a number of testing scripts.


Running Tests
-------------

The simplest test to ensure everything is working is the "service
request reply" test. This involves one client that is known as a
"service provider" and it listens on a well-known queue for
requests. Another client, known as the "service requester" creates a
private (temporary) response queue, creates a message with the private
response queue set as the "reply to" field and then publishes the
message to the well known service queue. The test allows you to time
how long it takes to send messages and receive the response back. It
also allows varying of the message size.

You must start the service provider first:

serviceProvidingClient.sh nop host:port

where host:port is the host and port you are running the broker
on.

To run the service requester:

serviceRequestingClient.sh nop host:post <count> <bytes>

This requests <count> messages, each of size <bytes>. After
receiving all the messages the client outputs the rate it achieved.

A more realistic test is the "headers test", which tests the
performance of routing messages based on message headers to a
configurable number of clients (e.g. 50). A publisher sends 10000
messages to each client and waits to receive a message from each
client when it has received all the messages.

You run the listener processes first:

run_many.sh 10 header "headersListener.sh -host 10.0.0.1 -port 5672"

In this command, the first argument means start 10 processes, the
second is just a name use in the log files generated and the third
argument is the command to run. In this case it runs another shell
script but it could be anything.

Then run the publisher process:

headersPublisher.sh -host 10.0.0.1 -port 5672 10000 10

The last two arguments are: the number of messages to send to each
client, and the number of clients.

Note that before starting the publisher you should wait about 30
seconds to ensure all the clients are registered with the broker (you
can see this from the broker output). Otherwise the numbers will be
slightly skewed.

A third useful test, which can easily be ported to other JMS
implementations is the "topic test". It does the same as the headers
test but using a standard topic (e.g. pub sub).

To run the listeners:

run_many.sh 10 topic "topicListener.sh -host 10.0.0.1 -port 5672"

and to run the publisher:

topicPublisher.sh -host 10.0.0.1 -port 5672 -clients 10 -messages 10000
