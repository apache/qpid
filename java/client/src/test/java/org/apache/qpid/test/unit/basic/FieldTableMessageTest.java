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
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.message.JMSBytesMessage;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableTest;
import org.apache.mina.common.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.After;

import javax.jms.*;
import java.io.IOException;
import java.util.ArrayList;

public class FieldTableMessageTest implements MessageListener
{
    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private final ArrayList<JMSBytesMessage> received = new ArrayList<JMSBytesMessage>();
    private FieldTable _expected;
    private int _count = 10;
    public String _connectionString = "vm://:1";

    @Before
    public void init() throws Exception
    {
        createVMBroker();
        init(new AMQConnection(_connectionString, "guest", "guest", randomize("Client"), "/test_path"));        
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
        init(connection, new AMQQueue(randomize("FieldTableMessageTest"), true));
    }

    private void init(AMQConnection connection, AMQDestination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);

        //set up a slow consumer
        _session.createConsumer(destination).setMessageListener(this);
        connection.start();

        //_expected = new FieldTableTest().load("FieldTableTest2.properties");
        _expected = load();
    }

    private FieldTable load() throws IOException
    {
        FieldTable result = new FieldTable();
        result.put("one", 1L);
        result.put("two", 2L);
        result.put("three", 3L);
        result.put("four", 4L);
        result.put("five", 5L);

        return result;
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

    void send(int count) throws JMSException, IOException
    {
        //create a publisher
        MessageProducer producer = _session.createProducer(_destination);
        for (int i = 0; i < count; i++)
        {
            BytesMessage msg = _session.createBytesMessage();
            msg.writeBytes(_expected.getDataAsBytes());
            producer.send(msg);
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

    void check() throws JMSException, AMQFrameDecodingException
    {
        for (Object m : received)
        {
            ByteBuffer buffer = ((JMSBytesMessage) m).getData();
            FieldTable actual = new FieldTable(buffer, buffer.remaining());
            new FieldTableTest().assertEquivalent(_expected, actual);
        }
    }

    public void onMessage(Message message)
    {
        synchronized(received)
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
        FieldTableMessageTest test = new FieldTableMessageTest();
        test._connectionString = argv.length == 0 ? "vm://:1" : argv[0];
        test.init();
        test._count = argv.length > 1 ? Integer.parseInt(argv[1]) : 5;
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(FieldTableMessageTest.class);
    }
}
