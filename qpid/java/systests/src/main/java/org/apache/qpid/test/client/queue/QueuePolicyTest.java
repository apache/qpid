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
package org.apache.qpid.test.client.queue;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueuePolicyTest extends QpidBrokerTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(QueuePolicyTest.class);
    private Connection _connection;
    
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection() ;
        _connection.start();
    }
    
    @Override
    public void tearDown() throws Exception
    {
        _connection.close();
        super.tearDown();
    }
    
    public void testRejectPolicy() throws Exception
    {
        String addr = "ADDR:queue; {create: always, " +
        "node: {x-bindings: [{exchange : 'amq.direct', key : test}], " +
        "x-declare:{ arguments : {'qpid.max_count':5} }}}";
                
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        
        Destination dest = ssn.createQueue(addr);
        MessageConsumer consumer = ssn.createConsumer(dest);
        MessageProducer prod = ssn.createProducer(ssn.createQueue("ADDR:amq.direct/test"));
        
        for (int i=0; i<6; i++)
        {
            prod.send(ssn.createMessage());
        }
        
        try
        {   
            prod.send(ssn.createMessage());
            ((AMQSession)ssn).sync();
            fail("The client did not receive an exception after exceeding the queue limit");
        }
        catch (AMQException e)
        {
           assertTrue("The correct error code is not set",e.getErrorCode().toString().contains("506"));
        }
    }
    
    public void testRingPolicy() throws Exception
    {
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        
        String addr = "ADDR:my-ring-queue; {create: always, " +
        "node: {x-bindings: [{exchange : 'amq.direct', key : test}], " +
               "x-declare:{arguments : {'qpid.policy_type':ring, 'qpid.max_count':2} }}}";
    
        Destination dest = ssn.createQueue(addr);
        MessageConsumer consumer = ssn.createConsumer(dest);
        MessageProducer prod = ssn.createProducer(ssn.createQueue("ADDR:amq.direct/test"));
        
        prod.send(ssn.createTextMessage("Test1"));
        prod.send(ssn.createTextMessage("Test2"));
        prod.send(ssn.createTextMessage("Test3"));
        
        TextMessage msg = (TextMessage)consumer.receive(1000);
        assertEquals("The consumer should receive the msg with body='Test2'",msg.getText(),"Test2");
        
        msg = (TextMessage)consumer.receive(1000);
        assertEquals("The consumer should receive the msg with body='Test3'",msg.getText(),"Test3");
     
        prod.send(ssn.createTextMessage("Test4"));
        assertEquals("The consumer should receive the msg with body='Test4'",msg.getText(),"Test3");
    }
}
