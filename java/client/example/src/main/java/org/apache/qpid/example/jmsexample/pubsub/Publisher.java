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
package org.apache.qpid.example.jmsexample.pubsub;

import org.apache.qpid.example.jmsexample.BaseExample;

import javax.jms.*;

/**
 * Publish messages to topics
 */
public class Publisher extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "Publisher";

    /**
     * Create a Publisher client.
     *
     * @param args Command line arguments.
     */
    public Publisher(String[] args)
    {
        super(CLASS, args);
    }

    /**
     * Run the message publisher example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        Publisher publisher = new Publisher(args);
        publisher.runTest();
    }

    private void runTest()
    {
        try
        {
            // Declare the connection
            TopicConnection connection = (TopicConnection) getConnection();

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a Message
            TextMessage message;
            System.out.println(CLASS + ": Creating a TestMessage to send to the topics");
            message = session.createTextMessage();

            // lookup the topics .usa.weather
            Topic topic = (Topic) getInitialContext().lookup("usa.weather");
            message.setStringProperty("topicName", "usa.weather");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            TopicPublisher messagePublisher = session.createPublisher(topic);
            publishMessages(message, messagePublisher);

            // lookup the topics usa.news
            topic = (Topic) getInitialContext().lookup("usa.news");
            message.setStringProperty("topicName", "usa.news");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            publishMessages(message, messagePublisher);

            // lookup the topics europe.weather
            topic = (Topic) getInitialContext().lookup("europe.weather");
            message.setStringProperty("topicName", "europe.weather");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            publishMessages(message, messagePublisher);

            // lookup the topics europe.news
            topic = (Topic) getInitialContext().lookup("europe.news");
            message.setStringProperty("topicName", "europe.news");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            publishMessages(message, messagePublisher);

            // send the final message
            message.setText("That's all, folks!");
            topic = (Topic) getInitialContext().lookup("news");
            message.setStringProperty("topicName", "news");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            messagePublisher
                    .send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            topic = (Topic) getInitialContext().lookup("weather");
            message.setStringProperty("topicName", "weather");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            messagePublisher
                    .send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            topic = (Topic) getInitialContext().lookup("europe");
            message.setStringProperty("topicName", "europe");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            messagePublisher
                    .send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            topic = (Topic) getInitialContext().lookup("usa");
            message.setStringProperty("topicName", "usa");
            // Create a Message Publisher
            System.out.println(CLASS + ": Creating a Message Publisherr");
            messagePublisher = session.createPublisher(topic);
            messagePublisher
                    .send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

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

    private void publishMessages(TextMessage message, TopicPublisher messagePublisher) throws JMSException
    {
        // Loop to publish the requested number of messages.
        for (int i = 1; i < getNumberMessages() + 1; i++)
        {
            // NOTE: We have NOT HAD TO START THE CONNECTION TO BEGIN SENDING  messages,
            // this is different to the consumer end as a CONSUMERS CONNECTIONS MUST BE STARTED BEFORE RECEIVING.
            message.setText("Message " + i);
            System.out.println(CLASS + ": Sending message: " + i);
            messagePublisher
                    .send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
        }
    }
}
