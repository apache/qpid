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

package org.apache.qpid.example;

import java.io.InputStream;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.configuration.ClientProperties;


public class Hello 
{

    public Hello() 
    {
    }

    public static void main(String[] args) throws Exception
    {
        System.setProperty(ClientProperties.AMQP_VERSION, "0-91");
        System.setProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, "0");
        System.setProperty(ClientProperties.DEST_SYNTAX, "BURL");

        Connection conn = new AMQConnection("127.0.0.1", 5672, "admin","admin", "client", "/");

        conn.start();

        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("queue");

        MessageConsumer consumer = session.createConsumer(destination);
        MessageProducer producer = session.createProducer(destination);

        for(int i = 0 ; i < 2 ; i ++)
        {
            TextMessage message = (TextMessage) consumer.receive(1000l);
            System.out.println(message == null ? "null" : message.getText());
        }
        for(int i = 0 ; i < 2 ; i ++)
        {
            TextMessage message = session.createTextMessage("Hello " + i);
            producer.send(message);
        }

        for(int i = 0 ; i < 2 ; i ++)
        {
            TextMessage message = (TextMessage) consumer.receive(1000l);
            System.out.println(message == null ? "null" : message.getText());
        }
    }

    private void runTest() 
    {
        try (InputStream resourceAsStream = this.getClass().getResourceAsStream("hello.properties"))
        {
            Properties properties = new Properties();
            properties.load(resourceAsStream);
            Context context = new InitialContext(properties);

            ConnectionFactory connectionFactory = (ConnectionFactory) context.lookup("qpidConnectionfactory");
            Connection connection = connectionFactory.createConnection();
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = (Destination) context.lookup("topicExchange");

            MessageProducer messageProducer = session.createProducer(destination);
            MessageConsumer messageConsumer = session.createConsumer(destination);

            TextMessage message = session.createTextMessage("Hello world!");
            messageProducer.send(message);

            message = (TextMessage)messageConsumer.receive();
            System.out.println(message.getText());

            connection.close();
            context.close();
        }
        catch (Exception exp) 
        {
            exp.printStackTrace();
        }
    }
}
