/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.test.unit.close;

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicInteger;


import javax.jms.ExceptionListener;
import javax.jms.Session;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.MessageProducer;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.MessageConsumer;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

public class MessageRequeueTest extends TestCase
{

    private static final Logger _logger = Logger.getLogger(MessageRequeueTest.class);

    protected static AtomicInteger consumerIds = new AtomicInteger(0);
    protected final Integer numTestMessages = 1000;

    protected final int consumeTimeout = 3000;

    protected final String queue = "direct://amq.direct//queue";
    protected String payload = "Message:";

    protected final String BROKER = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);

        _logger.error("Forcing AMQSession to ERROR level, to hide details of messages received after consumer has been closed QPID-XXX");
        Logger session = Logger.getLogger("org.apache.qpid.client.AMQSession");
        session.setLevel(Level.ERROR);

        QpidClientConnection conn = new QpidClientConnection();

        conn.connect();
        // clear queue
        conn.consume(queue, consumeTimeout);
        // load test data
        _logger.info("creating test data, " + numTestMessages + " messages");
        conn.put(queue, payload, numTestMessages);
        // close this connection
        conn.disconnect();
    } 

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killVMBroker(1);
    }

    /** multiple consumers */
    public void testTwoCompetingConsumers()
    {
        Consumer c1 = new Consumer();
        Consumer c2 = new Consumer();
        Consumer c3 = new Consumer();
//        Consumer c4 = new Consumer();

        Thread t1 = new Thread(c1);
        Thread t2 = new Thread(c2);
        Thread t3 = new Thread(c3);
//        Thread t4 = new Thread(c4);

        t1.start();
        t2.start();
        t3.start();
//        t4.start();

        try
        {
            t1.join();
            t2.join();
            t3.join();
//            t4.join();
        }
        catch (InterruptedException e)
        {
            fail("Uanble to join to Consumer theads");
        }

        _logger.info("consumer 1 count is " + c1.getCount());
        _logger.info("consumer 2 count is " + c2.getCount());
        _logger.info("consumer 3 count is " + c3.getCount());
//        _logger.info("consumer 4 count is " + c4.getCount());

        Integer totalConsumed = c1.getCount() + c2.getCount() + c3.getCount();// + c4.getCount();
        assertEquals("number of consumed messages does not match initial data", numTestMessages, totalConsumed);
    }

    class Consumer implements Runnable
    {
        private Integer count = 0;
        private Integer id;

        public Consumer()
        {
            id = consumerIds.addAndGet(1);
        }

        public void run()
        {
            try
            {
                _logger.info("consumer-" + id + ": starting");
                QpidClientConnection conn = new QpidClientConnection();

                conn.connect();

                _logger.info("consumer-" + id + ": connected, consuming...");
                String result;
                do
                {
                    result = conn.getNextMessage(queue, consumeTimeout);
                    if (result != null)
                    {
                        count++;
                        if (count % 100 == 0)
                        {
                            _logger.info("consumer-" + id + ": got " + result + ", new count is " + count);
                        }
                    }
                }
                while (result != null);

                _logger.info("consumer-" + id + ": complete");
                conn.disconnect();

            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public Integer getCount()
        {
            return count;
        }

        public Integer getId()
        {
            return id;
        }
    }


    public class QpidClientConnection implements ExceptionListener
    {
        private boolean transacted = true;
        private int ackMode = Session.CLIENT_ACKNOWLEDGE;
        private Connection connection;

        private String virtualHost;
        private String brokerlist;
        private int prefetch;
        protected Session session;
        protected boolean connected;

        public QpidClientConnection()
        {
            super();
            setVirtualHost("/test");
            setBrokerList(BROKER);
            setPrefetch(5000);
        }


        public void connect() throws JMSException
        {
            if (!connected)
            {
                /*
                * amqp://[user:pass@][clientid]/virtualhost?
                * brokerlist='[transport://]host[:port][?option='value'[&option='value']];'
                * [&failover='method[?option='value'[&option='value']]']
                * [&option='value']"
                */
                String brokerUrl = "amqp://guest:guest@" + virtualHost + "?brokerlist='" + brokerlist + "'";
                try
                {
                    AMQConnectionFactory factory = new AMQConnectionFactory(new AMQConnectionURL(brokerUrl));
                    _logger.info("connecting to Qpid :" + brokerUrl);
                    connection = factory.createConnection();

                    // register exception listener
                    connection.setExceptionListener(this);

                    session = ((AMQConnection) connection).createSession(transacted, ackMode, prefetch);


                    _logger.info("starting connection");
                    connection.start();

                    connected = true;
                }
                catch (URLSyntaxException e)
                {
                    throw new JMSException("URL syntax error in [" + brokerUrl + "]: " + e.getMessage());
                }
            }
        }

        public void disconnect() throws JMSException
        {
            if (connected)
            {
                session.commit();
                session.close();
                connection.close();
                connected = false;
                _logger.info("disconnected");
            }
        }

        public void disconnectWithoutCommit() throws JMSException
        {
            if (connected)
            {
                session.close();
                connection.close();
                connected = false;
                _logger.info("disconnected without commit");
            }
        }

        public String getBrokerList()
        {
            return brokerlist;
        }

        public void setBrokerList(String brokerlist)
        {
            this.brokerlist = brokerlist;
        }

        public String getVirtualHost()
        {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost)
        {
            this.virtualHost = virtualHost;
        }

        public void setPrefetch(int prefetch)
        {
            this.prefetch = prefetch;
        }


        /** override as necessary */
        public void onException(JMSException exception)
        {
            _logger.info("ExceptionListener event: error " + exception.getErrorCode() + ", message: " + exception.getMessage());
        }

        public boolean isConnected()
        {
            return connected;
        }

        public Session getSession()
        {
            return session;
        }

        /**
         * Put a String as a text messages, repeat n times. A null payload will result in a null message.
         *
         * @param queueName The queue name to put to
         * @param payload   the content of the payload
         * @param copies    the number of messages to put
         *
         * @throws javax.jms.JMSException any exception that occurs
         */
        public void put(String queueName, String payload, int copies) throws JMSException
        {
            if (!connected)
            {
                connect();
            }

            _logger.info("putting to queue " + queueName);
            Queue queue = session.createQueue(queueName);

            final MessageProducer sender = session.createProducer(queue);

            for (int i = 0; i < copies; i++)
            {
                Message m = session.createTextMessage(payload + i);
                sender.send(m);
            }

            session.commit();
            sender.close();
            _logger.info("put " + copies + " copies");
        }

        /**
         * GET the top message on a queue. Consumes the message. Accepts timeout value.
         *
         * @param queueName   The quename to get from
         * @param readTimeout The timeout to use
         *
         * @return the content of the text message if any
         *
         * @throws javax.jms.JMSException any exception that occured
         */
        public String getNextMessage(String queueName, long readTimeout) throws JMSException
        {
            if (!connected)
            {
                connect();
            }

            Queue queue = session.createQueue(queueName);

            final MessageConsumer consumer = session.createConsumer(queue);

            Message message = consumer.receive(readTimeout);
            session.commit();
            consumer.close();

            String result;

            // all messages we consume should be TextMessages
            if (message instanceof TextMessage)
            {
                result = ((TextMessage) message).getText();
            }
            else if (null == message)
            {
                result = null;
            }
            else
            {
                _logger.info("warning: received non-text message");
                result = message.toString();
            }

            return result;
        }

        /**
         * GET the top message on a queue. Consumes the message.
         *
         * @param queueName The Queuename to get from
         *
         * @return The string content of the text message, if any received
         *
         * @throws javax.jms.JMSException any exception that occurs
         */
        public String getNextMessage(String queueName) throws JMSException
        {
            return getNextMessage(queueName, 0);
        }

        /**
         * Completely clears a queue. For readTimeout behaviour see Javadocs for javax.jms.MessageConsumer.
         *
         * @param queueName   The Queue name to consume from
         * @param readTimeout The timeout for each consume
         *
         * @throws javax.jms.JMSException Any exception that occurs during the consume
         * @throws InterruptedException   If the consume thread was interrupted during a consume.
         */
        public void consume(String queueName, int readTimeout) throws JMSException, InterruptedException
        {
            if (!connected)
            {
                connect();
            }

            _logger.info("consuming queue " + queueName);
            Queue queue = session.createQueue(queueName);

            final MessageConsumer consumer = session.createConsumer(queue);
            int messagesReceived = 0;

            _logger.info("consuming...");
            while ((consumer.receive(readTimeout)) != null)
            {
                messagesReceived++;
            }

            session.commit();
            consumer.close();
            _logger.info("consumed: " + messagesReceived);
        }
    }

}