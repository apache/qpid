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
package org.apache.qpid.client.channelclose;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.log4j.Logger;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import javax.jms.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Due to bizarre exception handling all sessions are closed if you get
 * a channel close request and no exception listener is registered.
 *
 * JIRA issue IBTBLZ-10.
 *
 * Simulate by:
 *
 * 0. Create two sessions with no exception listener.
 * 1. Publish message to queue/topic that does not exist (wrong routing key).
 * 2. This will cause a channel close.
 * 3. Since client does not have an exception listener, currently all sessions are
 *    closed.
 */
public class ChannelCloseOkTest
{
    private Connection _connection;
    private Destination _destination1;
    private Destination _destination2;
    private Session _session1;
    private Session _session2;
    private final List<Message> _received1 = new ArrayList<Message>();
    private final List<Message> _received2 = new ArrayList<Message>();

    private final static Logger _log = Logger.getLogger(ChannelCloseOkTest.class);
    public String _connectionString = "vm://:1";

    @Before
    public void init() throws Exception
    {
        _connection = new AMQConnection(_connectionString, "guest", "guest", randomize("Client"), "/test_path");
        _destination1 = new AMQQueue("q1", true);
        _destination2 = new AMQQueue("q2", true);
        _session1 = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _session1.createConsumer(_destination1).setMessageListener(new MessageListener() {
            public void onMessage(Message message)
            {
                _log.debug("consumer 1 got message [" + getTextMessage(message) + "]");
                synchronized (_received1)
                {
                    _received1.add(message);
                    _received1.notify();
                }
            }
        });
        _session2 = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _session2.createConsumer(_destination2).setMessageListener(new MessageListener() {
            public void onMessage(Message message)
            {
                _log.debug("consumer 2 got message [" +  getTextMessage(message) + "]");
                synchronized (_received2)
                {
                    _received2.add(message);
                    _received2.notify();
                }
            }
        });

        _connection.start();
    }

    private String getTextMessage(Message message)
    {
        TextMessage tm = (TextMessage)message;
        try
        {
            return tm.getText();
        }
        catch (JMSException e)
        {
            return "oops " + e;
        }
    }

    @After
    public void closeConnection() throws JMSException
    {
        if (_connection != null)
        {
            System.out.println(">>>>>>>>>>>>>>.. closing");
            _connection.close();
        }
    }

    @Test
    public void testWithoutExceptionListener() throws Exception
    {
        test();
    }

    @Test
    public void testWithExceptionListener() throws Exception
    {
        _connection.setExceptionListener(new ExceptionListener() {
            public void onException(JMSException jmsException)
            {
                _log.error("onException - ", jmsException);
            }
        });

        test();
    }

    public void test() throws Exception
    {
        // Check both sessions are ok.
        sendAndWait(_session1, _destination1, "first", _received1, 1);
        sendAndWait(_session2, _destination2, "second", _received2, 1);
        assertEquals(1, _received1.size());
        assertEquals(1, _received2.size());

        // Now send message to incorrect destination on session 1.
        Destination destination = new AMQQueue("incorrect");
        send(_session1, destination, "third"); // no point waiting as message will never be received.

        // Ensure both sessions are still ok.
        // Send a bunch of messages as this give time for the sessions to be erroneously closed.
        final int num = 300;
        for (int i = 0; i < num; ++i)
        {
            send(_session1, _destination1, "" + i);
            send(_session2, _destination2, "" + i);
        }
        waitFor(_received1, num + 1);
        waitFor(_received2, num + 1);

        // Note that the third message is never received as it is sent to an incorrect destination.
        assertEquals(num + 1, _received1.size());
        assertEquals(num + 1, _received2.size());
    }

    private void sendAndWait(Session session, Destination destination, String message, List<Message> received, int count)
            throws JMSException, InterruptedException
    {
        send(session, destination, message);
        waitFor(received, count);
    }

    private void send(Session session, Destination destination, String message) throws JMSException
    {
        _log.debug("sending message " + message);
        MessageProducer producer1 = session.createProducer(destination);
        producer1.send(session.createTextMessage(message));
    }

    private void waitFor(List<Message> received, int count) throws InterruptedException
    {
        synchronized (received)
        {
            while (received.size() < count)
            {
                received.wait();
            }
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    /**
     * For Junit 3 compatibility.
     */
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ChannelCloseOkTest.class);
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main(ChannelCloseOkTest.class.getName());
    }
}
