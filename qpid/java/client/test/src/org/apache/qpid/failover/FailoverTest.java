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
package org.apache.qpid.failover;

import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.jms.ConnectionListener;

import javax.jms.*;
import java.util.Timer;
import java.util.TimerTask;

public class FailoverTest implements MessageListener, ConnectionListener
{
    private static final long TIMEOUT = 10 * 1000;
    private static final long INTERVAL = 500;
    private final Timer _timer = new Timer(true);
    private final Connection _connection;
    private final Session _session;
    private final MessageProducer _producer;
    private Timeout _timeout;
    private int _count;
    private Queue _tempQueue;

    FailoverTest(String connectionUrl) throws JMSException, AMQException, URLSyntaxException
    {
        this(new AMQConnection(connectionUrl));
        ((AMQConnection) _connection).setConnectionListener(this);
    }

    FailoverTest(Connection connection) throws JMSException
    {
        AMQConnection amqConnection  = (AMQConnection) connection;
        System.out.println("connection.url = " + amqConnection.toURL());
        _connection = connection;
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = _session.createTopic("topic1");
        _tempQueue = _session.createTemporaryQueue();
        _producer = _session.createProducer(topic);
        _session.createConsumer(_tempQueue).setMessageListener(this);
        //new TopicListener(_session, topic);
        new TopicListener(_connection.createSession(false, Session.AUTO_ACKNOWLEDGE), topic);
        _connection.start();

        Message msg = _session.createTextMessage("Init");
        msg.setJMSReplyTo(_tempQueue);
        send(msg);
    }

    public synchronized void onMessage(Message message)
    {
        try
        {
            //cancel timeout:
            _timeout.clear();
            new DelayedSend(_session.createTextMessage("Message" + (++_count)), INTERVAL);
        }
        catch (JMSException e)
        {
            error(e);
        }
    }

    private synchronized void send(Message msg) throws JMSException
    {
        _producer.send(msg);
        //start timeout:
        _timeout = new Timeout(TIMEOUT);
    }

    private void error(Exception e)
    {
        e.printStackTrace();
        stop();
    }

    private void stop()
    {
        System.out.println("Stopping...");
        try
        {
            _connection.close();
        }
        catch (JMSException e)
        {
            System.out.println("Failed to shutdown: " + e);
            e.printStackTrace();
        }
    }

    private void timeout()
    {
        try {
            System.out.println("timed out. Resending init message");
            Message msg = _session.createTextMessage("Init");
            msg.setJMSReplyTo(_tempQueue);
            send(msg);
        } catch (JMSException e) {
            throw new RuntimeException("Got JMSException", e);
        }
//        error(new RuntimeException("Timed out: count = " + _count));
    }

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        System.out.println("preFailover(" + redirect + ") called");
        return true;
    }

    public boolean preResubscribe()
    {
        System.out.println("preResubscribe() called");
        return true;
    }

    public void failoverComplete()
    {
        System.out.println("failoverComplete() called");
    }

    private class TopicListener implements MessageListener
    {
        private final Session _session;
        private MessageProducer _producer;
        private int _received;

        TopicListener(Session session, Topic topic) throws JMSException
        {
            _session = session;
            _session.createConsumer(topic).setMessageListener(this);
        }

        public void onMessage(Message message)
        {
            try
            {
                //if(_received++ % 100 == 0)
                {
                    System.out.println("Received: " + ((TextMessage) message).getText());
                }
                if(_producer == null)
                {
                    _producer = init(message);
                }
                reply(message);
            }
            catch (JMSException e)
            {
               error(e);
            }
        }

        private void reply(Message message) throws JMSException
        {
            _producer.send(_session.createTextMessage(((TextMessage) message).getText()));
        }

        private MessageProducer init(Message message) throws JMSException
        {
            return _session.createProducer(message.getJMSReplyTo());
        }
    }

    private class Timeout extends TimerTask
    {
        private volatile boolean _cancelled;

        Timeout(long time)
        {
            _timer.schedule(this, time);
        }

        void clear()
        {
            _cancelled = true;
        }

        public void run()
        {
            if(!_cancelled)
            {
                timeout();
                System.out.println("would have timed out!");
            }
        }
    }

    private class DelayedSend extends TimerTask
    {
        private final Message _msg;

        DelayedSend(Message msg, long delay)
        {
            _msg = msg;
            _timer.schedule(this, delay);
        }

        public void run()
        {
            try
            {
                send(_msg);
            }
            catch (JMSException e)
            {
                error(e);
            }
        }
    }

    public static void main(final String[] argv) throws Exception
    {
        final String clientId = "failover" + System.currentTimeMillis();
        final String defaultUrl = "amqp://guest:guest@" + clientId + "/test" +
                    "?brokerlist='tcp://localhost:5672;tcp://localhost:5673'&failover='roundrobin'";

        System.out.println("url = [" + defaultUrl + "]");

        System.out.println("connection url = [" + new AMQConnectionURL(defaultUrl) + "]");

        final String broker = argv.length == 0? defaultUrl : argv[0];
        new FailoverTest(broker);
    }
}
