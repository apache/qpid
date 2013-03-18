The qpid-perftests distributed JMS test framework
=================================================

This folder contains the distributed test (aka Perf Test) framework written for
testing the performance of a JMS provider. Although it was written for the
purpose of testing Qpid, it can be used to test the performance of any JMS
provider with minimal configuration changes.

This document explains how to use the framework.


How it works
------------

First, you need to run a message broker. This can be Qpid, ActiveMQ etc. All
messages are sent using the JMS API.

Then run a Perf Test Controller, providing the details of the test in either or
a JSON or Javascript file. This specifies details about the messages to send,
how many connections and sessions to use etc. There are a lot of options
available - see the .js and .json files under this folder for examples.

Now run one or more Perf Test Client processes. These will be responsible for
sending/receiving the messages once the test starts. For convenience, you can
instead configure the Controller to start clients in-process. The clients and
the controller communicate using queues on the message broker.

The test results are written to CSV files.

You can use the qpid-perftests-visualisation tool to create charts from the CSV files.

Example usage
-------------

The etc/ folder contains shell scripts that can be used to run the performance
tests and visualise the results.  It also contains sub-folders for test config
and chart definitions.

Instructions
------------

1. Extract the archive

2. cd into the etc/ folder

3. Start your JMS broker

4. To run the Controller and clients in a single process, run the following
command:

java -cp ".:../lib/*:/path/to/your-jms-client-jars/*" \
  -Dqpid.dest_syntax=BURL \
  org.apache.qpid.disttest.ControllerRunner \
  jndi-config=perftests-jndi.properties \
  test-config=/path/to/test-config.json \
  distributed=false

Note that the test-config parameter can point at either a JSON or Javascript
file, or at a directory (in which case all the .json and .js files in the
directory are used.

When the test is complete, the CSV files containing the results are written to
the current directory.


Running the clients in a separate process
-----------------------------------------

When using a large number of clients, you may get more representative
performance results if the clients are distributed among multiple processes,
potentially on multiple machines. To do this:

1. Run the Controller, providing distributed=true.

2. Run your clients (assuming you want to launch 10 logical clients in this
process):

java -cp ".:../lib/*:/path/to/your-jms-client-jars/*" \
  -Dqpid.dest_syntax=BURL \
  org.apache.qpid.disttest.ClientRunner \
  jndi-config=perftests-jndi.properties \
  number-of-clients=10


Caveats for non-Qpid JMS providers
----------------------------------

If you are not using the Qpid broker, you must create one or more queues before
running the test.  This is necessary because you can't use Qpid's API to create
queues on the broker. The queues are:

– The controller queue. You can specify the physical name of this in
etc/perftests-jndi.properties. This queue is used by the clients to register
with the Controller and to send results to it.
– the queue(s) used by your JSON test configuration (unless you have configured
a vendor-specific queue creator).

You must also override the Controller's default queue creator using the system
property qpid.disttest.queue.creator.class. Provide the class name of an
implementation of org.apache.qpid.disttest.jms.QueueCreator, or
org.apache.qpid.disttest.jms.NoOpQueueCreator if you are going to create and
delete the queues manually.

You can also omit the qpid.dest_syntax system property if your JMS provider is
not Qpid.
