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

import junit.framework.TestCase;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.BasicMessageProducer;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.message.JMSTextMessage;

import javax.jms.*;

/**
 * @author Apache Software Foundation
 */
public class MessageRefTest extends TestCase
{
    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testOneWayRef() throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test");
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test");
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();        
        
        producer.sendAsRef(session1.createTextMessage("Hello ref"));
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals("Hello ref", tm1.getText());
        
        con2.close();
        con1.close();
    }
    
    public void testOneWayRefAppend() throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test");
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test");
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();        
        
        String refId = producer.openRef();
        producer.transferRef(refId, ((JMSTextMessage)session1.createTextMessage()).getMessageHeaders());
        producer.appendRef(refId, new String("ABC").getBytes());
        producer.appendRef(refId, new String("123").getBytes());
        producer.appendRef(refId, new String("").getBytes());
        producer.appendRef(refId, new String("DEF").getBytes());
        producer.appendRef(refId, new String("456").getBytes());
        producer.closeRef(refId);
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals("ABC123DEF456", tm1.getText());
        
        con2.close();
        con1.close();
    }

    public void testTwoWayRef()  throws Exception
    {
        // Set frame size to 1000 and send message of 2500
        ConnectionTuneParameters tp = new ConnectionTuneParameters();
        tp.setFrameMax(1000L);
        tp.setChannelMax(32767);
        tp.setHeartbeat(600);
        String message = createMessage(2500);
        
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test", tp);
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        AMQConnection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test", tp);
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();
               
        producer.send(session1.createTextMessage(message));
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals(message, tm1.getText());
        
        con2.close();
        con1.close();
    }

    public void testUpSmallDownBig() throws Exception
    {
        ConnectionTuneParameters tp1 = new ConnectionTuneParameters();
        tp1.setFrameMax(1000L);
        tp1.setChannelMax(32767);
        tp1.setHeartbeat(600);
        ConnectionTuneParameters tp2 = new ConnectionTuneParameters();
        tp2.setFrameMax(2000L);
        tp2.setChannelMax(32767);
        tp2.setHeartbeat(600);
        String message = createMessage(2500);
        
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test", tp1);
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        AMQConnection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test", tp2);
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();
               
        producer.send(session1.createTextMessage(message));
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals(message, tm1.getText());
        
        con2.close();
        con1.close();
    }

    //*** Uncomment this test when the rechunking code has been included in AMQChannel.deliver() ***
    /* public void testUpBigDownSmall() throws Exception
    {
        ConnectionTuneParameters tp1 = new ConnectionTuneParameters();
        tp1.setFrameMax(2000L);
        tp1.setChannelMax(32767);
        tp1.setHeartbeat(600);
        ConnectionTuneParameters tp2 = new ConnectionTuneParameters();
        tp2.setFrameMax(1000L);
        tp2.setChannelMax(32767);
        tp2.setHeartbeat(600);
        String message = createMessage(2500);
        
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test", tp1);
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        AMQConnection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test", tp2);
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();
               
        producer.send(session1.createTextMessage(message));
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals(message, tm1.getText());
        
        con2.close();
        con1.close();
    } */
    
    public void testInterleavedRefs() throws Exception
    {        
        ConnectionTuneParameters tp = new ConnectionTuneParameters();
        tp.setFrameMax(1000L);
        tp.setChannelMax(32767);
        tp.setHeartbeat(600);
        String message = createMessage(500);
        
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test");
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        AMQConnection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test", tp);
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();
               
        String refId1 = producer.openRef();
        String refId2 = producer.openRef();
        producer.transferRef(refId1, ((JMSTextMessage)session1.createTextMessage()).getMessageHeaders());
        producer.transferRef(refId2, ((JMSTextMessage)session1.createTextMessage()).getMessageHeaders());
        producer.appendRef(refId1, message.getBytes());
        producer.appendRef(refId2, message.getBytes());
        String refId3 = producer.openRef();
        producer.appendRef(refId1, message.getBytes());
        producer.transferRef(refId3, ((JMSTextMessage)session1.createTextMessage()).getMessageHeaders());
        producer.appendRef(refId3, message.getBytes());
        producer.appendRef(refId3, message.getBytes());
        producer.appendRef(refId1, message.getBytes());
        producer.closeRef(refId1);
        producer.appendRef(refId3, message.getBytes());
        producer.appendRef(refId3, message.getBytes());
        producer.closeRef(refId2);
        producer.appendRef(refId3, message.getBytes());
        producer.closeRef(refId3);
        
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals(message + message + message, tm1.getText());
        TextMessage tm2 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm2);
        assertEquals(message, tm2.getText());
        TextMessage tm3 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm3);
        assertEquals(message + message + message + message + message, tm3.getText());
        
        con2.close();
        con1.close();
    }
    
    public void testEmptyContentRef() throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con1 = new AMQConnection("vm://:1", "guest", "guest", "Client1", "test");
        AMQSession session1 = con1.createAMQSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        BasicMessageProducer producer = session1.createBasicProducer(topic);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "Client2", "test");
        Session session2 = con2.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session2.createConsumer(topic);
        con2.start();        
        
        String refId = producer.openRef();
        producer.transferRef(refId, ((JMSTextMessage)session1.createTextMessage()).getMessageHeaders());
        producer.closeRef(refId);
        TextMessage tm1 = (TextMessage) consumer.receive(2000);
        assertNotNull(tm1);
        assertEquals("", tm1.getText());
        
        con2.close();
        con1.close();
    }
    
    // Utility to create message "012345678901234567890..." for length len chars.
    private String createMessage(int len)
    {
        StringBuffer sb = new StringBuffer(len);
        for (int i=0; i<len; i++)
            sb.append(i%10);
        return sb.toString();
    }
}
