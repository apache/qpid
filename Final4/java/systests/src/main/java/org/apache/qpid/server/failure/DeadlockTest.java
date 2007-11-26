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

package org.apache.qpid.server.failure;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * DeadlockTestCase:
 *   From a client requirement.
 *
 *  The JMS Spec specifies that a Session has a single thread of control. And as such setting message listeners from a
 * second thread is not allowed.
 * Section  4.4.6  of the Spec states:
  <quote>Another consequence is that a connection must be in stopped mode to set up a
session with more than one message listener. The reason is that when a
connection is actively delivering messages, once the first message listener for a
session has been registered, the session is now controlled by the thread of
control that delivers messages to it. At this point a client thread of control
cannot be used to further configure the session.</quote>
 *
 * It, however, does not specified what we should do in the case. it only states:
 <quote>Once a connection has been started, all its sessions with a registered message
listener are dedicated to the thread of control that delivers messages to them. It
is erroneous for client code to use such a session from another thread of
control. The only exception to this is the use of the session or connection close
method.</quote>
 *
 * While it may be erroneous the causing a Deadlock is not a very satisfactory solution. This test ensures that we do
 * no do this. There is no technical reason we cannot currently allow the setting of a messageListener on a new consumer.
 * The only caveate is due to QPID-577 there is likely to be temporary message 'loss'. As they are stuck on the internal
 * _synchronousQueue pending a synchronous receive. 
 *  
 */
public class DeadlockTest extends TestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(DeadlockTest.class);


    public static final String QPID_BROKER_CONNECTION_PROPERTY = "QPIDBROKER";

    private String topic1 = "TEST.DeadLock1.TMP";
    private String topic2 = "TEST.DeadLock2.TMP";

    private Session sess;

    private Semaphore s = new Semaphore(0);
    private final String LOCAL = "tcp://localhost:5670";
    private final String VM = "vm://:1";

    private String BROKER = VM;

    String connectionString = System.getProperty(QPID_BROKER_CONNECTION_PROPERTY,
                                                 "amqp://guest:guest@/test?brokerlist='" + BROKER + "'");


    public void setUp() throws AMQVMBrokerCreationException
    {
        if (BROKER.equals("vm://:1"))
        {
            TransportConnection.createVMBroker(1);
        }
    }

    public void tearDown() throws AMQVMBrokerCreationException
    {
        if (BROKER.equals("vm://:1"))
        {
            TransportConnection.killAllVMBrokers();
        }
    }

    public class EmptyMessageListener implements javax.jms.MessageListener
    {
        public void onMessage(Message message)
        {
            // do nothing
        }
    }

    public void setSessionListener(String topic, javax.jms.MessageListener listener)
    {
        try
        {
            Topic jmsTopic = sess.createTopic(topic);
            MessageConsumer subscriber = sess.createConsumer(jmsTopic);
            subscriber.setMessageListener(listener);
        }
        catch (JMSException e)
        {
            e.printStackTrace();
            fail("Caught JMSException");
        }
    }

    public class TestMessageListener implements javax.jms.MessageListener
    {
        public Random r = new Random();

        public void onMessage(Message message)
        {
            if (r.nextBoolean())
            {
                setSessionListener(topic2, new EmptyMessageListener());
            }
        }

    }

    public void testDeadlock() throws InterruptedException, URLSyntaxException, JMSException
    {
        // in order to trigger the deadlock we need to
        // set a message listener from one thread
        // whilst receiving a message on another thread and on that thread also setting (the same?) message listener
        AMQConnectionFactory acf = new AMQConnectionFactory(connectionString);
        Connection conn = acf.createConnection();
        conn.start();
        sess = conn.createSession(false, org.apache.qpid.jms.Session.NO_ACKNOWLEDGE);
        setSessionListener(topic1, new TestMessageListener());


        Thread th = new Thread()
        {
            public void run()
            {
                try
                {
                    Topic jmsTopic = sess.createTopic(topic1);
                    MessageProducer producer = sess.createProducer(jmsTopic);
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    Random r = new Random();
                    long end = System.currentTimeMillis() + 2000;
                    while (end - System.currentTimeMillis() > 0)
                    {
                        if (r.nextBoolean())
                        {
                            _logger.info("***************** send message");
                            Message jmsMessage = sess.createTextMessage("");
                            producer.send(jmsMessage);
                        }
                        else
                        {
                            _logger.info("***************** set session listener");
                            setSessionListener(topic2, new EmptyMessageListener());
                        }
                        Thread.yield();
                    }
                    _logger.info("done sends");
                    s.release();
                }
                catch (JMSException e)
                {
                    e.printStackTrace();
                    fail("Caught JMSException");
                }
            }
        };
        th.setDaemon(true);
        th.setName("testDeadlock");
        th.start();

        boolean success = s.tryAcquire(1, 4, TimeUnit.SECONDS);

        // if we failed, closing the connection will just hang the test case.
        if (success)
        {
            conn.close();
        }

        if (!success)
        {
            fail("Deadlock ocurred");
        }
    }
}
