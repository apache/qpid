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
package org.apache.qpid.topic;

import java.util.ArrayList;
import java.util.HashMap;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.naming.NamingException;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class TopicWithSelectorsTransientVolumeTest extends QpidBrokerTestCase
{   
    private static final int NUM_MSG_PER_ITERATION = 50;//must be a multiple of 10
    private static final int NUM_ITERATIONS = 1000;
    
    private static final int NUM_CONSUMERS = 50;
    private static final int MSG_SIZE = 1024;
    private static final byte[] BYTE_ARRAY = new byte[MSG_SIZE];
    
    ArrayList<MyMessageSubscriber> _subscribers = new ArrayList<MyMessageSubscriber>();
    HashMap<String,Long> _queueMsgCounts = new HashMap<String,Long>();
    
    private final static Object _lock=new Object();
    private boolean _producerFailed;
    private static int _finishedCount;
    private static int _failedCount;
    
    protected void setUp() throws Exception
    {
        super.setUp();
        init();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }
    
    private void init()
    {
        _finishedCount = 0;
        _failedCount = 0;
        _producerFailed = false;
        _subscribers.clear();
        _queueMsgCounts.clear();
    }
    
    
    private Message createMessage(Session session) throws JMSException
    {
        BytesMessage message = session.createBytesMessage();
        message.writeBytes(BYTE_ARRAY);
        
        return message;
    }
    
    /**
     * 1 Topic with 50 subscribers using a selector, and 1 producer sending 50,000 1K messages with 90% selector success ratio.
     */
    public void test50SubscribersWith90PercentMatched() throws Exception
    {
        Topic topic = new AMQTopic(ExchangeDefaults.TOPIC_EXCHANGE_NAME, "test50ConsumersWith10PercentUnmatched");

        System.out.println("Creating consumers");

        MyMessageSubscriber sub;

        for(int i=1; i <= NUM_CONSUMERS; i++)
        {
            sub = new MyMessageSubscriber(topic, "consumer" + i, ((9 * NUM_MSG_PER_ITERATION * NUM_ITERATIONS) / 10));
            _subscribers.add(sub);
        }
        
        System.out.println("Starting consumers");
        for(MyMessageSubscriber s: _subscribers)
        {
            Thread consumer = new Thread(s);
            consumer.start();
        }

        System.out.println("Creating producer");
        MyMessageProducer prod = new MyMessageProducer(topic);
        
        long startTime = System.currentTimeMillis();
        
        System.out.println("Starting producer");
        Thread producer = new Thread(prod);
        producer.start();
        
        
        // Wait for all the messageConsumers to have finished or failed
        synchronized (_lock)
        {
            while (_finishedCount + _failedCount < NUM_CONSUMERS)
            {
                try
                {
                    _lock.wait();
                }
                catch (InterruptedException e)
                {
                    //ignore
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("Elapsed time for messaging: " + (endTime-startTime) + "ms");
        
        assertFalse("Producer failed to send all messages", _producerFailed);
        
        //check if all messages received by consumers, or if there were failures
        if (_finishedCount != NUM_CONSUMERS)
        {
            fail(_failedCount + " consumers did not recieve all their expected messages");
        }
        
        //check if all queue depths were 0
        for(String consumer: _queueMsgCounts.keySet())
        {
            long depth = _queueMsgCounts.get(consumer);
            assertEquals(consumer + " subscription queue msg count was not 0", 0, depth);
        }

    }
    
    private class MyMessageProducer implements Runnable
    {
        private TopicConnection _connection;
        private TopicSession _session;
        private TopicPublisher _messagePublisher;

        public MyMessageProducer(Topic topic) throws JMSException, NamingException
        {
            _connection = (TopicConnection) getConnection();
            _session = (TopicSession) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _messagePublisher = _session.createPublisher(topic);
        }

        public void run()
        {
            try
            {
                for(int iter = 0; iter < NUM_ITERATIONS; iter++)
                {
                    int i = 0;
                    
                    //send 90% matching messages
                    for (; i < (9 * NUM_MSG_PER_ITERATION)/10; i++)
                    {
                        Message message = createMessage(_session);
                        message.setStringProperty("testprop", "true");

                        _messagePublisher.publish(message, DeliveryMode.NON_PERSISTENT, 
                                Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                        Thread.yield();
                    }

                    //send remaining 10% non-matching messages
                    for (; i < NUM_MSG_PER_ITERATION; i++)
                    {
                        Message message = _session.createMessage();
                        message.setStringProperty("testprop", "false");

                        _messagePublisher.publish(message, DeliveryMode.NON_PERSISTENT, 
                                Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                        Thread.yield();
                    }
                }

            }
            catch (Exception exp)
            {
                System.out.println("producer: caught an exception, probably exiting before all messages sent");
                exp.printStackTrace();
                synchronized (_lock)
                {
                    _producerFailed=true;
                    _lock.notifyAll();
                }
            }
        }
    }
    
    
    private class MyMessageSubscriber implements Runnable
    {
        /* The topic this subscriber is subscribing  to */
        private Topic _topic;
        private String _consumerName;
        private int _outstandingMsgCount;
        private TopicConnection _connection;
        private TopicSession _session;
        private TopicSubscriber _durSub;

        public MyMessageSubscriber(Topic topic, String consumerName, int messageCount) throws JMSException, NamingException
        {
            _outstandingMsgCount = messageCount;
            _topic=topic;
            _consumerName = consumerName;
            _connection = (TopicConnection) getConnection();
            _session = (TopicSession) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            _durSub = _session.createDurableSubscriber(_topic, _consumerName,"testprop='true'", false);
            _connection.start();
        }

        public void run()
        {
  
                boolean failed = false;
                do
                {
                    Message m = null;
                    try
                    {
                        m = _durSub.receive(10000);
                    }
                    catch (JMSException exp)
                    {
                        System.out.println(_consumerName + ": caught an exception handling a received message");
                        exp.printStackTrace();

                        failed = true;
                        break;
                    }

                    Thread.yield();

                    _outstandingMsgCount--;
                    
                    if(_outstandingMsgCount % 500 == 0)
                    {
                        System.out.println(_consumerName + ": outstanding message count: " + _outstandingMsgCount);
                    }
                    
                    if(m == null)
                    {
                        if(_outstandingMsgCount != 0)
                        {
                            failed = true;
                        }
                        break;
                    }
                }
                while(_outstandingMsgCount > 0);
                
                System.out.println(_consumerName + ": outstanding message count: " + _outstandingMsgCount);
                
                try
                {
                    AMQQueue subcriptionQueue = new AMQQueue(ExchangeDefaults.TOPIC_EXCHANGE_NAME,"clientid" + ":" + _consumerName);

                    ((AMQSession)_session).sync();
                    Long depth = ((AMQSession)_session).getQueueDepth(subcriptionQueue);
                    _queueMsgCounts.put(_consumerName, depth);
                    
                    System.out.println(_consumerName + ": completion queue msg count: " + depth);
                }
                catch (AMQException exp)
                {
                    System.out.println(_consumerName + ": caught an exception determining completion queue depth");
                    exp.printStackTrace();
                }
                finally
                {
                    try
                    {
                        _session.unsubscribe(_consumerName);
                    }
                    catch (JMSException e)
                    {
                        System.out.println(_consumerName + ": caught an exception whilst unsubscribing");
                        e.printStackTrace();
                    }
                }

                synchronized (_lock)
                {
                    if (_outstandingMsgCount == 0 && !failed)
                    {
                        _finishedCount++;
                        System.out.println(_consumerName + ": finished");
                    }
                    else
                    {
                        _failedCount++;
                        System.out.println(_consumerName + ": failed");
                    }
                    _lock.notifyAll();
                }

        }
    }

    //helper method to allow easily running against an external standalone broker
//    public static void main(String[] args) throws Exception
//    {
//        System.setProperty("broker.config", "/dev/null");
//        System.setProperty("broker", "external");
//        System.setProperty("java.naming.factory.initial", "org.apache.qpid.jndi.PropertiesFileInitialContextFactory");
//        System.setProperty("java.naming.provider.url", "test-profiles/test-provider.properties");
//        
//        TopicWithSelectorsTransientVolumeTest test = new TopicWithSelectorsTransientVolumeTest();
//        test.init();
//        test.test50SubscribersWith90PercentMatched();
//        test.tearDown();
//    }
}
