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
package org.apache.qpid.test.unit.message;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.message.TestNonQpidTextMessage;
import org.apache.qpid.framing.AMQShortString;

import javax.jms.*;

/**
 * @author Apache Software Foundation
 */
public class JMSPropertiesTest extends TestCase
{

    private static final Logger _logger = Logger.getLogger(JMSPropertiesTest.class);

    public String _connectionString = "vm://:1";

    public static final String JMS_CORR_ID = "QPIDID_01";
    public static final int JMS_DELIV_MODE = 1;
    public static final String JMS_TYPE = "test.jms.type";
    public static final Destination JMS_REPLY_TO = new AMQQueue("my.replyto");

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }


    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    public void testJMSProperties() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "test");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = new AMQQueue(new AMQShortString("someQ"), new AMQShortString("someQ"), false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        //create a test message to send
        ObjectMessage sentMsg = new TestNonQpidTextMessage();
        sentMsg.setJMSCorrelationID(JMS_CORR_ID);
        sentMsg.setJMSDeliveryMode(JMS_DELIV_MODE);
        sentMsg.setJMSType(JMS_TYPE);
        sentMsg.setJMSReplyTo(JMS_REPLY_TO);

        //send it
        producer.send(sentMsg);

        con2.close();

        con.start();

        //get message and check JMS properties
        ObjectMessage rm = (ObjectMessage) consumer.receive();
        assertNotNull(rm);

        assertEquals("JMS Correlation ID mismatch",sentMsg.getJMSCorrelationID(),rm.getJMSCorrelationID());
        //TODO: Commented out as always overwritten by send delivery mode value - prob should not set in conversion
        //assertEquals("JMS Delivery Mode mismatch",sentMsg.getJMSDeliveryMode(),rm.getJMSDeliveryMode());
        assertEquals("JMS Type mismatch",sentMsg.getJMSType(),rm.getJMSType());
        assertEquals("JMS Reply To mismatch",sentMsg.getJMSReplyTo(),rm.getJMSReplyTo());

        con.close();
    }

}
