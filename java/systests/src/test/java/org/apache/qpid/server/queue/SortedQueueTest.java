/*
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
package org.apache.qpid.server.queue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class SortedQueueTest extends QpidBrokerTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SortedQueueTest.class);
    public static final String TEST_SORT_KEY = "testSortKey";
    private static final String[] VALUES = SortedQueueEntryListTest.keys.clone();
    private static final String[] VALUES_SORTED = SortedQueueEntryListTest.keys.clone();
    private final String[] SUBSET_KEYS = { "000", "100", "200", "300", "400", "500", "600", "700", "800", "900" };

    private Connection _producerConnection;
    private Session _producerSession;
    private Connection _consumerConnection;
    private long _receiveInterval;

    protected void setUp() throws Exception
    {
        super.setUp();
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, "1");
        // Sort value array to generated "expected" order of messages.
        Arrays.sort(VALUES_SORTED);
        _producerConnection = getConnection();
        _consumerConnection = getConnection();
        _producerSession = _producerConnection.createSession(true, Session.SESSION_TRANSACTED);
        _receiveInterval = isBrokerStorePersistent() ? 3000l : 1500l;
    }

    protected void tearDown() throws Exception
    {
        _producerSession.close();
        _producerConnection.close();
        _consumerConnection.close();
        super.tearDown();
    }

    public void testSortOrder() throws JMSException, NamingException, AMQException
    {
        final Queue queue = createQueue();
        final MessageProducer producer = _producerSession.createProducer(queue);

        for(String value : VALUES)
        {
            final Message msg = _producerSession.createTextMessage("Message Text:" + value);
            msg.setStringProperty(TEST_SORT_KEY, value);
            producer.send(msg);
        }

        _producerSession.commit();
        producer.close();

        final Session consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer consumer = consumerSession.createConsumer(queue);
        _consumerConnection.start();
        TextMessage received;
        int messageCount = 0;
        while((received = (TextMessage) consumer.receive(_receiveInterval)) != null)
        {
            assertEquals("Received message with unexpected sorted key value", VALUES_SORTED[messageCount],
                            received.getStringProperty(TEST_SORT_KEY));
            assertEquals("Received message with unexpected message value",
                            "Message Text:" + VALUES_SORTED[messageCount], received.getText());
            messageCount++;
        }

        assertEquals("Incorrect number of messages received", VALUES.length, messageCount);
    }

    public void testAutoAckSortedQueue() throws JMSException, NamingException, AMQException
    {
        runThroughSortedQueueForSessionMode(Session.AUTO_ACKNOWLEDGE);
    }

    public void testTransactedSortedQueue() throws JMSException, NamingException, AMQException
    {
        runThroughSortedQueueForSessionMode(Session.SESSION_TRANSACTED);
    }

    public void testClientAckSortedQueue() throws JMSException, NamingException, AMQException
    {
        runThroughSortedQueueForSessionMode(Session.CLIENT_ACKNOWLEDGE);
    }

    private void runThroughSortedQueueForSessionMode(final int sessionMode) throws JMSException, NamingException,
                    AMQException
    {
        final Queue queue = createQueue();
        final MessageProducer producer = _producerSession.createProducer(queue);

        final TestConsumerThread consumerThread = new TestConsumerThread(sessionMode, queue);
        consumerThread.start();

        for(String value : VALUES)
        {
            final Message msg = _producerSession.createMessage();
            msg.setStringProperty(TEST_SORT_KEY, value);
            producer.send(msg);
            _producerSession.commit();
        }

        try
        {
            consumerThread.join(getConsumerThreadJoinInterval());
        }
        catch(InterruptedException e)
        {
            fail("Test failed waiting for consumer to complete");
        }

        assertTrue("Consumer timed out", consumerThread.isStopped());
        assertEquals("Incorrect number of messages received", VALUES.length, consumerThread.getConsumed());

        producer.close();
    }

    public void testSortedQueueWithAscendingSortedKeys() throws JMSException, NamingException, AMQException
    {
        final Queue queue = createQueue();
        final MessageProducer producer = _producerSession.createProducer(queue);

        final TestConsumerThread consumerThread = new TestConsumerThread(Session.AUTO_ACKNOWLEDGE, queue);
        consumerThread.start();

        for(int i = 0; i < 200; i++)
        {
            final String ascendingKey = AscendingSortedKeys.getNextKey();
            final Message msg = _producerSession.createTextMessage("Message Text:" + ascendingKey);
            msg.setStringProperty(TEST_SORT_KEY, ascendingKey);
            producer.send(msg);
            _producerSession.commit();
        }

        try
        {
            consumerThread.join(getConsumerThreadJoinInterval());
        }
        catch(InterruptedException e)
        {
            fail("Test failed waiting for consumer to complete");
        }

        assertTrue("Consumer timed out", consumerThread.isStopped());
        assertEquals("Incorrect number of messages received", 200, consumerThread.getConsumed());

        producer.close();
    }

    private long getConsumerThreadJoinInterval()
    {
        return isBrokerStorePersistent() ? 50000L: 5000L;
    }

    public void testSortOrderWithNonUniqueKeys() throws JMSException, NamingException, AMQException
    {
        final Queue queue = createQueue();
        final MessageProducer producer = _producerSession.createProducer(queue);

        int count = 0;
        while(count < 200)
        {
            final Message msg = _producerSession.createTextMessage("Message Text:" + count++);
            msg.setStringProperty(TEST_SORT_KEY, "samesortkeyvalue");
            producer.send(msg);
        }

        _producerSession.commit();
        producer.close();

        final Session consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer consumer = consumerSession.createConsumer(queue);
        _consumerConnection.start();
        TextMessage received = null;
        int messageCount = 0;

        while((received = (TextMessage) consumer.receive(_receiveInterval)) != null)
        {
            assertEquals("Received message with unexpected sorted key value", "samesortkeyvalue",
                            received.getStringProperty(TEST_SORT_KEY));
            assertEquals("Received message with unexpected message value", "Message Text:" + messageCount,
                            received.getText());
            messageCount++;
        }

        assertEquals("Incorrect number of messages received", 200, messageCount);
    }

    public void testSortOrderWithUniqueKeySubset() throws JMSException, NamingException, AMQException
    {
        final Queue queue = createQueue();
        final MessageProducer producer = _producerSession.createProducer(queue);

        int count = 0;
        while(count < 100)
        {
            int keyValueIndex = count % 10;
            final Message msg = _producerSession.createTextMessage("Message Text:" + count);
            msg.setStringProperty(TEST_SORT_KEY, SUBSET_KEYS[keyValueIndex]);
            producer.send(msg);
            count++;
        }

        _producerSession.commit();
        producer.close();

        final Session consumerSession = _consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer consumer = consumerSession.createConsumer(queue);
        _consumerConnection.start();
        TextMessage received;
        int messageCount = 0;

        while((received = (TextMessage) consumer.receive(_receiveInterval)) != null)
        {
            assertEquals("Received message with unexpected sorted key value", SUBSET_KEYS[messageCount / 10],
                            received.getStringProperty(TEST_SORT_KEY));
            messageCount++;
        }

        assertEquals("Incorrect number of messages received", 100, messageCount);
    }

    public void testGetNextWithAck() throws JMSException, NamingException, AMQException
    {
        Queue _queue = createQueue();
        MessageProducer producer = _producerSession.createProducer(_queue);
        Message received = null;

        //Send 3 out of order
        sendAndCommitMessage(producer,"2");
        sendAndCommitMessage(producer,"3");
        sendAndCommitMessage(producer,"1");

        final Session consumerSession = _consumerConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        final MessageConsumer consumer = consumerSession.createConsumer(_queue);
        _consumerConnection.start();

        //Receive 3 in sorted order
        received = assertReceiveAndValidateMessage(consumer, "1");
        received.acknowledge();
        received = assertReceiveAndValidateMessage(consumer, "2");
        received.acknowledge();
        received = assertReceiveAndValidateMessage(consumer, "3");
        received.acknowledge();

        //Send 1
        sendAndCommitMessage(producer,"4");

        //Receive 1 and recover
        received = assertReceiveAndValidateMessage(consumer, "4");
        consumerSession.recover();

        //Receive same 1
        received = assertReceiveAndValidateMessage(consumer, "4");
        received.acknowledge();

        //Send 3 out of order
        sendAndCommitMessage(producer,"7");
        sendAndCommitMessage(producer,"6");
        sendAndCommitMessage(producer,"5");

        //Receive 1 of 3 (possibly out of order due to pre-fetch)
        final Message messageBeforeRollback = assertReceiveMessage(consumer);
        consumerSession.recover();

        if (isBroker010())
        {
            //Receive 3 in sorted order (not as per JMS recover)
            received = assertReceiveAndValidateMessage(consumer, "5");
            received.acknowledge();
            received = assertReceiveAndValidateMessage(consumer, "6");
            received.acknowledge();
            received = assertReceiveAndValidateMessage(consumer, "7");
            received.acknowledge();
        }
        else
        {
            //First message will be the one rolled-back (as per JMS spec).
            final String messageKeyDeliveredBeforeRollback = messageBeforeRollback.getStringProperty(TEST_SORT_KEY);
            received = assertReceiveAndValidateMessage(consumer, messageKeyDeliveredBeforeRollback);
            received.acknowledge();

            //Remaining two messages will be sorted
            final SortedSet<String> keys = new TreeSet<String>(Arrays.asList("5", "6", "7"));
            keys.remove(messageKeyDeliveredBeforeRollback);

            received = assertReceiveAndValidateMessage(consumer, keys.first());
            received.acknowledge();
            received = assertReceiveAndValidateMessage(consumer, keys.last());
            received.acknowledge();
        }
    }

    private Queue createQueue() throws AMQException, JMSException
    {
        final Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(QueueArgumentsConverter.QPID_QUEUE_SORT_KEY, TEST_SORT_KEY);
        ((AMQSession<?,?>) _producerSession).createQueue(new AMQShortString(getTestQueueName()), false, true, false, arguments);
        final Queue queue = new AMQQueue("amq.direct", getTestQueueName());
        ((AMQSession<?,?>) _producerSession).declareAndBind((AMQDestination) queue);
        return queue;
    }

    private Message getSortableTestMesssage(final String key) throws JMSException
    {
        final Message msg = _producerSession.createTextMessage("Message Text: Key Value" + key);
        msg.setStringProperty(TEST_SORT_KEY, key);
        return msg;
    }

    private void sendAndCommitMessage(final MessageProducer producer, final String keyValue) throws JMSException
    {
        producer.send(getSortableTestMesssage(keyValue));
        _producerSession.commit();
    }

    private Message assertReceiveAndValidateMessage(final MessageConsumer consumer, final String expectedKey) throws JMSException
    {
        final Message received = assertReceiveMessage(consumer);
        assertEquals("Received message with unexpected sorted key value", expectedKey,
                        received.getStringProperty(TEST_SORT_KEY));
        return received;
    }

    private Message assertReceiveMessage(final MessageConsumer consumer)
            throws JMSException
    {
        final Message received = (TextMessage) consumer.receive(_receiveInterval);
        assertNotNull("Received message is unexpectedly null", received);
        return received;
    }

    private class TestConsumerThread extends Thread
    {
        private final AtomicInteger _consumed = new AtomicInteger(0);
        private volatile boolean _stopped = false;
        private int _count = 0;
        private int _sessionType = Session.AUTO_ACKNOWLEDGE;
        private Queue _queue;

        public TestConsumerThread(final int sessionType, final Queue queue)
        {
            _sessionType = sessionType;
            _queue = queue;
        }

        public void run()
        {
            try
            {
                Connection conn = null;
                try
                {
                    conn = getConnection();
                }
                catch(Exception e)
                {
                    throw new RuntimeException("Could not get connection");
                }

                final Session session = conn.createSession((_sessionType == Session.SESSION_TRANSACTED ? true : false),
                                _sessionType);
                final MessageConsumer consumer = session.createConsumer(_queue);

                conn.start();

                Message msg;
                while((msg = consumer.receive(_receiveInterval)) != null)
                {
                    if(_sessionType == Session.SESSION_TRANSACTED)
                    {
                         if (_count%10 == 0)
                         {
                             LOGGER.debug("transacted session rollback");
                             session.rollback();
                         }
                         else
                         {
                             LOGGER.debug("transacted session commit");
                            session.commit();
                            _consumed.incrementAndGet();
                         }
                    }
                    else if(_sessionType == Session.CLIENT_ACKNOWLEDGE)
                    {
                         if (_count%10 == 0)
                         {
                             LOGGER.debug("client ack session recover");
                             session.recover();
                         }
                         else
                         {
                             LOGGER.debug("client ack session acknowledge");
                             msg.acknowledge();
                             _consumed.incrementAndGet();
                         }
                    }
                    else
                    {
                        LOGGER.debug("auto ack session");
                        _consumed.incrementAndGet();
                    }

                    _count++;
                    LOGGER.debug("Message consumed with key: " + msg.getStringProperty(TEST_SORT_KEY));
                    LOGGER.debug("Message consumed with consumed index: " + _consumed.get());
                }

                _stopped = true;
                session.close();
                conn.close();
            }
            catch(JMSException e)
            {
                LOGGER.error("Exception in listener", e);
           }
        }

        public boolean isStopped()
        {
            return _stopped;
        }

        public int getConsumed()
        {
            return _consumed.get();
        }
    }

    private static class AscendingSortedKeys
    {
        public static final String[] KEYS = { "Ul4a1", "WaWsv", "2Yz7E", "ix74r", "okgRi", "HlUbF", "LewvM", "lweGy",
                        "TXQ0Z", "0Kyfs", "s7Mxk", "dmoS7", "8RCUA", "W3VFH", "aez9y", "uQIcz", "0h1b1", "cmXIX",
                        "4dEz6", "zHF1q", "D6rBy", "5drc6", "0BmCy", "BCxeC", "t59lR", "aL6AJ", "OHaBz", "WmadA",
                        "B3qem", "CxVEf", "AIYUu", "uJScX", "uoStw", "ogLgc", "AgJHQ", "hUTw7", "Rxrsm", "9GXkX",
                        "7hyVv", "y94nw", "Twano", "TCgPp", "pFrrl", "POUYS", "L7cGc", "0ao3l", "CNHmv", "MaJQs",
                        "OUqFM", "jeskS", "FPfSE", "v1Hln", "14FLR", "KZamH", "G1RhS", "FVMxo", "rKDLJ", "nnP8o",
                        "nFqik", "zmLYD", "1j5L8", "e6e4z", "WDVWJ", "aDGtS", "fcwDa", "nlaBy", "JJs5m", "vLsmS",
                        "No0Qb", "JXljW", "Waim6", "MezSW", "l83Ud", "SjskQ", "uPX7G", "5nmWv", "ZhwG1", "uTacx",
                        "t98iW", "JkzUn", "fmIK1", "i7WMQ", "bgJAz", "n1pmO", "jS1aj", "4W0Tl", "Yf2Ec", "sqVrf",
                        "MojnP", "qQxHP", "pWiOs", "yToGW", "kB5nP", "BpYhV", "Cfgr3", "zbIYY", "VLTy6", "he9IA",
                        "lm0pD", "WreyP", "8hJdt", "QnJ1S", "n8pJ9", "iqv4k", "OUYuF", "8cVD3", "sx5Gl", "cQOnv",
                        "wiHrZ", "oGu6x", "7fsYM", "gf8rI", "7fKYU", "pT8wu", "lCMxy", "prNT6", "5Drn0", "guMb8",
                        "OxWIH", "uZPqg", "SbRYy", "In3NS", "uvf7A", "FLsph", "pmeCd", "BbwgA", "ru4UG", "YOfrY",
                        "W7cTs", "K4GS8", "AOgEe", "618Di", "dpe1v", "3otm6", "oVQp6", "5Mg9r", "Y1mC0", "VIlwP",
                        "aFFss", "Mkgy8", "pv0i7", "S77LH", "XyPZN", "QYxC0", "vkCHH", "MGlTF", "24ARF", "v2eC3",
                        "ZUnqt", "HfyNQ", "FjHXR", "45cIH", "1LB1L", "zqH0W", "fLNg8", "oQ87r", "Cp3mZ", "Zv7z0",
                        "O3iyQ", "EOE1o", "5ZaEz", "tlILt", "MmsIo", "lXFOB", "gtCA5", "yEfy9", "7X3uy", "d7vjM",
                        "XflUq", "Fhtgl", "NOHsz", "GWqqX", "xciqp", "BFkb8", "P6bcg", "lViBv", "2TRI7", "2hEEU",
                        "9XyT9", "29QAz", "U3yw5", "FxX9q", "C2Irc", "8U2nU", "m4bxU", "5iGN5", "mX2GE", "cShY2",
                        "JRJQB", "yvOMI", "4QMc9", "NAFuw", "RmDcr", "faHir", "2ZHdk", "zY1GY", "a00b5", "ZuDtD",
                        "JIqXi", "K20wK", "gdQsS", "5Namm", "lkMUA", "IBe8k", "FcWrW", "FFDui", "tuDyS", "ZJTXH",
                        "AkKTk", "zQt6Q", "FNYIM", "RpBQm", "RsQUq", "Mm8si", "gjUTu", "zz4ZU", "jiVBP", "ReKEW",
                        "5VZjS", "YjB9t", "zFgtB", "8TxD7", "euZA5", "MK07Y", "CK5W7", "16lHc", "6q6L9", "Z4I1v",
                        "UlU3M", "SWfou", "0PktI", "55rfB", "jfREu", "580YD", "Uvlv4", "KASQ8", "AmdQd", "piJSk",
                        "hE1Ql", "LDk6f", "NcICA", "IKxdL", "iwzGk", "uN6r3", "lsQGo", "QClRL", "iKqhr", "FGzgp",
                        "RkQke", "b29RJ", "CIShG", "9eoRc", "F6PT2", "LbRTH", "M3zXL", "GXdoH", "IjTwP", "RBhp0",
                        "yluBx", "mz8gx", "MmKGJ", "Q6Lix", "uupzk", "RACuj", "d85a9", "qaofN", "kZANm", "jtn0X",
                        "lpF6W", "suY4x", "rz7Ut", "wDajX", "1v5hH", "Yw2oU", "ksJby", "WMiS3", "lj07Q", "EdBKc",
                        "6AFT0", "0YAGH", "ThjNn", "JKWYR", "9iGoT", "UmaEv", "3weIF", "CdyBV", "pAhR1", "djsrv",
                        "xReec", "8FmFH", "Dz1R3", "Ta8f6", "DG4sT", "VjCZq", "jSjS3", "Pb1pa", "VNCPd", "Kr8ys",
                        "AXpwE", "ZzJHW", "Nxx9V", "jzUqR", "dhSuH", "DQimp", "07n1c", "HP433", "RzaZA", "cL0aE",
                        "Ss0Zu", "FnPFB", "7lUXZ", "9rlg9", "lH1kt", "ni2v1", "48cHL", "soy9t", "WPmlx", "2Nslm",
                        "hSSvQ", "9y4lw", "ulk41", "ECMvU", "DLhzM", "GrDg7", "x3LDe", "QChxs", "xXTI4", "Gv3Fq",
                        "rhl0J", "QssNC", "brhlQ", "s93Ml", "tl72W", "pvgjS", "Qworu", "DcpWB", "X6Pin", "J2mQi",
                        "BGaQY", "CqqaD", "NhXdu", "dQ586", "Yh1hF", "HRxd8", "PYBf4", "64s8N", "tvdkD", "azIWp",
                        "tAOsr", "v8yFN", "h1zcH", "SmGzv", "bZLvS", "fFDrJ", "Oz8yZ", "0Wr5y", "fcJOy", "7ku1p",
                        "QbxXc", "VerEA", "QWxoT", "hYBCK", "o8Uyd", "FwEJz", "hi5X7", "uAWyp", "I7p2a", "M6qcG",
                        "gIYvE", "HzZT8", "iB08l", "StlDJ", "tjQxs", "k85Ae", "taOXK", "s4786", "2DREs", "atef2",
                        "Vprf2", "VBjhz", "EoToP", "blLA9", "qUJMd", "ydG8U", "8xEKz", "uLtKs", "GSQwj", "S2Dfu",
                        "ciuWz", "i3pyd", "7Ow5C", "IRh48", "vOqCE", "Q6hMC", "yofH3", "KsjRK", "5IhmG", "fqypy",
                        "0MR5X", "Chuy3" };

        private static int _i = 0;
        private static int _j = 0;

        static
        {
            Arrays.sort(KEYS);
        }

        public static String getNextKey()
        {
            if(_j == KEYS.length)
            {
                _j = 0;
                _i++;
                if(_i == KEYS.length)
                {
                    _i = 0;
                }
            }
            return new StringBuffer().append(KEYS[_i]).append("-").append(KEYS[_j++]).toString();
        }
    }
}
