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
package org.apache.qpid.test.unit.close;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class MessageConsumerCloseTest  extends QpidBrokerTestCase
{
    private volatile Exception _exception;

    public void testConsumerCloseAndSessionRollback() throws Exception
    {
        Connection connection = getConnection();
        final CountDownLatch receiveLatch = new CountDownLatch(1);
        final Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination destination = getTestQueue();
        MessageConsumer consumer = session.createConsumer(destination);
        sendMessage(session, destination, 2);
        connection.start();
        consumer.setMessageListener(new MessageListener()
        {
            @Override
            public void onMessage(Message message)
            {
                try
                {
                    receiveLatch.countDown();
                    session.rollback();
                }
                catch (JMSException e)
                {
                    _exception = e;
                }
            }
        });
        boolean messageReceived = receiveLatch.await(1l, TimeUnit.SECONDS);
        consumer.close();

        assertNull("Exception occurred on rollback:" + _exception, _exception);
        assertTrue("Message is not received", messageReceived);

        consumer = session.createConsumer(destination);
        Message message1 = consumer.receive(1000l);
        assertNotNull("message1 is not received", message1);
        Message message2 = consumer.receive(1000l);
        assertNotNull("message2 is not received", message2);
    }

    public void testPrefetchedMessagesReleasedOnConsumerClose() throws Exception
    {
        Connection connection = getConnection();
        final Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

        Destination destination = getTestQueue();
        MessageConsumer consumer = session.createConsumer(destination);

        sendMessage(session, destination, 3);

        connection.start();

        Message msg1 = consumer.receive(1000);
        assertNotNull("Message one was null", msg1);
        assertEquals("Message one has unexpected content", 0, msg1.getIntProperty(INDEX));
        session.commit();

        // Messages two and three will have been prefetched by the consumer.
        // Closing the consumer must make the available for delivery elsewhere

        consumer.close();

        MessageConsumer consumer2 = session.createConsumer(destination);

        Message msg2 = consumer2.receive(1000);
        Message msg3 = consumer2.receive(1000);
        assertNotNull("Message two was null", msg2);
        assertEquals("Message two has unexpected content", 1, msg2.getIntProperty(INDEX));

        assertNotNull("Message three was null", msg3);
        assertEquals("Message three has unexpected content", 2, msg3.getIntProperty(INDEX));
        session.commit();
    }
}
