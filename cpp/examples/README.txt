= Qpid C++ Examples =

This directory contains example C++ programs for Apache Qpid. They are
based on the 0-10 version of the AMQP specification (see www.amqp.org for
details). A short description of each example follows.

= Messaging API Examples = 

Qpid now uses a new, simpler API called the Messaging API. The
examples that use this API are in the cpp/examples/messaging
directory. If you are new to Qpid, we encourage you to start with
these examples.

== hello_world.cpp ==

hello_world.cpp is a simple example that creates a Sender and a
Receiver for the same address, sends a message to the address, reads
it, and prints it:

$ ./hello_world
Hello world!

By default, this program connects to a broker running on
localhost:5672. You can specify a host and port explicitly on the
command line:

$ ./hello_world localhost:5673

== drain.cpp, spout.cpp ==

drain and spout provide many features for sending or receiving
messages. Use --help to see all available options.

To learn how to specify various kinds of addresses using these
programs, read the chapter on Addresses here:

  http://qpid.apache.org/books/0.7/Programming-In-Apache-Qpid/html/

If you do not have qpid-config installed, you can create queues
explicitly as part of an address. For instance, to create a queue
named 'hello-world' and send a message to it, you can use spout as
follows:

$ ./spout "hello-world ; { create: always }"

Now you can read the message from this queue using drain:

$ ./drain hello-world

Message(properties={spout-id:c877e622-d57b-4df2-bf3e-6014c68da0ea:0}, content='')


== map_sender.cpp, map_receiver.cpp ==

These examples show how to send and receive typed data. Send the data
with map_sender, then receive it with map_receiver:

$ ./map_sender
$ ./map_receiver
{colours:[red, green, white], id:987654321, name:Widget, percent:0.98999999999999999, uuid:34935b4a-fd55-4212-9c41-e5baebc6e7a5}


== hello-xml.cpp ==

This example shows how to route XML messages with an XQuery using an
XML Exchange.

$ ./hello_xml
<weather><station>Raleigh-Durham International Airport (KRDU)</station><wind_speed_mph>16</wind_speed_mph><temperature_f>70</temperature_f><dewpoint>35</dewpoint></weather>


= Examples that use the Legacy API =

The following examples use an older API that is now deprecated. If you
are new to Qpid, we encourage you to use the Messaging API
instead. These examples may not be part of future distributions.

Please note that by default these examples attempt to connect to a Qpid
broker running on the local host (127.0.0.1) at the standard AMQP port (5672).
It is possible to instruct the examples to connect to an alternate broker
host and port by specifying the host name/address and port number as arguments
to the programs. For example, to have the declare_queues program connect to a
broker running on host1, port 9999, run the following command:

On Linux: 
  # ./declare_queues host1 9999

On Windows:
  C:\Program Files\qpidc-0.7\examples\direct> declare_queues host1 9999

The qpid C++ broker executable is named qpidd on Linux and qpidd.exe
on Windows. The default install locations are:
- Linux: /usr/sbin
- Windows: C:\Program Files\qpidc-0.7\bin

In a C++ source distribution the broker is located in the src subdirectory
(generally, from this examples directory, ../src).


== Direct ==

This example shows how to create Point-to-Point applications using Qpid. This
example contains three components.

 1. declare_queues
    This will bind a queue to the amq.direct exchange, so that the messages
    sent to the amq.direct exchange with a given routing key (routing_key) are 
    delivered to a specific queue (message_queue).

 2. direct_producer
    Publishes messages to the amq.direct exchange using the given routing key
    (routing_key) discussed above.

 3. listener
    Uses a message listener to listen for messages from a specific queue
    (message_queue) as discussed above.

In order to run this example,

On Linux:
  # ./declare_queues
  # ./direct_producer
  # ./listener

On Windows:
  C:\Program Files\qpidc-0.7\examples\direct> declare_queues
  C:\Program Files\qpidc-0.7\examples\direct> direct_producer
  C:\Program Files\qpidc-0.7\examples\direct> listener

Note that there is no requirement for the listener to be running before the
messages are published. The messages are stored in the queue until consumed
by the listener.

== Fanout ==

This example shows how to create Fanout exchange applications using Qpid.
This example has two components. Unlike the Direct example, the Fanout exchange
does not need a routing key to be specified.

 1. fanout_producer
    Publishes a message to the amq.fanout exchange, without using a routing key.

 2. listener
    Uses a message listener to listen for messages from the amq.fanout exchange.


Note that unlike the Direct example, it is necessary to start the listener
before the messages are published. The fanout exchange does not hold messages
in a queue. Therefore, it is recommended that the two parts of the example be
run in separate windows.

In order to run this example:

On Linux:
  # ./listener

  # ./fanout_producer

On Windows:
  C:\Program Files\qpidc-0.7\examples\fanout> listener

  C:\Program Files\qpidc-0.7\examples\direct> fanout_producer

== Publisher/Subscriber ==

This example demonstrates the ability to create topic Publishers and
Subscribers using Qpid. This example has two components.

 1. topic_publisher
    This application is used to publish messages to the amq.topic exchange
    using multipart routing keys, usa.weather, europe.weather, usa.news and
    europe.news.

 2. topic_listener
    This application is used to subscribe to several private queues, such as
    usa, europe, weather and news. In this program, each private queue created
    is bound to the amq.topic exchange using bindings that match the
    corresponding parts of the multipart routing keys. For example, subscribing
    to #.news will retrieve news irrespective of destination.

This example also shows the use of the 'control' routing key which is used by
control messages.

Due to this example's design, the topic_listener must be running before
starting the topic_publisher. Therefore, it is recommended that the two parts
of the example be run in separate windows.

In order to run this example,
  
On Linux:
  # ./topic_listener

  # ./topic_publisher

On Windows:
  C:\Program Files\qpidc-0.7\examples\pub-sub> topic_listener

  C:\Program Files\qpidc-0.7\examples\pub-sub> topic_publisher

== Request/Response ==

This example shows a simple server that will accept strings from a client,
convert them to upper case, and send them back to the client. This example
has two components.

 1. client
    This sends lines of poetry to the server.

 2. server
    This is a simple service that will convert incoming strings to upper case
    and send the result to amq.direct exchange on which the client listens.
    It uses the request's reply_to property as the response's routing key.

In order to run this example,

On Linux:
  # ./server
  # ./client

On Windows:
  C:\Program Files\qpidc-0.7\examples\request-response> server
  C:\Program Files\qpidc-0.7\examples\request-response> client


