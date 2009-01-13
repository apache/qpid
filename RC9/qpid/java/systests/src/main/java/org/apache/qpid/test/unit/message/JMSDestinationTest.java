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

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * @author Apache Software Foundation
 */
public class JMSDestinationTest extends QpidTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSDestinationTest.class);


    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testJMSDestination() throws Exception
    {
        AMQConnection con =  (AMQConnection) getConnection("guest", "guest");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(con.getDefaultQueueExchangeName(), new AMQShortString("someQ"), new AMQShortString("someQ"), false,
                true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        Connection con2 =  (AMQConnection) getConnection("guest", "guest");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        TextMessage sentMsg = producerSession.createTextMessage("hello");
        assertNull(sentMsg.getJMSDestination());

        producer.send(sentMsg);

        assertEquals(sentMsg.getJMSDestination(), queue);

        con2.close();

        con.start();

        TextMessage rm = (TextMessage) consumer.receive();
        assertNotNull(rm);

        assertEquals(rm.getJMSDestination(), queue);
        con.close();
    }

}
