In order to use the runSample script, you are required to set two environment
variables, QPID_HOME and QPID_SAMPLE. If not the default values will be used.

QPID_HOME
---------
This is the directory that contains the QPID distribution. If you are running the Qpid
Java broker on the same machine as the examples, you have already set QPID_HOME to this 
directory.

default: /usr/share/java/

QPID_SAMPLE
-----------

This is the examples directory, which is the parent directory of the
'java' directory in which you find 'runSample.sh'

(Ex:- $QPID_SRC_HOME/java/client/example/src/main)

default: $PWD

Note: you must have write privileges to this directory in order to run
the examples.


Running the Direct Examples
===========================

To run these programs, do the following:

   1. Make sure that a qpidd broker is running:

      $ ps -eaf | grep qpidd

      If a broker is running, you should see the qpidd process in the
      output of the above command.

   2. In the java directory, use runSample.sh to run the Consumer
      program:

      $  ./runSample.sh org.apache.qpid.example.jmsexample.direct.Consumer
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Consumer: Setting an ExceptionListener on the connection as sample uses a MessageConsumer
      Consumer: Creating a non-transacted, auto-acknowledged session
      Consumer: Creating a MessageConsumer
      Consumer: Starting connection so MessageConsumer can receive messages

   3. In a separate window, use runSample.sh to run the Producer
      program:

      $  ./runSample.sh org.apache.qpid.example.jmsexample.direct.Producer
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Producer: Creating a non-transacted, auto-acknowledged session
      Producer: Creating a Message Producer
      Producer: Creating a TestMessage to send to the destination
      Producer: Sending message: 1
      Producer: Sending message: 2
      Producer: Sending message: 3
      Producer: Sending message: 4
      Producer: Sending message: 5
      Producer: Sending message: 6
      Producer: Sending message: 7
      Producer: Sending message: 8
      Producer: Sending message: 9
      Producer: Sending message: 10
      Producer: Closing connection
      Producer: Closing JNDI context

   4. Now go back to the window where the Consumer program is
      running. You should see the following output:

      Consumer: Received  message:  Message 1
      Consumer: Received  message:  Message 2
      Consumer: Received  message:  Message 3
      Consumer: Received  message:  Message 4
      Consumer: Received  message:  Message 5
      Consumer: Received  message:  Message 6
      Consumer: Received  message:  Message 7
      Consumer: Received  message:  Message 8
      Consumer: Received  message:  Message 9
      Consumer: Received  message:  Message 10
      Consumer: Received final message That's all, folks!
      Consumer: Closing connection
      Consumer: Closing JNDI context



Running the Fanout Examples
===========================

To run these programs, do the following:

   1. Make sure that a qpidd broker is running:

      $ ps -eaf | grep qpidd

      If a broker is running, you should see the qpidd process in the
      output of the above command.

   2. In the java directory, use runSample.sh to run the Consumer or
   Listener program, specifying a unique queue name, which must be
   “fanoutQueue1”, “fanoutQueue2”, or “fanoutQueue3”:

      $ ./runSample.sh org.apache.qpid.example.jmsexample.fanout.Consumer fanoutQueue1
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Consumer: Setting an ExceptionListener on the connection as sample uses a MessageConsumer
      Consumer: Creating a non-transacted, auto-acknowledged session
      Consumer: Creating a MessageConsumer
      Consumer: Starting connection so MessageConsumer can receive messages

      You can do this in up to three windows, specifying a different
      name for each queue.

   3. In a separate window, use runSample.sh to run the Producer
   program:

      $  ./runSample.sh org.apache.qpid.example.jmsexample.fanout.Producer
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Producer: Creating a non-transacted, auto-acknowledged session
      Producer: Creating a Message Producer
      Producer: Creating a TestMessage to send to the destination
      Producer: Sending message: 1
      Producer: Sending message: 2
      Producer: Sending message: 3
      Producer: Sending message: 4
      Producer: Sending message: 5
      Producer: Sending message: 6
      Producer: Sending message: 7
      Producer: Sending message: 8
      Producer: Sending message: 9
      Producer: Sending message: 10
      Producer: Closing connection
      Producer: Closing JNDI context

   4. Now go back to the window where the Listener program is
   running. You should see output like this:

      Consumer: Received  message:  Message 1
      Consumer: Received  message:  Message 2
      Consumer: Received  message:  Message 3
      Consumer: Received  message:  Message 4
      Consumer: Received  message:  Message 5
      Consumer: Received  message:  Message 6
      Consumer: Received  message:  Message 7
      Consumer: Received  message:  Message 8
      Consumer: Received  message:  Message 9
      Consumer: Received  message:  Message 10
      Consumer: Received final message That's all, folks!
      Consumer: Closing connection
      Consumer: Closing JNDI context


Running the Publish/Subscribe Examples
======================================

To run these programs, do the following:

   1. Make sure that a qpidd broker is running:

      $ ps -eaf | grep qpidd

      If a broker is running, you should see the qpidd process in the
      output of the above command.

   2. In the java directory, use runSample.sh to run the Listener
      program:

      $  ./runSample.sh org.apache.qpid.example.jmsexample.pubsub.Listener
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Listener: Setting an ExceptionListener on the connection as sample uses a TopicSubscriber
      Listener: Creating a non-transacted, auto-acknowledged session
      Listener: Creating a Message Subscriber for topic usa
      Listener: Creating a Message Subscriber for topic europe
      Listener: Creating a Message Subscriber for topic news
      Listener: Creating a Message Subscriber for topic weather
      Listener: Starting connection so TopicSubscriber can receive messages

   3. In a separate window, use runSample.sh to run the Publisher
      program:

      $  ./runSample.sh org.apache.qpid.example.jmsexample.pubsub.Publisher
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Publisher: Creating a non-transacted, auto-acknowledged session
      Publisher: Creating a TestMessage to send to the topics
      Publisher: Creating a Message Publisher for topic usa.weather
      Publisher: Sending message 1
      Publisher: Sending message 2
      Publisher: Sending message 3
      Publisher: Sending message 4
      Publisher: Sending message 5
      Publisher: Sending message 6
      Publisher: Creating a Message Publisher for topic usa.news
      Publisher: Sending message 1
      Publisher: Sending message 2
      Publisher: Sending message 3
      Publisher: Sending message 4
      Publisher: Sending message 5
      Publisher: Sending message 6
      Publisher: Creating a Message Publisher for topic europe.weather
      Publisher: Sending message 1
      Publisher: Sending message 2
      Publisher: Sending message 3
      Publisher: Sending message 4
      Publisher: Sending message 5
      Publisher: Sending message 6
      Publisher: Creating a Message Publisher for topic europe.news
      Publisher: Sending message 1
      Publisher: Sending message 2
      Publisher: Sending message 3
      Publisher: Sending message 4
      Publisher: Sending message 5
      Publisher: Sending message 6
      Publisher: Closing connection
      Publisher: Closing JNDI context

   4. Now go back to the window where the Listener program is
      running. You should see output like this:

      Listener: Received message for topic: usa: message 1
      Listener: Received message for topic: weather: message 1
      Listener: Received message for topic: usa: message 2
      Listener: Received message for topic: weather: message 2
      Listener: Received message for topic: usa: message 3
      Listener: Received message for topic: weather: message 3
      Listener: Received message for topic: usa: message 4
      Listener: Received message for topic: weather: message 4
      Listener: Received message for topic: usa: message 5
      Listener: Received message for topic: weather: message 5
      Listener: Received message for topic: usa: message 6
      Listener: Received message for topic: weather: message 6
      . . .
      Listener: Shutting down listener for news
      Listener: Shutting down listener for weather
      Listener: Shutting down listener for usa
      Listener: Shutting down listener for europe
      Listener: Closing connection
      Listener: Closing JNDI context


Running the Request/Response Examples
=====================================

To run these programs, do the following:

   1. Make sure that a qpidd broker is running:

      $ ps -eaf | grep qpidd

      If a broker is running, you should see the qpidd process in the output of the above command. 

   2. In the java directory, use runSample.sh to run the Server
      program:

      $ ./runSample.sh org.apache.qpid.example.jmsexample.requestResponse.Server
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Server: Setting an ExceptionListener on the connection as sample uses a MessageConsumer
      Server: Creating a non-transacted, auto-acknowledged session
      Server: Creating a MessageConsumer
      Server: Creating a MessageProducer
      Server: Starting connection so MessageConsumer can receive messages

   3. In a separate window, use runSample.sh to run the Client
      program:

      $ ./runSample.sh org.apache.qpid.example.jmsexample.requestResponse.Client
      Using QPID_HOME: /usr/share/java/
      Using QPID_SAMPLE: /usr/share/doc/rhm-0.3
      Client: Setting an ExceptionListener on the connection as sample uses a MessageConsumer
      Client: Creating a non-transacted, auto-acknowledged session
      Client: Creating a QueueRequestor
      Client: Starting connection
      Client:         Request Content= Twas brillig, and the slithy toves
      Client:         Response Content= TWAS BRILLIG, AND THE SLITHY TOVES
      Client:         Request Content= Did gire and gymble in the wabe.
      Client:         Response Content= DID GIRE AND GYMBLE IN THE WABE.
      Client:         Request Content= All mimsy were the borogroves,
      Client:         Response Content= ALL MIMSY WERE THE BOROGROVES,
      Client:         Request Content= And the mome raths outgrabe.
      Client:         Response Content= AND THE MOME RATHS OUTGRABE.
      Client: Closing connection
      Client: Closing JNDI context

