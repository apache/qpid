/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
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
 *
 */
package  org.apache.qpid.example.jmsexample.direct;

import org.apache.qpid.example.jmsexample.BaseExample;

import javax.jms.*;

/**
 * The example creates a MessageConsumer on the specified
 * Queue which is used to synchronously consume messages.
 */
public class Consumer extends BaseExample
{
    /**
     * Used in log output.
     */
    private static final String CLASS = "Consumer";

    /* The queue name  */
    private String _queueName;

    /**
     * Create a Consumer client.
     *
     * @param args Command line arguments.
     */
    public Consumer(String[] args)
    {
        super(CLASS, args);
        _queueName = _argProcessor.getStringArgument("-queueName");
    }

    /**
     * Run the message consumer example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-queueName", "Queue name");
        _defaults.put("-queueName", "message_queue");
        Consumer syncConsumer = new Consumer(args);
        syncConsumer.runTest();
    }

    /**
     * Start the example.
     */
    private void runTest()
    {
        try
        {
            // lookup the queue
            Queue destination = (Queue) getInitialContext().lookup(_queueName);

            // Declare the connection
            Connection connection = getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(
                    CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a MessageConsumer
            System.out.println(CLASS + ": Creating a MessageConsumer");
            MessageConsumer messageConsumer = session.createConsumer(destination);

            // Now the messageConsumer is set up we can start the connection
            System.out.println(CLASS + ": Starting connection so MessageConsumer can receive messages");
            connection.start();

            // Cycle round until all the messages are consumed.
            Message message;
            boolean end = false;
            while (!end)
            {
                System.out.println(CLASS + ": Receiving a message");
                message = messageConsumer.receive();
                if (message instanceof TextMessage)
                {
                    System.out.println(" - contents = " + ((TextMessage) message).getText());
                    if (((TextMessage) message).getText().equals("That's all, folks!"))
                    {
                        System.out.println("Received final message for " + _queueName);
                        end = true;
                    }
                }
                else
                {
                    System.out.println(" not text message");
                }
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
}
