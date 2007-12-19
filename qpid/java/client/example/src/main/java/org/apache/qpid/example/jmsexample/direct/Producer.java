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
package org.apache.qpid.example.jmsexample.direct;

import org.apache.qpid.example.jmsexample.common.BaseExample;

import javax.jms.*;

/**
 * Message producer example, sends message to a queue.
 */
public class Producer extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "Producer";

    /* The queue name  */
    private String _queueName;

    /**
     * Create a Producer client.
     * @param args Command line arguments.
     */
    public Producer (String[] args)
    {
         super(CLASS, args);
        _queueName = _argProcessor.getStringArgument("-queueName");
    }

    /**
     * Run the message producer example.
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-queueName", "Queue name");
         _defaults.put("-queueName", "message_queue");
        Producer producer = new Producer(args);
        producer.runTest();
    }

    private void runTest()
    {
        try
        {
            // Declare the connection
            Connection connection = getConnection();

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // lookup the queue
            Queue   destination = session.createQueue(_queueName);

            // Create a Message producer
            System.out.println(CLASS + ": Creating a Message Producer");
            MessageProducer messageProducer = session.createProducer(destination);

            // Create a Message
            TextMessage message;
            System.out.println(CLASS + ": Creating a TestMessage to send to the destination");

            // Loop to publish the requested number of messages.
            for (int i = 1; i < getNumberMessages() + 1; i++)
            {
                // NOTE: We have NOT HAD TO START THE CONNECTION TO BEGIN SENDING  messages,
                // this is different to the consumer end as a CONSUMERS CONNECTIONS MUST BE STARTED BEFORE RECEIVING.
                message = session.createTextMessage("Message " + i);
                System.out.println(CLASS + ": Sending message: " + i);
                messageProducer.send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);             
            }

            // And send a final message to indicate termination.
            message = session.createTextMessage("That's all, folks!");            
            messageProducer.send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            // Close the connection to the broker
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
