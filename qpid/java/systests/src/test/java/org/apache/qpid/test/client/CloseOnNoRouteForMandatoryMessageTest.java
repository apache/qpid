/*
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
 */
package org.apache.qpid.test.client;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Tests the broker's connection-closing behaviour when it receives an unroutable message
 * on a transactional session.
 *
 * @see ImmediateAndMandatoryPublishingTest for more general tests of mandatory and immediate publishing
 */
public class CloseOnNoRouteForMandatoryMessageTest extends QpidBrokerTestCase
{
    private static final Logger _logger = Logger.getLogger(CloseOnNoRouteForMandatoryMessageTest.class);

    private Connection _connection;
    private UnroutableMessageTestExceptionListener _testExceptionListener = new UnroutableMessageTestExceptionListener();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
    }

    public void testNoRoute_brokerClosesConnection() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(true);

        Session transactedSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
        String testQueueName = getTestQueueName();
        MessageProducer mandatoryProducer = ((AMQSession<?, ?>) transactedSession).createProducer(
                transactedSession.createQueue(testQueueName),
                true, // mandatory
                false); // immediate

        Message message = transactedSession.createMessage();
        mandatoryProducer.send(message);
        try
        {
            transactedSession.commit();
            fail("Expected exception not thrown");
        }
        catch (IllegalStateException ise)
        {
            _logger.debug("Caught exception", ise);
            //The session was marked closed even before we had a chance to call commit on it
            assertTrue("ISE did not indicate closure", ise.getMessage().contains("closed"));
        }
        catch(JMSException e)
        {
            _logger.debug("Caught exception", e);
            _testExceptionListener.assertNoRoute(e, testQueueName);
        }
        _testExceptionListener.assertReceivedNoRoute(testQueueName);

        forgetConnection(_connection);
    }

    public void testCloseOnNoRouteWhenExceptionMessageLengthIsGreater255() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(true);

        AMQSession<?, ?> transactedSession = (AMQSession<?, ?>) _connection.createSession(true, Session.SESSION_TRANSACTED);

        StringBuilder longExchangeName = getLongExchangeName();

        AMQShortString exchangeName = new AMQShortString(longExchangeName.toString());
        transactedSession.declareExchange(exchangeName, new AMQShortString("direct"), false);

        Destination testQueue = new AMQQueue(exchangeName, getTestQueueName());
        MessageProducer mandatoryProducer = transactedSession.createProducer(
                testQueue,
                true, // mandatory
                false); // immediate

        Message message = transactedSession.createMessage();
        mandatoryProducer.send(message);
        try
        {
            transactedSession.commit();
            fail("Expected exception not thrown");
        }
        catch (IllegalStateException ise)
        {
            _logger.debug("Caught exception", ise);
            //The session was marked closed even before we had a chance to call commit on it
            assertTrue("ISE did not indicate closure", ise.getMessage().contains("closed"));
        }
        catch (JMSException e)
        {
            _logger.debug("Caught exception", e);
            AMQException noRouteException = (AMQException) e.getLinkedException();
            assertNotNull("AMQException should be linked to JMSException", noRouteException);

            assertEquals(AMQConstant.NO_ROUTE, noRouteException.getErrorCode());
            String expectedMessage = "Error: No route for message [Exchange: " + longExchangeName.substring(0, 220) + "...";
            assertEquals("Unexpected exception message: " + noRouteException.getMessage(), expectedMessage,
                    noRouteException.getMessage());
        }
        finally
        {
            forgetConnection(_connection);
        }
    }

    public void testNoRouteMessageReurnedWhenExceptionMessageLengthIsGreater255() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(false);

        AMQSession<?, ?> transactedSession = (AMQSession<?, ?>) _connection.createSession(true, Session.SESSION_TRANSACTED);

        StringBuilder longExchangeName = getLongExchangeName();

        AMQShortString exchangeName = new AMQShortString(longExchangeName.toString());
        transactedSession.declareExchange(exchangeName, new AMQShortString("direct"), false);

        AMQQueue testQueue = new AMQQueue(exchangeName, getTestQueueName());
        MessageProducer mandatoryProducer = transactedSession.createProducer(
                testQueue,
                true, // mandatory
                false); // immediate

        Message message = transactedSession.createMessage();
        mandatoryProducer.send(message);
        transactedSession.commit();
        _testExceptionListener.assertReceivedReturnedMessageWithLongExceptionMessage(message, testQueue);
    }

    private StringBuilder getLongExchangeName()
    {
        StringBuilder longExchangeName = new StringBuilder();
        for (int i = 0; i < 50; i++)
        {
            longExchangeName.append("abcde");
        }
        return longExchangeName;
    }

    public void testNoRouteForNonMandatoryMessage_brokerKeepsConnectionOpenAndCallsExceptionListener() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(true);

        Session transactedSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
        String testQueueName = getTestQueueName();
        MessageProducer nonMandatoryProducer = ((AMQSession<?, ?>) transactedSession).createProducer(
                transactedSession.createQueue(testQueueName),
                false, // mandatory
                false); // immediate

        Message message = transactedSession.createMessage();
        nonMandatoryProducer.send(message);

        // should succeed - the message is simply discarded
        transactedSession.commit();

        _testExceptionListener.assertNoException();
    }


    public void testNoRouteOnNonTransactionalSession_brokerKeepsConnectionOpenAndCallsExceptionListener() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(true);

        Session nonTransactedSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        String testQueueName = getTestQueueName();
        MessageProducer mandatoryProducer = ((AMQSession<?, ?>) nonTransactedSession).createProducer(
                nonTransactedSession.createQueue(testQueueName),
                true, // mandatory
                false); // immediate

        Message message = nonTransactedSession.createMessage();
        mandatoryProducer.send(message);

        // should succeed - the message is asynchronously bounced back to the exception listener
        message.acknowledge();

        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());
    }

    public void testClientDisablesCloseOnNoRoute_brokerKeepsConnectionOpenAndCallsExceptionListener() throws Exception
    {
        createConnectionWithCloseWhenNoRoute(false);

        Session transactedSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
        String testQueueName = getTestQueueName();
        MessageProducer mandatoryProducer = ((AMQSession<?, ?>) transactedSession).createProducer(
                transactedSession.createQueue(testQueueName),
                true, // mandatory
                false); // immediate

        Message message = transactedSession.createMessage();
        mandatoryProducer.send(message);
        transactedSession.commit();
        _testExceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());
    }

    private void createConnectionWithCloseWhenNoRoute(boolean closeWhenNoRoute) throws URLSyntaxException, NamingException, JMSException
    {
        Map<String, String> options = new HashMap<String, String>();
        options.put(ConnectionURL.OPTIONS_CLOSE_WHEN_NO_ROUTE, Boolean.toString(closeWhenNoRoute));
        _connection = getConnectionWithOptions(options);
        _connection.setExceptionListener(_testExceptionListener);
    }
}
