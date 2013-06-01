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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQHeadersExchange;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageEOFException;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.StreamMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Apache Software Foundation
 */
public class StreamMessageTest extends QpidBrokerTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(StreamMessageTest.class);

    public void testStreamMessageEOF() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        AMQHeadersExchange queue =
            new AMQHeadersExchange(new AMQBindingURL(
                    ExchangeDefaults.HEADERS_EXCHANGE_CLASS + "://" + ExchangeDefaults.HEADERS_EXCHANGE_NAME
                    + "/test/queue1?" + BindingURL.OPTION_ROUTING_KEY + "='F0000=1'"));

        FieldTable ft = new FieldTable();
        ft.setString("x-match", "any");
        ft.setString("F1000", "1");
        consumerSession.declareAndBind(queue, ft);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        // force synch to ensure the consumer has resulted in a bound queue
        // ((AMQSession) consumerSession).declareExchangeSynch(ExchangeDefaults.HEADERS_EXCHANGE_NAME, ExchangeDefaults.HEADERS_EXCHANGE_CLASS);
        // This is the default now

        Connection con2 = (AMQConnection) getConnection("guest", "guest");

        AMQSession producerSession = (AMQSession) con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        // Need to start the "producer" connection in order to receive bounced messages
        _logger.info("Starting producer connection");
        con2.start();

        MessageProducer mandatoryProducer = producerSession.createProducer(queue);

        // Third test - should be routed
        _logger.info("Sending isBound message");
        StreamMessage msg = producerSession.createStreamMessage();

        msg.setStringProperty("F1000", "1");

        msg.writeByte((byte) 42);

        mandatoryProducer.send(msg);

        _logger.info("Starting consumer connection");
        con.start();

        StreamMessage msg2 = (StreamMessage) consumer.receive(2000);
        assertNotNull(msg2);

        msg2.readByte();
        try
        {
            msg2.readByte();
            fail("Expected exception not thrown");
        }
        catch (Exception e)
        {
            assertTrue("Expected MessageEOFException: " + e, e instanceof MessageEOFException);
        }
        con.close();
        con2.close();
    }

    public void testModifyReceivedMessageExpandsBuffer() throws Exception
    {
        final CountDownLatch awaitMessages = new CountDownLatch(1);
        final AtomicReference<Throwable> listenerCaughtException = new AtomicReference<Throwable>();

        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        AMQQueue queue = new AMQQueue(con.getDefaultQueueExchangeName(), new AMQShortString("testQ"));
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        consumer.setMessageListener(new MessageListener()
            {

                public void onMessage(Message message)
                {
                    final StreamMessage sm = (StreamMessage) message;
                    try
                    {
                        sm.clearBody();
                        // it is legal to extend a stream message's content
                        sm.writeString("dfgjshfslfjshflsjfdlsjfhdsljkfhdsljkfhsd");
                    }
                    catch (Throwable t)
                    {
                        listenerCaughtException.set(t);
                    }
                    finally
                    {
                        awaitMessages.countDown();
                    }
                }
            });

        Connection con2 = (AMQConnection) getConnection("guest", "guest");
        AMQSession producerSession = (AMQSession) con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);
        con.start();
        StreamMessage sm = producerSession.createStreamMessage();
        sm.writeInt(42);
        producer.send(sm);

        // Allow up to five seconds for the message to arrive with the consumer
        final boolean completed = awaitMessages.await(5, TimeUnit.SECONDS);
        assertTrue("Message did not arrive with consumer within a reasonable time", completed);
        final Throwable listenerException = listenerCaughtException.get();
        assertNull("No exception should be caught by listener : " + listenerException, listenerException);

        con.close();
        con2.close();
    }
}
