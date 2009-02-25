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

package org.apache.qpid.test.client.failover;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession_0_10;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.FailoverBaseCase;

public class FailoverTest extends FailoverBaseCase implements ConnectionListener
{
    private static final Logger _logger = Logger.getLogger(FailoverTest.class);

    private static final String QUEUE = "queue";
    private static final int DEFAULT_NUM_MESSAGES = 10;
    private static final int DEFAULT_SEED = 20080921;
    private int numMessages = 0;
    private Connection connnection;
    private Session producerSession;
    private Queue queue;
    private MessageProducer producer;
    private Session consumerSession;
    private MessageConsumer consumer;

    private static int usedBrokers = 0;
    private CountDownLatch failoverComplete;
    private static final long DEFAULT_FAILOVER_TIME = 10000L;
    private boolean CLUSTERED = Boolean.getBoolean("profile.clustered");
    private int seed;
    private Random rand;
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        
        numMessages = Integer.getInteger("profile.failoverMsgCount",DEFAULT_NUM_MESSAGES);
        seed = Integer.getInteger("profile.failoverRandomSeed",DEFAULT_SEED);
        rand = new Random(seed);
        
        connnection = getConnection();
        ((AMQConnection) connnection).setConnectionListener(this);
        connnection.start();
        failoverComplete = new CountDownLatch(1);
    }

    private void init(boolean transacted, int mode) throws JMSException, NamingException
    {
        queue = (Queue) getInitialContext().lookup(QUEUE);

        consumerSession = connnection.createSession(transacted, mode);
        consumer = consumerSession.createConsumer(queue);

        producerSession = connnection.createSession(transacted, mode);
        producer = producerSession.createProducer(queue);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            connnection.close();
        }
        catch (Exception e)
        {

        }

        super.tearDown();
    }

    private void consumeMessages(int startIndex,int endIndex, boolean transacted) throws JMSException
    {
        Message msg;
        _logger.debug("**************** Receive (Start: " + startIndex + ", End:" + endIndex + ")***********************");
        
        for (int i = startIndex; i < endIndex; i++)
        {
            msg = consumer.receive(1000);            
            assertNotNull("Message " + i + " was null!", msg);
            
            _logger.debug("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            _logger.debug("Received : " + ((TextMessage) msg).getText());
            _logger.debug("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            
            assertEquals("Invalid message order","message " + i, ((TextMessage) msg).getText());
            
        }
        _logger.debug("***********************************************************");
        
        if (transacted) 
        {
            consumerSession.commit();
        }
    }

    private void sendMessages(int startIndex,int endIndex, boolean transacted) throws JMSException
    {
        _logger.debug("**************** Send (Start: " + startIndex + ", End:" + endIndex + ")***********************");
        
        for (int i = startIndex; i < endIndex; i++)
        {            
            producer.send(producerSession.createTextMessage("message " + i));
            
            _logger.debug("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
            _logger.debug("Sending message"+i);
            _logger.debug("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        }
        
        _logger.debug("***********************************************************");
        
        if (transacted)
        {
            producerSession.commit();
        }
    }

    public void testP2PFailover() throws Exception
    {
        testP2PFailover(numMessages, true,true, false);
    }

    public void testP2PFailoverWithMessagesLeftToConsumeAndProduce() throws Exception
    {
        if (CLUSTERED)
        {
            testP2PFailover(numMessages, false,false, false);
        }
    }
    
    public void testP2PFailoverWithMessagesLeftToConsume() throws Exception
    {
        if (CLUSTERED)
        {    
            testP2PFailover(numMessages, false,true, false);
        }
    }    
     
    public void testP2PFailoverTransacted() throws Exception
    {
        testP2PFailover(numMessages, true,true, false);
    }

    public void testP2PFailoverTransactedWithMessagesLeftToConsumeAndProduce() throws Exception
    {
        // Currently the cluster does not support transactions that span a failover
        if (CLUSTERED)
        {
            testP2PFailover(numMessages, false,false, false);
        }
    }

    private void testP2PFailover(int totalMessages, boolean consumeAll, boolean produceAll , boolean transacted) throws JMSException, NamingException
    {        
        init(transacted, Session.AUTO_ACKNOWLEDGE);
        runP2PFailover(totalMessages,consumeAll, produceAll , transacted);
    } 
    
    private void runP2PFailover(int totalMessages, boolean consumeAll, boolean produceAll , boolean transacted) throws JMSException, NamingException
    {
        Message msg = null;
        int toProduce = totalMessages;
        
        _logger.debug("===================================================================");
        _logger.debug("Total messages used for the test " + totalMessages + " messages");
        _logger.debug("===================================================================");
        
        if (!produceAll)
        {
            toProduce = totalMessages - rand.nextInt(totalMessages);
        }
                
        _logger.debug("==================");
        _logger.debug("Sending " + toProduce + " messages");
        _logger.debug("==================");
        
        sendMessages(0,toProduce, transacted);

        // Consume some messages
        int toConsume = toProduce;
        if (!consumeAll)
        {
            toConsume = toProduce - rand.nextInt(toProduce);         
        }
        
        consumeMessages(0,toConsume, transacted);

        _logger.debug("==================");
        _logger.debug("Consuming " + toConsume + " messages");
        _logger.debug("==================");
        
        _logger.info("Failing over");

        causeFailure(DEFAULT_FAILOVER_TIME);

        if (!CLUSTERED)
        {
            msg = consumer.receive(500);
            assertNull("Should not have received message from new broker!", msg);
        }

        // Check that you produce and consume the rest of messages.
        _logger.debug("==================");
        _logger.debug("Sending " + (totalMessages-toProduce) + " messages");
        _logger.debug("==================");
        
        sendMessages(toProduce,totalMessages, transacted);
        consumeMessages(toConsume,totalMessages, transacted);       
        
        _logger.debug("==================");
        _logger.debug("Consuming " + (totalMessages-toConsume) + " messages");
        _logger.debug("==================");
    }

    private void causeFailure(long delay)
    {

        failBroker();

        _logger.info("Awaiting Failover completion");
        try
        {
            if (!failoverComplete.await(delay, TimeUnit.MILLISECONDS))
            {
                fail("failover did not complete");
            }
        }
        catch (InterruptedException e)
        {
            //evil ignore IE.
        }
    }
   
    public void testClientAckFailover() throws Exception
    {
        init(false, Session.CLIENT_ACKNOWLEDGE);
        sendMessages(0,1, false);
        Message msg = consumer.receive();
        assertNotNull("Expected msgs not received", msg);

        causeFailure(DEFAULT_FAILOVER_TIME);

        Exception failure = null;
        try
        {
            msg.acknowledge();
        }
        catch (Exception e)
        {
            failure = e;
        }
        assertNotNull("Exception should be thrown", failure);
    }

    /**
     * The client used to have a fixed timeout of 4 minutes after which failover would no longer work.
     * Check that this code has not regressed
     *
     * @throws Exception if something unexpected occurs in the test.
     */
   
    public void test4MinuteFailover() throws Exception
    {
        ConnectionURL connectionURL = getConnectionFactory().getConnectionURL();

        int RETRIES = 4;
        int DELAY = 60000;

        //Set up a long delay on and large number of retries
        BrokerDetails details = connectionURL.getBrokerDetails(1);
        details.setProperty(BrokerDetails.OPTIONS_RETRY, String.valueOf(RETRIES));
        details.setProperty(BrokerDetails.OPTIONS_CONNECT_DELAY, String.valueOf(DELAY));

        connnection = new AMQConnection(connectionURL, null);

        ((AMQConnection) connnection).setConnectionListener(this);

        //Start the connection
        connnection.start();

        long FAILOVER_DELAY = (RETRIES * DELAY);

        // Use Nano seconds as it is more accurate for comparision.
        long failTime = System.nanoTime() + FAILOVER_DELAY * 1000000;

        //Fail the first broker
        causeFailure(FAILOVER_DELAY + DEFAULT_FAILOVER_TIME);

        //Reconnection should occur
        assertTrue("Failover did not take long enough", System.nanoTime() > failTime);
    }

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        return true;
    }

    public boolean preResubscribe()
    {
        return true;
    }

    public void failoverComplete()
    {
        failoverComplete.countDown();
    }
}
