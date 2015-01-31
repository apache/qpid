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
package org.apache.qpid.client;

import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.StreamMessage;

import org.apache.qpid.client.message.AMQPEncodedListMessage;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.*;
import org.apache.qpid.transport.Connection.SessionFactory;
import org.apache.qpid.transport.Connection.State;

/**
 * Tests AMQSession_0_10 methods.
 * <p>
 * The main purpose of the tests in this test suite is to check that
 * {@link SessionException} is not thrown from methods of
 * {@link AMQSession_0_10}.
 */
public class AMQSession_0_10Test extends QpidTestCase
{

    public void testExceptionOnCommit()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            session.commit();
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testExceptionOnCreateMessageProducer()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            session.createMessageProducer(createDestination(), true, true, 1l);
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected but got:" + e, e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testExceptionOnRollback()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            session.rollback();
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
        }
    }

    public void testExceptionOnRecover()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10(javax.jms.Session.AUTO_ACKNOWLEDGE);
        try
        {
            session.recover();
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
        }
    }

    public void testExceptionOnCreateBrowser()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        AMQQueue destination = createQueue();
        try
        {
            session.createBrowser(destination);
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testExceptionOnCreateConsumer()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        AMQAnyDestination destination = createDestination();
        try
        {
            session.createConsumer(destination);
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testExceptionOnCreateSubscriber()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        AMQAnyDestination destination = createDestination();
        try
        {
            session.createSubscriber(destination);
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testExceptionOnUnsubscribe()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            session.unsubscribe("whatever");
            fail("JMSExceptiuon should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testCommit()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.commit();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, TxCommit.class, false);
        assertNotNull("TxCommit was not sent", event);
    }

    public void testRollback()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.rollback();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, TxRollback.class, false);
        assertNotNull("TxRollback was not sent", event);
    }

    public void testRecover()
    {
        AMQSession_0_10 session = createAMQSession_0_10(javax.jms.Session.AUTO_ACKNOWLEDGE);
        try
        {
            session.recover();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageRelease.class, false);
        assertNotNull("MessageRelease was not sent", event);
    }

    public void testCreateProducer()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.createProducer(createQueue());
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, ExchangeDeclare.class, false);
        assertNotNull("ExchangeDeclare was not sent", event);
    }

    public void testCreateConsumer()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.createConsumer(createQueue());
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageSubscribe.class, false);
        assertNotNull("MessageSubscribe was not sent", event);
    }

    public void testSync()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.sync();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, ExecutionSync.class, false);
        assertNotNull("ExecutionSync was not sent", event);
    }

    public void testSendQueueDelete()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.sendQueueDelete(new AMQShortString("test"));
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, QueueDelete.class, false);
        assertNotNull("QueueDelete event was not sent", event);
        QueueDelete exchangeDelete = (QueueDelete) event;
        assertEquals("test", exchangeDelete.getQueue());
    }

    public void testSendConsume()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            session.sendConsume(consumer, new AMQShortString("test"), true, 1);
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageSubscribe.class, false);
        assertNotNull("MessageSubscribe event was not sent", event);
    }

    public void testCreateMessageProducer()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.createMessageProducer(createDestination(), true, true, 1l);
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, ExchangeDeclare.class, false);
        assertNotNull("ExchangeDeclare event was not sent", event);
    }

    public void testSendExchangeDelete()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            session.sendExchangeDelete("test", true);
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, ExchangeDelete.class, false);
        assertNotNull("ExchangeDelete event was not sent", event);
        ExchangeDelete exchangeDelete = (ExchangeDelete) event;
        assertEquals("test", exchangeDelete.getExchange());
    }

    public void testExceptionOnMessageConsumerReceive()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            session.start();
            consumer.receive(1);
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testMessageConsumerReceive()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            session.start();
            consumer.receive(1);
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageFlow.class, false);
        assertNotNull("MessageFlow event was not sent", event);
    }

    public void testExceptionOnMessageConsumerReceiveNoWait()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            session.start();
            consumer.receiveNoWait();
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testMessageConsumerClose()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            consumer.close();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageCancel.class, false);
        assertNotNull("MessageCancel event was not sent", event);
    }

    public void testExceptionOnMessageConsumerClose()
    {
        AMQSession_0_10 session = createThrowingExceptionAMQSession_0_10();
        try
        {
            BasicMessageConsumer_0_10 consumer = session.createMessageConsumer(createDestination(), 1, 1, true, false,
                    null, null, false, true);
            consumer.close();
            fail("JMSException should be thrown");
        }
        catch (Exception e)
        {
            assertTrue("JMSException is expected", e instanceof JMSException);
            assertEquals("541 error code is expected", "541", ((JMSException) e).getErrorCode());
        }
    }

    public void testMessageProducerSend()
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        try
        {
            MessageProducer producer = session.createProducer(createQueue());
            producer.send(session.createTextMessage("Test"));
            session.commit();
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent event = findSentProtocolEventOfClass(session, MessageTransfer.class, false);
        assertNotNull("MessageTransfer event was not sent", event);
        event = findSentProtocolEventOfClass(session, ExchangeDeclare.class, false);
        assertNotNull("ExchangeDeclare event was not sent", event);
    }

    public void testCreateStreamMessage() throws Exception
    {
        AMQSession_0_10 session = createAMQSession_0_10();
        StreamMessage m = session.createStreamMessage();
        assertTrue("Legacy Stream message encoding should be the default" + m.getClass(),!(m instanceof AMQPEncodedListMessage));
    }

    public void testGetQueueDepthWithSync()
    {
        // slow down a flush thread
        setTestSystemProperty("qpid.session.max_ack_delay", "10000");
        AMQSession_0_10 session =  createAMQSession_0_10(false, javax.jms.Session.DUPS_OK_ACKNOWLEDGE);
        try
        {
            session.acknowledgeMessage(-1, false);
            session.getQueueDepth(createDestination(), true);
        }
        catch (Exception e)
        {
            fail("Unexpected exception is caught:" + e.getMessage());
        }
        ProtocolEvent command = findSentProtocolEventOfClass(session, MessageAccept.class, false);
        assertNotNull("MessageAccept command was not sent", command);
        command = findSentProtocolEventOfClass(session, ExecutionSync.class, false);
        assertNotNull("ExecutionSync command was not sent", command);
        command = findSentProtocolEventOfClass(session, QueueQuery.class, false);
        assertNotNull("QueueQuery command was not sent", command);
    }

    private AMQAnyDestination createDestination()
    {
        AMQAnyDestination destination = null;
        try
        {
            destination = new AMQAnyDestination(new AMQShortString("amq.direct"), new AMQShortString("direct"),
                    new AMQShortString("test"), false, true, new AMQShortString("test"), true, null);
        }
        catch (Exception e)
        {
            fail("Failued to create destination:" + e.getMessage());
        }
        return destination;
    }

    private AMQQueue createQueue()
    {
        AMQQueue destination = null;
        try
        {
            destination = new AMQQueue(new AMQShortString("amq.direct"), new AMQShortString("test"),
                    new AMQShortString("test"));
        }
        catch (Exception e)
        {
            fail("Failued to create destination:" + e.getMessage());
        }
        return destination;
    }

    private AMQSession_0_10 createThrowingExceptionAMQSession_0_10()
    {
        return createAMQSession_0_10(true, javax.jms.Session.SESSION_TRANSACTED);
    }

    private AMQSession_0_10 createThrowingExceptionAMQSession_0_10(int akcnowledgeMode)
    {
        return createAMQSession_0_10(true, akcnowledgeMode);
    }

    private ProtocolEvent findSentProtocolEventOfClass(AMQSession_0_10 session, Class<? extends ProtocolEvent> class1,
            boolean isLast)
    {
        ProtocolEvent found = null;
        List<ProtocolEvent> events = ((MockSession) session.getQpidSession()).getSender().getSendEvents();
        assertNotNull("Events list should not be null", events);
        assertFalse("Events list should not be empty", events.isEmpty());
        if (isLast)
        {
            ProtocolEvent event = events.get(events.size() - 1);
            if (event.getClass().isAssignableFrom(class1))
            {
                found = event;
            }
        }
        else
        {
            for (ProtocolEvent protocolEvent : events)
            {
                if (protocolEvent.getClass().isAssignableFrom(class1))
                {
                    found = protocolEvent;
                    break;
                }
            }

        }
        return found;
    }

    private AMQSession_0_10 createAMQSession_0_10()
    {
        return createAMQSession_0_10(false, javax.jms.Session.SESSION_TRANSACTED);
    }

    private AMQSession_0_10 createAMQSession_0_10(int acknowledgeMode)
    {
        return createAMQSession_0_10(false, acknowledgeMode);
    }

    private AMQSession_0_10 createAMQSession_0_10(boolean throwException, int acknowledgeMode)
    {
        AMQConnection amqConnection = null;
        try
        {
            amqConnection = new MockAMQConnection(
                    "amqp://guest:guest@client/test?brokerlist='tcp://localhost:1'&maxprefetch='0'");
        }
        catch (Exception e)
        {
            fail("Failure to create a mock connection:" + e.getMessage());
        }
        boolean isTransacted = acknowledgeMode == javax.jms.Session.SESSION_TRANSACTED ? true : false;
        AMQSession_0_10 session = new AMQSession_0_10(createConnection(throwException), amqConnection, 1, isTransacted, acknowledgeMode,
                 10, 10, "test");
        return session;
    }

    private Connection createConnection(final boolean throwException)
    {
        MockTransportConnection connection = new MockTransportConnection();
        connection.setState(State.OPEN);
        connection.setSender(new MockSender());
        connection.setSessionFactory(new SessionFactory()
        {

            public Session newSession(Connection conn, Binary name, long expiry, boolean isNoReplay)
            {
                return new MockSession(conn, new SessionDelegate(), name, expiry, throwException);
            }
        });
        return connection;
    }

    private final class MockMessageListener implements MessageListener
    {
        public void onMessage(Message arg0)
        {
        }
    }

    class MockSession extends Session
    {
        private final boolean _throwException;
        private final Connection _connection;
        private final SessionDelegate _delegate;

        protected MockSession(Connection connection, SessionDelegate delegate, Binary name, long expiry,
                boolean throwException)
        {
            super(connection, delegate, name, expiry);
            _throwException = throwException;
            setState(State.OPEN);
            _connection = connection;
            _delegate = delegate;
        }

        public void invoke(Method m, Runnable postIdSettingAction)
        {
            if (_throwException)
            {
                if (m instanceof SessionAttach || m instanceof SessionRequestTimeout || m instanceof TxSelect)
                {
                    // do not throw exception for SessionAttach,
                    // SessionRequestTimeout and TxSelect
                    // session needs to be instantiated
                    return;
                }
                ExecutionException e = new ExecutionException();
                e.setErrorCode(ExecutionErrorCode.INTERNAL_ERROR);
                throw new SessionException(e);
            }
            else
            {
                super.invoke(m, postIdSettingAction);
                if (m instanceof SessionDetach)
                {
                    setState(State.CLOSED);
                }
            }
        }

        public void sync()
        {
            // to avoid recursive calls
            setAutoSync(false);
            // simply send sync command
            super.executionSync(Option.SYNC);
        }

        protected <T> Future<T> invoke(Method m, Class<T> klass)
        {
            int commandId = getCommandsOut();
            Future<T> future = super.invoke(m, klass);
            ExecutionResult result = new ExecutionResult();
            result.setCommandId(commandId);
            if (m instanceof ExchangeBound)
            {
                ExchangeBoundResult struc = new ExchangeBoundResult();
                result.setValue(struc);
            }
            else if (m instanceof ExchangeQuery)
            {
                ExchangeQueryResult struc = new ExchangeQueryResult();
                result.setValue(struc);
            }
            else if (m instanceof QueueQuery)
            {
                QueueQueryResult struc = new QueueQueryResult();
                result.setValue(struc);
            }
            _delegate.executionResult(this, result);
            return future;
        }

        public MockSender getSender()
        {
            return (MockSender) _connection.getSender();
        }
    }

    class MockTransportConnection extends Connection
    {
        public void setState(State state)
        {
            super.setState(state);
        }
    }

    class MockSender implements ProtocolEventSender
    {
        private List<ProtocolEvent> _sendEvents = new ArrayList<ProtocolEvent>();

        private void setIdleTimeout(int i)
        {
        }

        public void send(ProtocolEvent msg)
        {
            _sendEvents.add(msg);
        }

        public void flush()
        {
        }

        public void close()
        {
        }

        public List<ProtocolEvent> getSendEvents()
        {
            return _sendEvents;
        }

    }

}
