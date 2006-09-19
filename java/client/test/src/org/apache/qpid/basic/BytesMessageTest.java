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
package org.apache.qpid.basic;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.JMSBytesMessage;
import org.apache.qpid.client.testutil.VmOrRemoteTestCase;
import org.apache.mina.common.ByteBuffer;
import org.junit.Test;

import javax.jms.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BytesMessageTest extends VmOrRemoteTestCase implements MessageListener
{
    private Connection _connection;
    private Destination _destination;
    private Session _session;
    private final List<JMSBytesMessage> received = new ArrayList<JMSBytesMessage>();
    private final List<byte[]> messages = new ArrayList<byte[]>();
    private int _count = 100;

    void init() throws Exception
    {
        init(new AMQConnection(getConnectionString(), "guest", "guest", randomize("Client"), "/test_path"));
    }

    void init(AMQConnection connection) throws Exception
    {
        init(connection, new AMQQueue(randomize("BytesMessageTest"), true));
    }

    void init(AMQConnection connection, AMQDestination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);

        // Set up a slow consumer.
        _session.createConsumer(destination).setMessageListener(this);
        connection.start();
    }

    @Test
    public void test() throws Exception
    {
        init();

        try
        {
            send(_count);
            waitFor(_count);
            check();
            System.out.println("Completed without failure");
        }
        finally
        {
            _connection.close();
        }
    }

    void send(int count) throws JMSException
    {
        //create a publisher
        MessageProducer producer = _session.createProducer(_destination);
        for (int i = 0; i < count; i++)
        {
            BytesMessage msg = _session.createBytesMessage();
            byte[] data = ("Message " + i).getBytes();
            msg.writeBytes(data);
            messages.add(data);
            producer.send(msg);
        }
    }

    void waitFor(int count) throws InterruptedException
    {
        synchronized (received)
        {
            while (received.size() < count)
            {
                received.wait();
            }
        }
    }

    void check() throws JMSException
    {
        List<byte[]> actual = new ArrayList<byte[]>();
        for (JMSBytesMessage m : received)
        {
            ByteBuffer buffer = m.getData();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            actual.add(data);
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
                assertEquivalent((byte[]) expected.next(), (byte[]) actual.next());
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

    private static void assertEquivalent(byte[] expected, byte[] actual)
    {
        if (expected.length != actual.length)
        {
            throw new RuntimeException("Expected length " + expected.length + " got " + actual.length);
        }
        for (int i = 0; i < expected.length; i++)
        {
            if (expected[i] != actual[i])
            {
                throw new RuntimeException("Failed on byte " + i + " of " + expected.length);
            }
        }
    }

    public void onMessage(Message message)
    {
        synchronized (received)
        {
            received.add((JMSBytesMessage) message);
            received.notify();
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        final String connectionString;
        final int count;
        if (argv.length == 0)
        {
            connectionString = "localhost:5672";
            count = 100;
        }
        else
        {
            connectionString = argv[0];
            count = Integer.parseInt(argv[1]);
        }

        System.out.println("connectionString = " + connectionString);
        System.out.println("count = " + count);

        BytesMessageTest test = new BytesMessageTest();
        test.setConnectionString(connectionString);
        test._count = count;
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(BytesMessageTest.class);
    }
}
