/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.example.jmsexample.pubsub;

import org.apache.qpid.example.jmsexample.BaseExample;

import javax.jms.*;

/**
 * The example creates a MessageConsumer on the specified
 * Topic and uses a MessageListener with this MessageConsumer
 * in order to enable asynchronous delivery.
 */
public class Listener extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "Listener";

    /* An object to synchronize on. */
    private final static Object _lock = new Object();

    /* A boolean to indicate a clean finish. */
    private static int _finished = 0;

    /* A boolean to indicate an unsuccesful finish. */
    private static boolean _failed = false;

    /**
     * Create an Listener client.
     *
     * @param args Command line arguments.
     */
    public Listener(String[] args)
    {
        super(CLASS, args);
    }

    /**
     * Run the message consumer example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        Listener listener = new Listener(args);
        listener.runTest();
    }

    /**
     * Start the example.
     */
    private void runTest()
    {
        try
        {
            // Declare the connection
            TopicConnection connection = (TopicConnection) getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(
                    CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses
            // the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            // lookup the topics usa
            Topic topic = (Topic) getInitialContext().lookup("usa");
            // Create a Message Subscriber
            System.out.println(CLASS + ": Creating a Message Subscriber");
            TopicSubscriber messageSubscriber = session.createSubscriber(topic);
            // Set a message listener on the messageConsumer
            messageSubscriber.setMessageListener(new MyMessageListener("usa"));

            // lookup the topics world.usa.news
            topic = (Topic) getInitialContext().lookup("europe");
            // Create a Message Subscriber
            System.out.println(CLASS + ": Creating a Message Subscriber");
            messageSubscriber = session.createSubscriber(topic);
            // Set a message listener on the messageConsumer
            messageSubscriber.setMessageListener(new MyMessageListener("europe"));

            // lookup the topics world.europw
            topic = (Topic) getInitialContext().lookup("news");
            // Create a Message Subscriber
            System.out.println(CLASS + ": Creating a Message Subscriber");
            messageSubscriber = session.createSubscriber(topic);
            // Set a message listener on the messageConsumer
            messageSubscriber.setMessageListener(new MyMessageListener("news"));

            // lookup the topics world.europw
            topic = (Topic) getInitialContext().lookup("weather");
            // Create a Message Subscriber
            System.out.println(CLASS + ": Creating a Message Subscriber");
            messageSubscriber = session.createSubscriber(topic);
            // Set a message listener on the messageConsumer
            messageSubscriber.setMessageListener(new MyMessageListener("weather"));

            // Now the messageConsumer is set up we can start the connection
            System.out.println(CLASS + ": Starting connection so MessageConsumer can receive messages");
            connection.start();

            // Wait for the messageConsumer to have received all the messages it needs
            synchronized (_lock)
            {
                while (_finished < 3 && !_failed)
                {
                    _lock.wait();
                }
            }

            // If the MessageListener abruptly failed (probably due to receiving a non-text message)
            if (_failed)
            {
                System.out.println(CLASS + ": This sample failed as it received unexpected messages");
            }

            // Close the connection to the server
            System.out.println(CLASS + ": Closing connection");
            connection.close();

            // Close the JNDI reference
            System.out.println(CLASS + ": Closing JNDI context");
            getInitialContext().close();
        }
        catch (Exception exp)
        {
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }

    private class MyMessageListener implements MessageListener
    {
        /* The number of messages processed. */
        private int _messageCount = 0;

        /* The topic this subscriber is subscribing  to */
        private String _topicName;

        public MyMessageListener(String topicName)
        {
            _topicName = topicName;
        }

        /**
         * This method is required by the <CODE>MessageListener</CODE> interface. It
         * will be invoked when messages are available.
         * After receiving the final message it releases a lock so that the
         * main program may continue.
         *
         * @param message The message.
         */
        public void onMessage(Message message)
        {
            try
            {
                // Increment the number of messages that have been received
                _messageCount = _messageCount + 1;
                // Print out the details of the just received message
                System.out
                        .print(_topicName + ":  message received: " + _messageCount + " " + message.getJMSMessageID());
                System.out.println(" - contents = " + ((TextMessage) message).getText());
                // If this is the total number of messages required
                if (((TextMessage) message).getText().equals("That's all, folks!"))
                {
                    System.out.println("Shutting down listener for " + _topicName);
                    synchronized (_lock)
                    {
                        _finished++;
                        _lock.notifyAll();
                    }
                }
            }
            catch (JMSException exp)
            {
                System.out.println(CLASS + ": Caught an exception handling a received message");
                exp.printStackTrace();
                synchronized (_lock)
                {
                    _failed = true;
                    _lock.notifyAll();
                }
            }
        }
    }
}
