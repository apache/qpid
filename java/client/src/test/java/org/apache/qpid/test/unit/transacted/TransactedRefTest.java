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
package org.apache.qpid.test.unit.transacted;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.BasicMessageProducer;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.testutil.VMBrokerSetup;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.mina.util.SessionLog;
import org.apache.log4j.Logger;

import javax.jms.*;

import junit.framework.TestCase;

public class TransactedRefTest extends TestCase
{
    private ConnectionTuneParameters tp;
    private String message;
    private AMQQueue queue1;
    private AMQQueue queue2;

    private AMQConnection con;
    private AMQSession session;
    private MessageConsumer consumer1;
    private BasicMessageProducer producer2;

    private AMQConnection prepCon;
    private AMQSession prepSession;
    private BasicMessageProducer prepProducer1;

    private AMQConnection testCon;
    private Session testSession;
    private MessageConsumer testConsumer1;
    private MessageConsumer testConsumer2;
    private static final Logger _logger = Logger.getLogger(TransactedTest.class);

    protected void setUp() throws Exception
    {
        tp = new ConnectionTuneParameters();
        tp.setFrameMax(1000L);
        tp.setChannelMax(32767);
        tp.setHeartbeat(600);
        message = createMessage(2500);
        
        super.setUp();
        TransportConnection.createVMBroker(1);
        queue1 = new AMQQueue(new AMQShortString("Q1"), new AMQShortString("Q1"), false, true);
        queue2 = new AMQQueue("Q2", false);

        con = new AMQConnection("vm://:1", "guest", "guest", "TransactedTest", "test", tp);
        session = con.createAMQSession(true, 0);
        consumer1 = session.createConsumer(queue1);
        //Dummy just to create the queue. 
        MessageConsumer consumer2 = session.createConsumer(queue2);
        consumer2.close();
        producer2 = session.createBasicProducer(queue2);
        con.start();

        prepCon = new AMQConnection("vm://:1", "guest", "guest", "PrepConnection", "test", tp);
        prepSession = prepCon.createAMQSession(false, AMQSession.NO_ACKNOWLEDGE);
        prepProducer1 = prepSession.createBasicProducer(queue1);
        prepCon.start();
        
        //add some messages
        prepProducer1.sendAsRef(prepSession.createTextMessage("A" + message));
        prepProducer1.sendAsRef(prepSession.createTextMessage("B" + message));
        prepProducer1.sendAsRef(prepSession.createTextMessage("C" + message));
        
        producer2.sendAsRef(session.createTextMessage("X" + message));
        producer2.sendAsRef(session.createTextMessage("Y" + message));
        producer2.sendAsRef(session.createTextMessage("Z" + message));
    }

    protected void tearDown() throws Exception
    {
        testCon.close();
        con.close();
        prepCon.close();
        TransportConnection.killAllVMBrokers();
        super.tearDown();
    }

    public void testCommit() throws Exception
    {
        expect("A" + message, consumer1.receive(1000));
        expect("B" + message, consumer1.receive(1000));
        expect("C" + message, consumer1.receive(1000));

        //commit
        session.commit();

        //ensure sent messages can be received and received messages are gone

        testCon = new AMQConnection("vm://:1", "guest", "guest", "TestConnection", "test", tp);
        testSession = testCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        testConsumer1 = testSession.createConsumer(queue1);
        testConsumer2 = testSession.createConsumer(queue2);
        testCon.start();
        
        expect("X" + message, testConsumer2.receive(1000));
        expect("Y" + message, testConsumer2.receive(1000));
        expect("Z" + message, testConsumer2.receive(1000));

        testConsumer1 = testSession.createConsumer(queue1);
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }
    
    // This checks that queue Q1 is in fact empty and does not have any stray
    // messages left over from the last test (which can affect later tests)...
    public void testEmpty1() throws Exception
    {
        assertTrue(null == consumer1.receive(1000));
    }

    public void testRollback() throws Exception
    {
        expect("A" + message, consumer1.receive(1000));
        expect("B" + message, consumer1.receive(1000));
        expect("C" + message, consumer1.receive(1000));

        //rollback
        session.rollback();

        //ensure sent messages are not visible and received messages are requeued
        expect("A" + message, consumer1.receive(1000));
        expect("B" + message, consumer1.receive(1000));
        expect("C" + message, consumer1.receive(1000));

        //commit
        //session.commit();


        testCon = new AMQConnection("vm://:1", "guest", "guest", "TestConnection", "test", tp);
        testSession = testCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        testConsumer1 = testSession.createConsumer(queue1);
        testConsumer2 = testSession.createConsumer(queue2);
        testCon.start();
        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }
      
    // This checks that queue Q1 is in fact empty and does not have any stray
    // messages left over from the last test (which can affect later tests)...
    public void testEmpty2() throws Exception
    {
        assertTrue(null == consumer1.receive(1000));
    }

    private void expect(String text, Message msg) throws JMSException
    {
        assertTrue(msg instanceof TextMessage);
        assertEquals(text, ((TextMessage) msg).getText());
    }
    
    // Utility to create message "012345678901234567890..." for length len chars.
    private String createMessage(int len)
    {
        StringBuffer sb = new StringBuffer(len);
        for (int i=0; i<len; i++)
            sb.append(i%10);
        return sb.toString();
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TransactedTest.class);
    }
}
