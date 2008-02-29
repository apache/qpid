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

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.AMQMessage;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.testutil.QpidTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PropertyValueTest extends QpidTestCase implements MessageListener
{
    private static final Logger _logger = LoggerFactory.getLogger(PropertyValueTest.class);

    private int count = 0;
    private AMQConnection _connection;
    private Destination _destination;
    private AMQSession _session;
    private final List<JMSTextMessage> received = new ArrayList<JMSTextMessage>();
    private final List<String> messages = new ArrayList<String>();
    private int _count = 1;
    public String _connectionString = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    private void init(AMQConnection connection) throws Exception
    {
        Destination destination = new AMQQueue(connection, randomize("PropertyValueTest"), true);
        init(connection, destination);
    }

    private void init(AMQConnection connection, Destination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // set up a slow consumer
        _session.createConsumer(destination).setMessageListener(this);
        connection.start();
    }

    public void testOnce()
    {
        runBatch(1);
    }

    public void test50()
    {
        runBatch(50);
    }

    private void runBatch(int runSize)
    {
        try
        {
            int run = 0;
            while (run < runSize)
            {
                _logger.error("Run Number:" + run++);
                try
                {
                    init( (AMQConnection) getConnection("guest", "guest"));
                }
                catch (Exception e)
                {
                    fail("Unable to initialilse connection: " + e);
                }

                int count = _count;
                send(count);
                waitFor(count);
                check();
                _logger.info("Completed without failure");

                Thread.sleep(10);
                _connection.close();

                _logger.error("End Run Number:" + (run - 1));
            }
        }
        catch (Exception e)
        {
            _logger.error(e.getMessage(), e);
            e.printStackTrace();
        }
    }

    void send(int count) throws JMSException
    {
        // create a publisher
        MessageProducer producer = _session.createProducer(_destination);
        for (int i = 0; i < count; i++)
        {
            String text = "Message " + i;
            messages.add(text);
            Message m = _session.createTextMessage(text);

            m.setBooleanProperty("Bool", true);

            m.setByteProperty("Byte", (byte) Byte.MAX_VALUE);
            m.setDoubleProperty("Double", (double) Double.MAX_VALUE);
            m.setFloatProperty("Float", (float) Float.MAX_VALUE);
            m.setIntProperty("Int", (int) Integer.MAX_VALUE);

            m.setJMSCorrelationID("Correlation");
            // fixme the m.setJMSMessage has no effect
            producer.setPriority(8);
            m.setJMSPriority(3);

            // Queue
            Queue q;

            if ((i / 2) == 0)
            {
                q = _session.createTemporaryQueue();
            }
            else
            {
                q = new AMQQueue(_connection, "TestReply");
            }

            m.setJMSReplyTo(q);
            m.setStringProperty("TempQueue", q.toString());

            _logger.trace("Message:" + m);

            Assert.assertEquals("Check temp queue has been set correctly", m.getJMSReplyTo().toString(),
                m.getStringProperty("TempQueue"));

            m.setJMSType("Test");
            m.setLongProperty("UnsignedInt", (long) 4294967295L);
            m.setLongProperty("Long", (long) Long.MAX_VALUE);

            m.setShortProperty("Short", (short) Short.MAX_VALUE);
            m.setStringProperty("String", "Test");

            // AMQP Specific values

            // Timestamp
            long nano = System.nanoTime();
            m.setStringProperty("time-str", String.valueOf(nano));
            ((AMQMessage) m).setTimestampProperty(new AMQShortString("time"), nano);

            // Decimal
            BigDecimal bd = new BigDecimal(Integer.MAX_VALUE);
            ((AMQMessage) m).setDecimalProperty(new AMQShortString("decimal"), bd.setScale(Byte.MAX_VALUE));

            bd = new BigDecimal((long) Integer.MAX_VALUE + 1L);

            try
            {
                ((AMQMessage) m).setDecimalProperty(new AMQShortString("decimal-bad-value"), bd.setScale(Byte.MAX_VALUE));
                fail("UnsupportedOperationException should be thrown as value can't be correctly transmitted");
            }
            catch (UnsupportedOperationException uoe)
            {
                // normal path.
            }

            try
            {
                ((AMQMessage) m).setDecimalProperty(new AMQShortString("decimal-bad-scale"),
                    bd.setScale(Byte.MAX_VALUE + 1));
                fail("UnsupportedOperationException should be thrown as scale can't be correctly transmitted");
            }
            catch (UnsupportedOperationException uoe)
            {
                // normal path.
            }

            // Void
            ((AMQMessage) m).setVoidProperty(new AMQShortString("void"));

            _logger.debug("Sending Msg:" + m);
            producer.send(m);
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
        List<String> actual = new ArrayList<String>();
        for (JMSTextMessage m : received)
        {
            actual.add(m.getText());

            // Check Properties

            Assert.assertEquals("Check Boolean properties are correctly transported", true, m.getBooleanProperty("Bool"));
            Assert.assertEquals("Check Byte properties are correctly transported", (byte) Byte.MAX_VALUE,
                m.getByteProperty("Byte"));
            Assert.assertEquals("Check Double properties are correctly transported", (double) Double.MAX_VALUE,
                m.getDoubleProperty("Double"));
            Assert.assertEquals("Check Float properties are correctly transported", (float) Float.MAX_VALUE,
                m.getFloatProperty("Float"));
            Assert.assertEquals("Check Int properties are correctly transported", (int) Integer.MAX_VALUE,
                m.getIntProperty("Int"));
            Assert.assertEquals("Check CorrelationID properties are correctly transported", "Correlation",
                m.getJMSCorrelationID());
            Assert.assertEquals("Check Priority properties are correctly transported", 8, m.getJMSPriority());

            // Queue
            Assert.assertEquals("Check ReplyTo properties are correctly transported", m.getStringProperty("TempQueue"),
                m.getJMSReplyTo().toString());

            Assert.assertEquals("Check Type properties are correctly transported", "Test", m.getJMSType());

            Assert.assertEquals("Check Short properties are correctly transported", (short) Short.MAX_VALUE,
                m.getShortProperty("Short"));
            Assert.assertEquals("Check UnsignedInt properties are correctly transported", (long) 4294967295L,
                m.getLongProperty("UnsignedInt"));
            Assert.assertEquals("Check Long properties are correctly transported", (long) Long.MAX_VALUE,
                m.getLongProperty("Long"));
            Assert.assertEquals("Check String properties are correctly transported", "Test", m.getStringProperty("String"));

            // AMQP Tests Specific values

            Assert.assertEquals("Check Timestamp properties are correctly transported", m.getStringProperty("time-str"),
                ((AMQMessage) m).getTimestampProperty(new AMQShortString("time")).toString());

            // Decimal
            BigDecimal bd = new BigDecimal(Integer.MAX_VALUE);

            Assert.assertEquals("Check decimal properties are correctly transported", bd.setScale(Byte.MAX_VALUE),
                ((AMQMessage) m).getDecimalProperty(new AMQShortString("decimal")));

            // Void
            ((AMQMessage) m).setVoidProperty(new AMQShortString("void"));

            Assert.assertTrue("Check void properties are correctly transported",
                ((AMQMessage) m).getPropertyHeaders().containsKey("void"));
        }

        received.clear();

        assertEqual(messages.iterator(), actual.iterator());

        messages.clear();
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
        synchronized (received)
        {
            received.add((JMSTextMessage) message);
            received.notify();
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        PropertyValueTest test = new PropertyValueTest();
        test._connectionString = (argv.length == 0) ? "vm://:1" : argv[0];
        test.setUp();
        if (argv.length > 1)
        {
            test._count = Integer.parseInt(argv[1]);
        }

        test.testOnce();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(PropertyValueTest.class);
    }
}
