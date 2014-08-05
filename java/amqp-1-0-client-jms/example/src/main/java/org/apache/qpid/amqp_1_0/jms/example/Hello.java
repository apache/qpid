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
package org.apache.qpid.amqp_1_0.jms.example;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

public class Hello
{
    public Hello()
    {
    }

    public static void main(String[] args)
    {
        Hello hello = new Hello();
        hello.runExample();
    }

    private void runExample()
    {
        try
        {
            // Read the hello.properties JNDI properties file and use contents to create the InitialContext.
            Properties properties = new Properties();
            properties.load(getClass().getResourceAsStream("hello.properties"));
            Context context = new InitialContext(properties);
            // Alternatively, JNDI information can be supplied by setting the "java.naming.factory.initial"
            // system property to value "org.apache.qpid.amqp_1_0.jms.jndi.PropertiesFileInitialContextFactory"
            // and setting the "java.naming.provider.url" system property as a URL to a properties file.

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("localhost");
            Connection connection = connectionFactory.createConnection();

            Session producersession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = (Queue) context.lookup("queue");

            Session consumerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = consumerSession.createConsumer(queue);

            final CountDownLatch latch = new CountDownLatch(1);
            messageConsumer.setMessageListener(new MessageListener()
            {
                public void onMessage(final Message message)
                {
                    try
                    {
                        if(message instanceof TextMessage)
                        {
                            System.out.println(((TextMessage) message).getText());
                        }
                        else
                        {
                            System.out.println("Received enexpected message type: " + message.getClass().getName());
                        }

                        latch.countDown();
                    }
                    catch (JMSException e)
                    {
                        System.out.println("Caught exception in onMessage(): " + e.getMessage());
                    }
                }
            });

            connection.start();

            MessageProducer messageProducer = producersession.createProducer(queue);
            TextMessage message = producersession.createTextMessage("Hello world!");

            messageProducer.send(message);

            int delay = 5;
            if(!latch.await(delay, TimeUnit.SECONDS))
            {
                System.out.println("Waited " + delay + "sec but no message recieved.");
            }

            connection.close();
            context.close();
        }
        catch (Exception exp)
        {
            System.out.println("Caught exception: " + exp.getMessage());
            exp.printStackTrace();
        }
    }
}
