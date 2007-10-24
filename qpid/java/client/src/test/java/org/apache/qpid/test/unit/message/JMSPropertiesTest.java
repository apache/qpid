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

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.NonQpidObjectMessage;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.testutil.QpidTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

/**
 * @author Apache Software Foundation
 */
public class JMSPropertiesTest extends QpidTestCase
{

    private static final Logger _logger = LoggerFactory.getLogger(JMSPropertiesTest.class);

    public String _connectionString = "vm://:1";

    public static final String JMS_CORR_ID = "QPIDID_01";
    public static final int JMS_DELIV_MODE = 1;
    public static final String JMS_TYPE = "test.jms.type";

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testJMSProperties() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(con.getDefaultQueueExchangeName(), new AMQShortString("someQ"), new AMQShortString("someQ"), false,
                true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        AMQConnection con2 = (AMQConnection) getConnection("guest", "guest");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);
        Destination JMS_REPLY_TO = new AMQQueue(con2, "my.replyto");
        // create a test message to send
        ObjectMessage sentMsg = new NonQpidObjectMessage();
        sentMsg.setJMSCorrelationID(JMS_CORR_ID);
        sentMsg.setJMSDeliveryMode(JMS_DELIV_MODE);
        sentMsg.setJMSType(JMS_TYPE);
        sentMsg.setJMSReplyTo(JMS_REPLY_TO);

        // send it
        producer.send(sentMsg);

        con2.close();

        con.start();

        // get message and check JMS properties
        ObjectMessage rm = (ObjectMessage) consumer.receive(2000);
        assertNotNull(rm);

        assertEquals("JMS Correlation ID mismatch", sentMsg.getJMSCorrelationID(), rm.getJMSCorrelationID());
        // TODO: Commented out as always overwritten by send delivery mode value - prob should not set in conversion
        // assertEquals("JMS Delivery Mode mismatch",sentMsg.getJMSDeliveryMode(),rm.getJMSDeliveryMode());
        assertEquals("JMS Type mismatch", sentMsg.getJMSType(), rm.getJMSType());
        assertEquals("JMS Reply To mismatch", sentMsg.getJMSReplyTo(), rm.getJMSReplyTo());

        con.close();
    }

}
