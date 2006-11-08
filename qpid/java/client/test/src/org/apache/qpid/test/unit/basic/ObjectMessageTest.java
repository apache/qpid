/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.test.unit.basic;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.message.JMSObjectMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.After;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ObjectMessageTest implements MessageListener
{
    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private final List<JMSObjectMessage> received = new ArrayList<JMSObjectMessage>();
    private final List<Payload> messages = new ArrayList<Payload>();
    private int _count = 100;
    public String _connectionString = "vm://:1";

    @Before
    public void init()
    {
        String broker = _connectionString;
        createVMBroker();
        try
        {            
            init(new AMQConnection(broker, "guest", "guest", randomize("Client"), "/test_path"));
        }
        catch (Exception e)
        {
            Assert.fail("Uable to initialise: " + e);
        }
    }

    public void createVMBroker()
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

    @After
    public void stopVmBroker()
    {
        TransportConnection.killVMBroker(1);
    }

    private void init(AMQConnection connection) throws Exception
    {
        init(connection, new AMQQueue(randomize("ObjectMessageTest"), true));
    }

    private void init(AMQConnection connection, AMQDestination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);

        //set up a slow consumer
        _session.createConsumer(destination).setMessageListener(this);
        connection.start();
    }

    @Test
    public void test() throws Exception
    {
        int count = _count;
        send(count);
        waitFor(count);
        check();
        System.out.println("Completed without failure");
        _connection.close();
    }

    void send(int count) throws JMSException
    {
        //create a publisher
        MessageProducer producer = _session.createProducer(_destination);
        for (int i = 0; i < count; i++)
        {
            Payload payload = new Payload("Message " + i);
            messages.add(payload);
            producer.send(_session.createObjectMessage(payload));
        }
    }

    void waitFor(int count) throws InterruptedException
    {
        synchronized(received)
        {
            while (received.size() < count)
            {
                received.wait();
            }
        }
    }

    void check() throws JMSException
    {
        List<Object> actual = new ArrayList<Object>();
        for (JMSObjectMessage m : received)
        {
            actual.add(m.getObject());
        }

        assertEqual(messages.iterator(), actual.iterator());
    }

    private static void assertEqual(Iterator expected, Iterator actual)
    {
        List<String> errors = new ArrayList<String>();
        while (expected.hasNext() && actual.hasNext())
        {
            try
            {
                assertEqual(expected.next(), actual.next());
            }
            catch (Exception e)
            {
                errors.add(e.getMessage());
            }
        }
        while (expected.hasNext())
        {
            errors.add("Expected " + expected.next() + " but no more actual values.");
        }
        while (actual.hasNext())
        {
            errors.add("Found " + actual.next() + " but no more expected values.");
        }
        if (!errors.isEmpty())
        {
            throw new RuntimeException(errors.toString());
        }
    }

    private static void assertEqual(Object expected, Object actual)
    {
        if (!expected.equals(actual))
        {
            throw new RuntimeException("Expected '" + expected + "' found '" + actual + "'");
        }
    }

    public void onMessage(Message message)
    {
        synchronized(received)
        {
            received.add((JMSObjectMessage) message);
            received.notify();
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    private static class Payload implements Serializable
    {
        private final String data;

        Payload(String data)
        {
            this.data = data;
        }

        public int hashCode()
        {
            return data.hashCode();
        }

        public boolean equals(Object o)
        {
            return o instanceof Payload && ((Payload) o).data.equals(data);
        }

        public String toString()
        {
            return "Payload[" + data + "]";
        }
    }

    public static void main(String[] argv) throws Exception
    {
        ObjectMessageTest test = new ObjectMessageTest();
        test._connectionString = argv.length == 0 ? "vm://:1" : argv[0];
        test.init();
        if (argv.length > 1)
        {
            test._count = Integer.parseInt(argv[1]);
        }
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ObjectMessageTest.class);
    }
}
