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
package org.apache.qpid.test.unit.basic;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.junit.Test;
import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import javax.jms.*;

/**
 * This is a slow test.
 */
public class MultipleConnectionTest
{
    public static String _connectionString = "vm://:1";

    private static class Receiver
    {
        private AMQConnection _connection;
        private Session[] _sessions;
        private MessageCounter[] _counters;

        Receiver(String broker, AMQDestination dest, int sessions) throws Exception
        {
            this(new AMQConnection(broker, "guest", "guest", randomize("Client"), "/test_path"), dest, sessions);
        }

        Receiver(AMQConnection connection, AMQDestination dest, int sessions) throws Exception
        {
            _connection = connection;
            _sessions = new AMQSession[sessions];
            _counters = new MessageCounter[sessions];
            for (int i = 0; i < sessions; i++)
            {
                _sessions[i] = _connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
                _counters[i] = new MessageCounter(_sessions[i].toString());
                _sessions[i].createConsumer(dest).setMessageListener(_counters[i]);
            }
            _connection.start();
        }

        void close() throws JMSException
        {
            _connection.close();
        }
    }

    private static class Publisher
    {
        private AMQConnection _connection;
        private Session _session;
        private MessageProducer _producer;

        Publisher(String broker, AMQDestination dest) throws Exception
        {
            this(new AMQConnection(broker, "guest", "guest", randomize("Client"), "/test_path"), dest);
        }

        Publisher(AMQConnection connection, AMQDestination dest) throws Exception
        {
            _connection = connection;
            _session = _connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
            _producer = _session.createProducer(dest);
        }

        void send(String msg) throws JMSException
        {
            _producer.send(_session.createTextMessage(msg));
        }

        void close() throws JMSException
        {
            _connection.close();
        }
    }

    private static class MessageCounter implements MessageListener
    {
        private final String _name;
        private int _count;

        MessageCounter(String name)
        {
            _name = name;
        }

        public synchronized void onMessage(Message message)
        {
            _count++;
            notifyAll();
        }

        synchronized boolean waitUntil(int expected, long maxWait) throws InterruptedException
        {
            long start = System.currentTimeMillis();
            long timeLeft = maxWait;
            do
            {
                wait(timeLeft);
                timeLeft = maxWait - timeSince(start);
            }
            while (expected > _count && timeLeft > 0);
            return expected <= _count;
        }

        private long timeSince(long start)
        {
            return System.currentTimeMillis() - start;
        }

        public synchronized String toString()
        {
            return _name + ": " + _count;
        }
    }

    private static void waitForCompletion(int expected, long wait, Receiver[] receivers) throws InterruptedException
    {
        for (int i = 0; i < receivers.length; i++)
        {
            waitForCompletion(expected, wait, receivers[i]._counters);
        }
    }

    private static void waitForCompletion(int expected, long wait, MessageCounter[] counters) throws InterruptedException
    {
        for (int i = 0; i < counters.length; i++)
        {
            if (!counters[i].waitUntil(expected, wait))
            {
                throw new RuntimeException("Expected: " + expected + " got " + counters[i]);
            }
            ;
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        String broker = argv.length > 0 ? argv[0] : "vm://:1";

        int connections = 7;
        int sessions = 2;

        MultipleConnectionTest test = new MultipleConnectionTest();
        test._connectionString = broker;
        test.test();
    }

    @Test
    public void test() throws Exception
    {
        String broker = _connectionString;
        int messages = 10;

        AMQTopic topic = new AMQTopic("amq.topic");

        /*
        Receiver[] receivers = new Receiver[connections];
        for(int i = 0; i < receivers.length; i++)
        {
            receivers[i] = new Receiver(broker, topic, sessions);
        }
        */

        Receiver[] receivers = new Receiver[]{
                new Receiver(broker, topic, 2),
                new Receiver(broker, topic, 14)
        };

        Publisher publisher = new Publisher(broker, topic);
        for (int i = 0; i < messages; i++)
        {
            publisher.send("Message " + (i + 1));
        }

        try
        {
            waitForCompletion(messages, 5000, receivers);
            System.out.println("All receivers received all expected messages");
        }
        finally
        {
            publisher.close();
            for (int i = 0; i < receivers.length; i++)
            {
                receivers[i].close();
            }
        }
    }

    @BeforeClass
    public static void createVMBroker() throws Exception
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create broker: " + e);
        }
    }

    @AfterClass
    public static void stopVmBroker()
    {
        TransportConnection.killVMBroker(1);
    }


    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(MultipleConnectionTest.class);
    }
}
