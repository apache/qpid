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
package org.apache.qpid.test.client.message;

import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Tests that {@link Message#setJMSReplyTo(Destination)} can be used to pass a {@link Destination} between
 * messaging clients as is commonly used in request/response messaging pattern implementations.
 */
public class JMSReplyToTest extends QpidBrokerTestCase
{
    private AtomicReference<Throwable> _caughtException = new AtomicReference<Throwable>();
    private Queue _requestQueue;
    private Connection _connection;
    private Session _session;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _requestQueue = startAsyncRespondingJmsConsumerOnSeparateConnection();

        _connection = getConnection();
        _connection.start();
        _session = _connection.createSession(false,  Session.AUTO_ACKNOWLEDGE);
    }

    public void testRequestResponseUsingJmsReplyTo() throws Exception
    {
        final String responseQueueName = getTestQueueName() + ".response";
        Queue replyToQueue = _session.createQueue(responseQueueName);
        sendRequestAndValidateResponse(replyToQueue);
    }

    public void testRequestResponseUsingTemporaryJmsReplyTo() throws Exception
    {
        TemporaryQueue replyToQueue = _session.createTemporaryQueue();

        sendRequestAndValidateResponse(replyToQueue);
    }

    private void sendRequestAndValidateResponse(Queue replyToQueue) throws JMSException, Exception
    {
        MessageConsumer replyConsumer = _session.createConsumer(replyToQueue);

        Message requestMessage = createRequestMessageWithJmsReplyTo(_session, replyToQueue);
        sendRequest(_requestQueue, _session, requestMessage);

        receiveAndValidateResponse(replyConsumer, requestMessage);

        assertNull("Async responder caught unexpected exception", _caughtException.get());
    }

    private Message createRequestMessageWithJmsReplyTo(Session session, Queue replyToQueue)
            throws JMSException
    {
        Message requestMessage = session.createTextMessage("My request");
        requestMessage.setJMSReplyTo(replyToQueue);
        return requestMessage;
    }

    private void sendRequest(final Queue requestQueue, Session session, Message requestMessage) throws Exception
    {
        MessageProducer producer = session.createProducer(requestQueue);
        producer.send(requestMessage);
    }

    private void receiveAndValidateResponse(MessageConsumer replyConsumer, Message requestMessage) throws JMSException
    {
        Message responseMessage = replyConsumer.receive(RECEIVE_TIMEOUT);
        assertNotNull("Response message not received", responseMessage);
        assertEquals("Correlation id of the response should match message id of the request",
                responseMessage.getJMSCorrelationID(), requestMessage.getJMSMessageID());
    }

    private Queue startAsyncRespondingJmsConsumerOnSeparateConnection() throws Exception
    {
        final String requestQueueName = getTestQueueName() + ".request";
        final Connection responderConnection =  getConnection();
        responderConnection.start();
        final Session responderSession = responderConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final Queue requestQueue = responderSession.createQueue(requestQueueName);

        final MessageConsumer requestConsumer = responderSession.createConsumer(requestQueue);
        requestConsumer.setMessageListener(new AsyncResponder(responderSession));

        return requestQueue;
    }

    private final class AsyncResponder implements MessageListener
    {
        private final Session _responderSession;

        private AsyncResponder(Session responderSession)
        {
            _responderSession = responderSession;
        }

        @Override
        public void onMessage(Message requestMessage)
        {
            try
            {
                Destination replyTo = getReplyToQueue(requestMessage);

                Message responseMessage = _responderSession.createMessage();
                responseMessage.setJMSCorrelationID(requestMessage.getJMSMessageID());

                sendResponseToQueue(replyTo, responseMessage);
            }
            catch (Throwable t)
            {
                _caughtException.set(t);
            }
        }

        private Destination getReplyToQueue(Message requestMessage) throws JMSException, IllegalStateException
        {
            Destination replyTo = requestMessage.getJMSReplyTo();
            if (replyTo == null)
            {
                throw new IllegalStateException("JMSReplyTo was null on message " + requestMessage);
            }
            return replyTo;
        }

        private void sendResponseToQueue(Destination replyTo, Message responseMessage)
                throws JMSException
        {
            MessageProducer responseProducer = _responderSession.createProducer(replyTo);
            responseProducer.send(responseMessage);
        }
    }

}
