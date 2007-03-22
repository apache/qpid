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
package org.apache.qpid.client;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUndeliveredException;
import org.apache.qpid.AMQInvalidRoutingKeyException;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.client.failover.FailoverSupport;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.JMSBytesMessage;
import org.apache.qpid.client.message.JMSMapMessage;
import org.apache.qpid.client.message.JMSObjectMessage;
import org.apache.qpid.client.message.JMSStreamMessage;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolHandlerImpl;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.protocol.ProtocolOutputHandler;
import org.apache.qpid.client.util.FlowControllingBlockingQueue;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.*;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;

public class AMQSession extends Closeable implements Session, QueueSession, TopicSession
{
    private static final Logger _logger = Logger.getLogger(AMQSession.class);

    public static final int DEFAULT_PREFETCH_HIGH_MARK = 5000;
    public static final int DEFAULT_PREFETCH_LOW_MARK = 2500;

    private AMQConnection _connection;

    private boolean _transacted;

    private int _acknowledgeMode;

    private int _channelId;

    private int _ticket;

    private int _defaultPrefetchHighMark = DEFAULT_PREFETCH_HIGH_MARK;
    private int _defaultPrefetchLowMark = DEFAULT_PREFETCH_LOW_MARK;

    private MessageListener _messageListener = null;

    private AtomicBoolean _startedAtLeastOnce = new AtomicBoolean(false);

    /**
     * Used to reference durable subscribers so they requests for unsubscribe can be handled correctly.  Note this only
     * keeps a record of subscriptions which have been created in the current instance.  It does not remember
     * subscriptions between executions of the client
     */
    private final ConcurrentHashMap<String, TopicSubscriberAdaptor> _subscriptions =
            new ConcurrentHashMap<String, TopicSubscriberAdaptor>();
    private final ConcurrentHashMap<BasicMessageConsumer, String> _reverseSubscriptionMap =
            new ConcurrentHashMap<BasicMessageConsumer, String>();

    /** Used in the consume method. We generate the consume tag on the client so that we can use the nowait feature. */
    private int _nextTag = 1;

    /** This queue is bounded and is used to store messages before being dispatched to the consumer */
    private final FlowControllingBlockingQueue _queue;

    private Dispatcher _dispatcher;

    private MessageFactoryRegistry _messageFactoryRegistry;

    /** Set of all producers created by this session */
    private Map _producers = new ConcurrentHashMap();

    /** Maps from consumer tag (String) to JMSMessageConsumer instance */
    private Map<AMQShortString, BasicMessageConsumer> _consumers = new ConcurrentHashMap<AMQShortString, BasicMessageConsumer>();

    /** Maps from destination to count of JMSMessageConsumers */
    private ConcurrentHashMap<Destination, AtomicInteger> _destinationConsumerCount =
            new ConcurrentHashMap<Destination, AtomicInteger>();

    /**
     * Default value for immediate flag used by producers created by this session is false, i.e. a consumer does not
     * need to be attached to a queue
     */
    protected static final boolean DEFAULT_IMMEDIATE = false;

    /**
     * Default value for mandatory flag used by producers created by this sessio is true, i.e. server will not silently
     * drop messages where no queue is connected to the exchange for the message
     */
    protected static final boolean DEFAULT_MANDATORY = true;

    /**
     * The counter of the next producer id. This id is generated by the session and used only to allow the producer to
     * identify itself to the session when deregistering itself. <p/> Access to this id does not require to be
     * synchronized since according to the JMS specification only one thread of control is allowed to create producers
     * for any given session instance.
     */
    private long _nextProducerId;


    /**
     * Set when recover is called. This is to handle the case where recover() is called by application code during
     * onMessage() processing. We need to make sure we do not send an auto ack if recover was called.
     */
    private boolean _inRecovery;

    private boolean _connectionStopped;

    private boolean _hasMessageListeners;

    private boolean _suspended;

    private final Object _suspensionLock = new Object();
    private static final AMQShortString CHANNEL_CLOSE_REPLY_TEXT = new AMQShortString("JMS client closing channel");


    /** Responsible for decoding a message fragment and passing it to the appropriate message consumer. */

    private class Dispatcher extends Thread
    {

        /** Track the 'stopped' state of the dispatcher, a session starts in the stopped state. */
        private final AtomicBoolean _closed = new AtomicBoolean(false);

        private final Object _lock = new Object();

        public Dispatcher()
        {
            super("Dispatcher-Channel-" + _channelId);
        }

        public void run()
        {
            UnprocessedMessage message;

            try
            {
                while (!_closed.get() && (message = (UnprocessedMessage) _queue.take()) != null)
                {
                    synchronized (_lock)
                    {

                        while (connectionStopped())
                        {
                            _lock.wait();
                        }

                        dispatchMessage(message);

                        while (connectionStopped())
                        {
                            _lock.wait();
                        }

                    }

                }
            }
            catch (InterruptedException e)
            {
                ;
            }

            _logger.info("Dispatcher thread terminating for channel " + _channelId);
        }

        // only call while holding lock
        final boolean connectionStopped()
        {
            return _connectionStopped;
        }

        boolean setConnectionStopped(boolean connectionStopped)
        {
            boolean currently;
            synchronized (_lock)
            {
                currently = _connectionStopped;
                _connectionStopped = connectionStopped;
                _lock.notify();
            }
            return currently;
        }

        private void dispatchMessage(UnprocessedMessage message)
        {
            if (message.getDeliverBody() != null)
            {
                final BasicMessageConsumer consumer = (BasicMessageConsumer) _consumers.get(message.getDeliverBody().getConsumerTag());

                if (consumer == null)
                {
                    _logger.warn("Received a message from queue " + message.getDeliverBody().getConsumerTag() + " without a handler - ignoring...");
                    _logger.warn("Consumers that exist: " + _consumers);
                    _logger.warn("Session hashcode: " + System.identityHashCode(this));
                }
                else
                {

                    consumer.notifyMessage(message, _channelId);

                }
            }
        }

        public void close()
        {
            _closed.set(true);
            interrupt();

            //fixme awaitTermination

        }

        public void rollback()
        {

            synchronized (_lock)
            {
                boolean isStopped = connectionStopped();

                if (!isStopped)
                {
                    setConnectionStopped(true);
                }

                rejectAllMessages(true);

                _logger.debug("Session Pre Dispatch Queue cleared");

                for (BasicMessageConsumer consumer : _consumers.values())
                {
                    consumer.rollback();
                }

                setConnectionStopped(isStopped);
            }

        }

        public void rejectPending(AMQShortString consumerTag)
        {
            synchronized (_lock)
            {
                boolean stopped = connectionStopped();

                _dispatcher.setConnectionStopped(false);

                rejectMessagesForConsumerTag(consumerTag, true);

                if (stopped)
                {
                    _dispatcher.setConnectionStopped(stopped);
                }
            }
        }
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry)
    {
        this(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, DEFAULT_PREFETCH_HIGH_MARK, DEFAULT_PREFETCH_LOW_MARK);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetch)
    {
        this(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, defaultPrefetch, defaultPrefetch);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {
        _connection = con;
        _transacted = transacted;
        if (transacted)
        {
            _acknowledgeMode = javax.jms.Session.SESSION_TRANSACTED;
        }
        else
        {
            _acknowledgeMode = acknowledgeMode;
        }
        _channelId = channelId;
        _messageFactoryRegistry = messageFactoryRegistry;
        _defaultPrefetchHighMark = defaultPrefetchHighMark;
        _defaultPrefetchLowMark = defaultPrefetchLowMark;

        if (_acknowledgeMode == NO_ACKNOWLEDGE)
        {
            _queue = new FlowControllingBlockingQueue(_defaultPrefetchHighMark, _defaultPrefetchLowMark,
                                                      new FlowControllingBlockingQueue.ThresholdListener()
                                                      {
                                                          public void aboveThreshold(int currentValue)
                                                          {
                                                              if (_acknowledgeMode == NO_ACKNOWLEDGE)
                                                              {
                                                                  _logger.warn("Above threshold(" + _defaultPrefetchHighMark + ") so suspending channel. Current value is " + currentValue);
                                                                  new Thread(new SuspenderRunner(true)).start();
                                                              }
                                                          }

                                                          public void underThreshold(int currentValue)
                                                          {
                                                              if (_acknowledgeMode == NO_ACKNOWLEDGE)
                                                              {
                                                                  _logger.warn("Below threshold(" + _defaultPrefetchLowMark + ") so unsuspending channel. Current value is " + currentValue);
                                                                  new Thread(new SuspenderRunner(false)).start();
                                                              }
                                                          }
                                                      });
        }
        else
        {
            _queue = new FlowControllingBlockingQueue(_defaultPrefetchHighMark, null);
        }
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry());
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetch)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetch);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetchHigh, int defaultPrefetchLow)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetchHigh, defaultPrefetchLow);
    }

    public AMQConnection getAMQConnection()
    {
        return _connection;
    }

    public BytesMessage createBytesMessage() throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            checkNotClosed();
            return new JMSBytesMessage();
        }
    }

    public MapMessage createMapMessage() throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            checkNotClosed();
            return new JMSMapMessage();
        }
    }

    public javax.jms.Message createMessage() throws JMSException
    {
        return createBytesMessage();
    }

    public ObjectMessage createObjectMessage() throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            checkNotClosed();
            return (ObjectMessage) new JMSObjectMessage();
        }
    }

    public ObjectMessage createObjectMessage(Serializable object) throws JMSException
    {
        ObjectMessage msg = createObjectMessage();
        msg.setObject(object);
        return msg;
    }

    public StreamMessage createStreamMessage() throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            checkNotClosed();

            return new JMSStreamMessage();
        }
    }

    public TextMessage createTextMessage() throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            checkNotClosed();

            return new JMSTextMessage();
        }
    }

    public TextMessage createTextMessage(String text) throws JMSException
    {

        TextMessage msg = createTextMessage();
        msg.setText(text);
        return msg;
    }

    public boolean getTransacted() throws JMSException
    {
        checkNotClosed();
        return _transacted;
    }

    public int getAcknowledgeMode() throws JMSException
    {
        checkNotClosed();
        return _acknowledgeMode;
    }

    public void commit() throws JMSException
    {
        checkTransacted();
        try
        {
            // Acknowledge up to message last delivered (if any) for each consumer.
            //need to send ack for messages delivered to consumers so far
            for (Iterator<BasicMessageConsumer> i = _consumers.values().iterator(); i.hasNext();)
            {
                //Sends acknowledgement to server
                i.next().acknowledgeLastDelivered();
            }

            TxCommitBody commitBody = getAMQMethodFactory().createTxCommit();
            sendCommandReceiveResponse(commitBody);



        }
        catch (AMQException e)
        {
            JMSException exception = new JMSException("Failed to commit: " + e.getMessage());
            exception.setLinkedException(e);
            throw exception;
        }
    }


    public void rollback() throws JMSException
    {
        synchronized (_suspensionLock)
        {
            checkTransacted();
            try
            {
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.

                boolean isSuspended = isSuspended();

                if (!isSuspended)
                {
                    suspendChannel(true);
                }

                TxRollbackBody rollbackBody = getAMQMethodFactory().createTxRollback();
                sendCommandReceiveResponse(rollbackBody);

                if (_dispatcher != null)
                {
                    _dispatcher.rollback();
                }

                if (!isSuspended)
                {
                    suspendChannel(false);
                }
            }
            catch (AMQException e)
            {
                throw(JMSException) (new JMSException("Failed to rollback: " + e).initCause(e));
            }
        }
    }

    public void close() throws JMSException
    {
        close(-1);
    }

    public void close(long timeout) throws JMSException
    {
        // We must close down all producers and consumers in an orderly fashion. This is the only method
        // that can be called from a different thread of control from the one controlling the session
        synchronized (_connection.getFailoverMutex())
        {
            //Ensure we only try and close an open session.
            if (!_closed.getAndSet(true))
            {
                // we pass null since this is not an error case
                closeProducersAndConsumers(null);

                try
                {

                    getProtocolHandler().closeSession(this);
                    ChannelCloseBody closeBody = getAMQMethodFactory().createChannelClose(AMQConstant.REPLY_SUCCESS.getCode(),    // replyCode
                                                                                          CHANNEL_CLOSE_REPLY_TEXT);
                    sendCommandReceiveResponse(closeBody, timeout);



                    // When control resumes at this point, a reply will have been received that
                    // indicates the broker has closed the channel successfully

                }
                catch (AMQException e)
                {
                    JMSException jmse = new JMSException("Error closing session: " + e);
                    jmse.setLinkedException(e);
                    throw jmse;
                }
                finally
                {
                    _connection.deregisterSession(_channelId);
                }
            }
        }
    }

    private AMQProtocolHandlerImpl getProtocolHandler()
    {
        return _connection.getProtocolHandler();
    }

    public ProtocolOutputHandler getProtocolOutputHandler()
    {
        return _connection.getProtocolOutputHandler();
    }




    /**
     * Close all producers or consumers. This is called either in the error case or when closing the session normally.
     *
     * @param amqe the exception, may be null to indicate no error has occurred
     */
    private void closeProducersAndConsumers(AMQException amqe) throws JMSException
    {
        JMSException jmse = null;
        try
        {
            closeProducers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
            jmse = e;
        }
        try
        {
            closeConsumers(amqe);
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
            if (jmse == null)
            {
                jmse = e;
            }
        }
        finally
        {
            if (jmse != null)
            {
                throw jmse;
            }
        }

    }


    public boolean isSuspended()
    {
        return _suspended;
    }


    /**
     * Called when the server initiates the closure of the session unilaterally.
     *
     * @param e the exception that caused this session to be closed. Null causes the
     */
    public void closed(Throwable e) throws JMSException
    {
        synchronized (_connection.getFailoverMutex())
        {
            // An AMQException has an error code and message already and will be passed in when closure occurs as a
            // result of a channel close request
            _closed.set(true);
            AMQException amqe;
            if (e instanceof AMQException)
            {
                amqe = (AMQException) e;
            }
            else
            {
                amqe = new AMQException(_logger, "Closing session forcibly", e);
            }
            _connection.deregisterSession(_channelId);
            closeProducersAndConsumers(amqe);
        }
    }

    /**
     * Called to mark the session as being closed. Useful when the session needs to be made invalid, e.g. after failover
     * when the client has veoted resubscription. <p/> The caller of this method must already hold the failover mutex.
     */
    void markClosed()
    {
        _closed.set(true);
        _connection.deregisterSession(_channelId);
        markClosedProducersAndConsumers();
    }

    private void markClosedProducersAndConsumers()
    {
        try
        {
            // no need for a markClosed* method in this case since there is no protocol traffic closing a producer
            closeProducers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
        try
        {
            markClosedConsumers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
    }

    /**
     * Called to close message producers cleanly. This may or may <b>not</b> be as a result of an error. There is
     * currently no way of propagating errors to message producers (this is a JMS limitation).
     */
    private void closeProducers() throws JMSException
    {
        // we need to clone the list of producers since the close() method updates the _producers collection
        // which would result in a concurrent modification exception
        final ArrayList clonedProducers = new ArrayList(_producers.values());

        final Iterator it = clonedProducers.iterator();
        while (it.hasNext())
        {
            final BasicMessageProducer prod = (BasicMessageProducer) it.next();
            prod.close();
        }
        // at this point the _producers map is empty
    }

    /**
     * Called to close message consumers cleanly. This may or may <b>not</b> be as a result of an error.
     *
     * @param error not null if this is a result of an error occurring at the connection level
     */
    private void closeConsumers(Throwable error) throws JMSException
    {
        if (_dispatcher != null)
        {
            _dispatcher.close();
            _dispatcher = null;
        }
        // we need to clone the list of consumers since the close() method updates the _consumers collection
        // which would result in a concurrent modification exception
        final ArrayList<BasicMessageConsumer> clonedConsumers = new ArrayList(_consumers.values());

        final Iterator<BasicMessageConsumer> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final BasicMessageConsumer con = it.next();
            if (error != null)
            {
                con.notifyError(error);
            }
            else
            {
                con.close();
            }
        }
        // at this point the _consumers map will be empty
    }

    private void markClosedConsumers() throws JMSException
    {
        if (_dispatcher != null)
        {
            _dispatcher.close();
            _dispatcher = null;
        }
        // we need to clone the list of consumers since the close() method updates the _consumers collection
        // which would result in a concurrent modification exception
        final ArrayList<BasicMessageConsumer> clonedConsumers = new ArrayList<BasicMessageConsumer>(_consumers.values());

        final Iterator<BasicMessageConsumer> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final BasicMessageConsumer con = it.next();
            con.markClosed();
        }
        // at this point the _consumers map will be empty
    }

    /**
     * Asks the broker to resend all unacknowledged messages for the session.
     *
     * @throws JMSException
     */
    public void recover() throws JMSException
    {
        checkNotClosed();
        checkNotTransacted(); // throws IllegalStateException if a transacted session
        // this is set only here, and the before the consumer's onMessage is called it is set to false
        _inRecovery = true;
        try
        {

            boolean isSuspended = isSuspended();

            if (!isSuspended)
            {
                suspendChannel(true);
            }

            for (BasicMessageConsumer consumer : _consumers.values())
            {
                consumer.clearUnackedMessages();
            }

            final boolean requeue = false;
            AMQMethodBody recoverBody = getAMQMethodFactory().createRecover(requeue);
            sendCommandReceiveResponse(recoverBody);




            if (_dispatcher != null)
            {
                _dispatcher.rollback();
            }

            if (!isSuspended)
            {
                suspendChannel(false);
            }
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
    }

    boolean isInRecovery()
    {
        return _inRecovery;
    }

    void setInRecovery(boolean inRecovery)
    {
        _inRecovery = inRecovery;
    }

    public void acknowledge() throws JMSException
    {
        if (isClosed())
        {
            throw new IllegalStateException("Session is already closed");
        }
        for (BasicMessageConsumer consumer : _consumers.values())
        {
            consumer.acknowledge();
        }


    }


    public MessageListener getMessageListener() throws JMSException
    {
//        checkNotClosed();
        return _messageListener;
    }

    public void setMessageListener(MessageListener listener) throws JMSException
    {
//        checkNotClosed();
//
//        if (_dispatcher != null && !_dispatcher.connectionStopped())
//        {
//            throw new javax.jms.IllegalStateException("Attempt to set listener while session is started.");
//        }
//
//        // We are stopped
//        for (Iterator<BasicMessageConsumer> i = _consumers.values().iterator(); i.hasNext();)
//        {
//            BasicMessageConsumer consumer = i.next();
//
//            if (consumer.isReceiving())
//            {
//                throw new javax.jms.IllegalStateException("Another thread is already receiving synchronously.");
//            }
//        }
//
//        _messageListener = listener;
//
//        for (Iterator<BasicMessageConsumer> i = _consumers.values().iterator(); i.hasNext();)
//        {
//            i.next().setMessageListener(_messageListener);
//        }

    }

    public void run()
    {
        throw new java.lang.UnsupportedOperationException();
    }

    public MessageProducer createProducer(Destination destination, boolean mandatory,
                                          boolean immediate, boolean waitUntilSent)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate, waitUntilSent);
    }

    public MessageProducer createProducer(Destination destination, boolean mandatory, boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate);
    }

    public MessageProducer createProducer(Destination destination, boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, DEFAULT_MANDATORY, immediate);
    }

    public MessageProducer createProducer(Destination destination) throws JMSException
    {
        return createProducerImpl(destination, DEFAULT_MANDATORY, DEFAULT_IMMEDIATE);
    }

    private org.apache.qpid.jms.MessageProducer createProducerImpl(Destination destination, boolean mandatory,
                                                                   boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate, false);
    }

    private org.apache.qpid.jms.MessageProducer createProducerImpl(final Destination destination, final boolean mandatory,
                                                                   final boolean immediate, final boolean waitUntilSent)
            throws JMSException
    {
        return (org.apache.qpid.jms.MessageProducer) new FailoverSupport()
        {
            public Object operation() throws JMSException
            {
                checkNotClosed();
                long producerId = getNextProducerId();
                BasicMessageProducer producer = new BasicMessageProducer(_connection, (AMQDestination) destination, _transacted, _channelId,
                                                                         AMQSession.this, getProtocolHandler(),
                                                                         producerId, immediate, mandatory, waitUntilSent);
                registerProducer(producerId, producer);
                return producer;
            }
        }.execute(_connection);
    }

    /**
     * Creates a QueueReceiver
     *
     * @param destination
     *
     * @return QueueReceiver - a wrapper around our MessageConsumer
     *
     * @throws JMSException
     */
    public QueueReceiver createQueueReceiver(Destination destination) throws JMSException
    {
        checkValidDestination(destination);
        AMQQueue dest = (AMQQueue) destination;
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(destination);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    /**
     * Creates a QueueReceiver using a message selector
     *
     * @param destination
     * @param messageSelector
     *
     * @return QueueReceiver - a wrapper around our MessageConsumer
     *
     * @throws JMSException
     */
    public QueueReceiver createQueueReceiver(Destination destination, String messageSelector) throws JMSException
    {
        checkValidDestination(destination);
        AMQQueue dest = (AMQQueue) destination;
        BasicMessageConsumer consumer = (BasicMessageConsumer)
                createConsumer(destination, messageSelector);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  false,
                                  false,
                                  null,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  false,
                                  false,
                                  messageSelector,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  noLocal,
                                  false,
                                  messageSelector,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createBrowserConsumer(Destination destination,
                                                 String messageSelector,
                                                 boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  noLocal,
                                  false,
                                  messageSelector,
                                  null,
                                  true,
                                  true);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetch,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetch, prefetch, noLocal, exclusive, selector, null, false, false);
    }


    public MessageConsumer createConsumer(Destination destination,
                                          int prefetchHigh,
                                          int prefetchLow,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive, selector, null, false, false);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetch,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector,
                                          FieldTable rawSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetch, prefetch, noLocal, exclusive,
                                  selector, rawSelector, false, false);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetchHigh,
                                          int prefetchLow,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector,
                                          FieldTable rawSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive,
                                  selector, rawSelector, false, false);
    }

    protected MessageConsumer createConsumerImpl(final Destination destination,
                                                 final int prefetchHigh,
                                                 final int prefetchLow,
                                                 final boolean noLocal,
                                                 final boolean exclusive,
                                                 final String selector,
                                                 final FieldTable rawSelector,
                                                 final boolean noConsume,
                                                 final boolean autoClose) throws JMSException
    {
        checkTemporaryDestination(destination);


        return (org.apache.qpid.jms.MessageConsumer) new FailoverSupport()
        {
            public Object operation() throws JMSException
            {
                checkNotClosed();

                AMQDestination amqd = (AMQDestination) destination;

                final AMQProtocolHandler protocolHandler = getProtocolHandler();
                // TODO: construct the rawSelector from the selector string if rawSelector == null
                final FieldTable ft = FieldTableFactory.newFieldTable();
                //if (rawSelector != null)
                //    ft.put("headers", rawSelector.getDataAsBytes());
                if (rawSelector != null)
                {
                    ft.addAll(rawSelector);
                }
                BasicMessageConsumer consumer = new BasicMessageConsumer(_channelId, _connection, amqd, selector, noLocal,
                                                                         _messageFactoryRegistry, AMQSession.this,
                                                                         protocolHandler, ft, prefetchHigh, prefetchLow, exclusive,
                                                                         _acknowledgeMode, noConsume, autoClose);

                if (_messageListener != null)
                {
                    consumer.setMessageListener(_messageListener);
                }

                try
                {
                    registerConsumer(consumer, false);
                }
                catch (AMQInvalidArgumentException ise)
                {
                    JMSException ex = new InvalidSelectorException(ise.getMessage());
                    ex.setLinkedException(ise);
                    throw ex;
                }
                catch (AMQInvalidRoutingKeyException e)
                {
                    JMSException ide = new InvalidDestinationException("Invalid routing key:" + amqd.getRoutingKey().toString());
                    ide.setLinkedException(e);
                    throw ide;
                }
                catch (AMQException e)
                {
                    JMSException ex = new JMSException("Error registering consumer: " + e);

                    //todo remove
                    e.printStackTrace();
                    ex.setLinkedException(e);
                    throw ex;
                }

                synchronized (destination)
                {
                    _destinationConsumerCount.putIfAbsent(destination, new AtomicInteger());
                    _destinationConsumerCount.get(destination).incrementAndGet();
                }

                return consumer;
            }
        }.execute(_connection);
    }

    private void checkTemporaryDestination(Destination destination)
            throws JMSException
    {
        if ((destination instanceof TemporaryDestination))
        {
            _logger.debug("destination is temporary");
            final TemporaryDestination tempDest = (TemporaryDestination) destination;
            if (tempDest.getSession() != this)
            {
                _logger.debug("destination is on different session");
                throw new JMSException("Cannot consume from a temporary destination created onanother session");
            }
            if (tempDest.isDeleted())
            {
                _logger.debug("destination is deleted");
                throw new JMSException("Cannot consume from a deleted destination");
            }
        }
    }


    public boolean hasConsumer(Destination destination)
    {
        AtomicInteger counter = _destinationConsumerCount.get(destination);

        return (counter != null) && (counter.get() != 0);
    }


    public void declareExchange(AMQShortString name, AMQShortString type) throws AMQException
    {
        ExchangeDeclareBody exchangeDeclare = getAMQMethodFactory().createExchangeDeclare(name,type,getTicket());
        sendCommandReceiveResponse(exchangeDeclare);
    }

    public void declareExchangeSynch(AMQShortString name, AMQShortString type) throws AMQException
    {
        ExchangeDeclareBody exchangeDeclare = getAMQMethodFactory().createExchangeDeclare(name,type,getTicket());
        sendCommandReceiveResponse(exchangeDeclare);

    }

    public void createQueue(AMQShortString name, boolean autoDelete, boolean durable, boolean exclusive) throws AMQException
    {
        QueueDeclareBody queueDeclare = getAMQMethodFactory().createQueueDeclare(name, null, autoDelete, durable, exclusive, false ,getTicket());
        sendCommandReceiveResponse(queueDeclare);
    }


    public void bindQueue(AMQShortString queueName, AMQShortString routingKey, FieldTable arguments, AMQShortString exchangeName) throws AMQException
    {
        QueueBindBody queueBind = getAMQMethodFactory().createQueueBind(queueName,exchangeName,routingKey,arguments,getTicket());
        sendCommandReceiveResponse(queueBind);
    }

    /**
     * Declare the queue.
     *
     * @param amqd
     *
     * @return the queue name. This is useful where the broker is generating a queue name on behalf of the client.
     *
     * @throws AMQException
     */
    private AMQShortString declareQueue(AMQDestination amqd) throws AMQException
    {
        // For queues (but not topics) we generate the name in the client rather than the
        // server. This allows the name to be reused on failover if required. In general,
        // the destination indicates whether it wants a name generated or not.
        if (amqd.isNameRequired())
        {
            amqd.setQueueName(getProtocolHandler().generateQueueName());
        }

        //TODO verify the destiation is valid. else throw 

        createQueue(amqd.getAMQQueueName(),amqd.isAutoDelete(),amqd.isDurable(),amqd.isExclusive());


        return amqd.getAMQQueueName();
    }

    private void bindQueue(AMQDestination amqd, AMQShortString queueName, FieldTable ft) throws AMQException
    {
        bindQueue(queueName,amqd.getRoutingKey(),ft,amqd.getExchangeName());
    }

    /**
     * Register to consume from the queue.
     *
     * @param queueName
     *
     * @return the consumer tag generated by the broker
     */
    private void consumeFromQueue(BasicMessageConsumer consumer, AMQShortString queueName, boolean nowait, String messageSelector) throws AMQException
    {
        //fixme prefetch values are not used here. Do we need to have them as parametsrs?
        //need to generate a consumer tag on the client so we can exploit the nowait flag
        AMQShortString tag = new AMQShortString(Integer.toString(_nextTag++));

        FieldTable arguments = FieldTableFactory.newFieldTable();
        if (messageSelector != null && !messageSelector.equals(""))
        {
            arguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue(), messageSelector);
        }
        if (consumer.isAutoClose())
        {
            arguments.put(AMQPFilterTypes.AUTO_CLOSE.getValue(), Boolean.TRUE);
        }
        if (consumer.isNoConsume())
        {
            arguments.put(AMQPFilterTypes.NO_CONSUME.getValue(), Boolean.TRUE);
        }

        consumer.setConsumerTag(tag);
        // we must register the consumer in the map before we actually start listening
        _consumers.put(tag, consumer);

        try
        {
            final boolean noAck = consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE;

            AMQMethodBody consumeBody = getAMQMethodFactory().createConsumer(tag,
                                                 queueName,
                                                 arguments,
                                                 noAck,
                                                 consumer.isExclusive(),
                                                 consumer.isNoLocal(),
                                                 getTicket());
            if(nowait)
            {
                sendCommand(consumeBody);
            }
            else
            {
                sendCommandReceiveResponse(consumeBody);
            }

        }
        catch (AMQException e)
        {
            // clean-up the map in the event of an error
            _consumers.remove(tag);
            throw e;
        }
    }

    public Queue createQueue(String queueName) throws JMSException
    {
        checkNotClosed();
        if (queueName.indexOf('/') == -1)
        {
            return new AMQQueue(getDefaultQueueExchangeName(), new AMQShortString(queueName));
        }
        else
        {
            try
            {
                return new AMQQueue(new AMQBindingURL(queueName));
            }
            catch (URLSyntaxException urlse)
            {
                JMSException jmse = new JMSException(urlse.getReason());
                jmse.setLinkedException(urlse);

                throw jmse;
            }
        }
    }

    public AMQShortString getDefaultQueueExchangeName()
    {
        return _connection.getDefaultQueueExchangeName();
    }

    /**
     * Creates a QueueReceiver wrapping a MessageConsumer
     *
     * @param queue
     *
     * @return QueueReceiver
     *
     * @throws JMSException
     */
    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        checkNotClosed();
        AMQQueue dest = (AMQQueue) queue;
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(dest);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    /**
     * Creates a QueueReceiver wrapping a MessageConsumer using a message selector
     *
     * @param queue
     * @param messageSelector
     *
     * @return QueueReceiver
     *
     * @throws JMSException
     */
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        AMQQueue dest = (AMQQueue) queue;
        BasicMessageConsumer consumer = (BasicMessageConsumer)
                createConsumer(dest, messageSelector);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    public QueueSender createSender(Queue queue) throws JMSException
    {
        checkNotClosed();
        //return (QueueSender) createProducer(queue);
        return new QueueSenderAdapter(createProducer(queue), queue);
    }

    public Topic createTopic(String topicName) throws JMSException
    {
        checkNotClosed();

        if (topicName.indexOf('/') == -1)
        {
            return new AMQTopic(getDefaultTopicExchangeName(), new AMQShortString(topicName));
        }
        else
        {
            try
            {
                return new AMQTopic(new AMQBindingURL(topicName));
            }
            catch (URLSyntaxException urlse)
            {
                JMSException jmse = new JMSException(urlse.getReason());
                jmse.setLinkedException(urlse);

                throw jmse;
            }
        }
    }

    public AMQShortString getDefaultTopicExchangeName()
    {
        return _connection.getDefaultTopicExchangeName();
    }

    /**
     * Creates a non-durable subscriber
     *
     * @param topic
     *
     * @return TopicSubscriber - a wrapper round our MessageConsumer
     *
     * @throws JMSException
     */
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException
    {
        checkNotClosed();
        AMQTopic dest = checkValidTopic(topic);
        //AMQTopic dest = new AMQTopic(topic.getTopicName());
        return new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest));
    }

    /**
     * Creates a non-durable subscriber with a message selector
     *
     * @param topic
     * @param messageSelector
     * @param noLocal
     *
     * @return TopicSubscriber - a wrapper round our MessageConsumer
     *
     * @throws JMSException
     */
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException
    {
        checkNotClosed();
        AMQTopic dest = checkValidTopic(topic);
        //AMQTopic dest = new AMQTopic(topic.getTopicName());
        return new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest, messageSelector, noLocal));
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        checkNotClosed();
        AMQTopic origTopic = checkValidTopic(topic);
        AMQTopic dest = AMQTopic.createDurableTopic(origTopic, name, _connection);
        TopicSubscriberAdaptor subscriber = _subscriptions.get(name);
        if (subscriber != null)
        {
            if (subscriber.getTopic().equals(topic))
            {
                throw new IllegalStateException("Already subscribed to topic " + topic + " with subscription exchange " +
                                                name);
            }
            else
            {
                unsubscribe(name);
            }
        }
        else
        {
            AMQShortString topicName;
            if (topic instanceof AMQTopic)
            {
                topicName = ((AMQTopic) topic).getDestinationName();
            }
            else
            {
                topicName = new AMQShortString(topic.getTopicName());
            }
            // if the queue is bound to the exchange but NOT for this topic, then the JMS spec
            // says we must trash the subscription.
            if (isQueueBound(dest.getExchangeName(), dest.getAMQQueueName()) &&
                !isQueueBound(dest.getExchangeName(), dest.getAMQQueueName(), topicName))
            {
                deleteQueue(dest.getAMQQueueName());
            }
        }

        subscriber = new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest));

        _subscriptions.put(name, subscriber);
        _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);

        return subscriber;
    }

    void deleteQueue(AMQShortString queueName) throws JMSException
    {
        try
        {
            
            QueueDeleteBody deleteBody = getAMQMethodFactory().createQueueDelete(queueName, false, false, getTicket());
            sendCommandReceiveResponse(deleteBody);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
    }

    /** Note, currently this does not handle reuse of the same name with different topics correctly. */
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkNotClosed();
        checkValidTopic(topic);
        AMQTopic dest = AMQTopic.createDurableTopic((AMQTopic) topic, name, _connection);
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(dest, messageSelector, noLocal);
        TopicSubscriberAdaptor subscriber = new TopicSubscriberAdaptor(dest, consumer);
        _subscriptions.put(name, subscriber);
        _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);
        return subscriber;
    }

    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        checkNotClosed();
        return new TopicPublisherAdapter((BasicMessageProducer) createProducer(topic), topic);
    }

    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        return createBrowser(queue, null);
    }

    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        checkValidQueue(queue);
        return new AMQQueueBrowser(this, (AMQQueue) queue, messageSelector);
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkNotClosed();
        return new AMQTemporaryQueue(this);
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        checkNotClosed();
        return new AMQTemporaryTopic(this);
    }

    public void unsubscribe(String name) throws JMSException
    {
        checkNotClosed();
        TopicSubscriberAdaptor subscriber = _subscriptions.get(name);
        if (subscriber != null)
        {
            // send a queue.delete for the subscription
            deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
            _subscriptions.remove(name);
            _reverseSubscriptionMap.remove(subscriber);
        }
        else
        {
            if (isQueueBound(getDefaultTopicExchangeName(), AMQTopic.getDurableTopicQueueName(name, _connection)))
            {
                deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
            }
            else
            {
                throw new InvalidDestinationException("Unknown subscription exchange:" + name);
            }
        }
    }

    boolean isQueueBound(AMQShortString exchangeName, AMQShortString queueName) throws JMSException
    {
        return isQueueBound(exchangeName, queueName, null);
    }

    boolean isQueueBound(AMQShortString exchangeName, AMQShortString queueName, AMQShortString routingKey) throws JMSException
    {
        AMQMethodEvent response = null;
        try
        {
            ExchangeBoundBody exchangeBoundBody =
                getAMQMethodFactory().createExchangeBound(exchangeName,    // exchange
                                                          queueName,    // queue
                                                          routingKey);    // routingKey


            ExchangeBoundOkBody responseBody = sendCommandReceiveResponse(exchangeBoundBody, ExchangeBoundOkBody.class);
            return (responseBody.getReplyCode() == 0);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }

    }

    private void checkTransacted() throws JMSException
    {
        if (!getTransacted())
        {
            throw new IllegalStateException("Session is not transacted");
        }
    }

    private void checkNotTransacted() throws JMSException
    {
        if (getTransacted())
        {
            throw new IllegalStateException("Session is transacted");
        }
    }

    /**
     * Invoked by the MINA IO thread (indirectly) when a message is received from the transport. Puts the message onto
     * the queue read by the dispatcher.
     *
     * @param message the message that has been received
     */
    public void messageReceived(UnprocessedMessage message)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Message[" + (message.getDeliverBody() == null ?
                                        "B:" + message.getBounceBody() : "D:" + message.getDeliverBody())
                          + "] received in session with channel id " + _channelId);
        }

        if (message.getDeliverBody() == null)
        {
            // Return of the bounced message.
            returnBouncedMessage(message);
        }
        else
        {
            _queue.add(message);
        }
    }

    private void returnBouncedMessage(final UnprocessedMessage message)
    {
        _connection.performConnectionTask(
                new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            // Bounced message is processed here, away from the mina thread
                            AbstractJMSMessage bouncedMessage = _messageFactoryRegistry.createMessage(0,
                                                                                                      false,
                                                                                                      message.getBounceBody().getExchange(),
                                                                                                      message.getBounceBody().getRoutingKey(),
                                                                                                      message.getContentHeader(),
                                                                                                      message.getBodies());

                            AMQConstant errorCode = AMQConstant.getConstant(message.getBounceBody().getReplyCode());
                            AMQShortString reason = message.getBounceBody().getReplyText();
                            _logger.debug("Message returned with error code " + errorCode + " (" + reason + ")");

                            //@TODO should this be moved to an exception handler of sorts. Somewhere errors are converted to correct execeptions.
                            if (errorCode == AMQConstant.NO_CONSUMERS)
                            {
                                _connection.exceptionReceived(new AMQNoConsumersException("Error: " + reason, bouncedMessage));
                            }
                            else if (errorCode == AMQConstant.NO_ROUTE)
                            {
                                _connection.exceptionReceived(new AMQNoRouteException("Error: " + reason, bouncedMessage));
                            }
                            else
                            {
                                _connection.exceptionReceived(new AMQUndeliveredException(errorCode, "Error: " + reason, bouncedMessage));
                            }

                        }
                        catch (Exception e)
                        {
                            _logger.error("Caught exception trying to raise undelivered message exception (dump follows) - ignoring...", e);
                        }
                    }
                });
    }

    /**
     * Acknowledge a message or several messages. This method can be called via AbstractJMSMessage or from a
     * BasicConsumer. The former where the mode is CLIENT_ACK and the latter where the mode is AUTO_ACK or similar.
     *
     * @param deliveryTag the tag of the last message to be acknowledged
     * @param multiple    if true will acknowledge all messages up to and including the one specified by the delivery
     *                    tag
     */
    public void acknowledgeMessage(long deliveryTag, boolean multiple)
    {
        AMQMethodBody ackBody = getAMQMethodFactory().createAcknowledge(deliveryTag, multiple);
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on channel " + _channelId);
        }
        sendCommand(ackBody);
    }

    public int getDefaultPrefetch()
    {
        return _defaultPrefetchHighMark;
    }

    public int getDefaultPrefetchHigh()
    {
        return _defaultPrefetchHighMark;
    }

    public int getDefaultPrefetchLow()
    {
        return _defaultPrefetchLowMark;
    }

    public int getChannelId()
    {
        return _channelId;
    }

    void start() throws AMQException
    {
        //fixme This should be controlled by _stopped as it pairs with the stop method
        //fixme or check the FlowControlledBlockingQueue _queue to see if we have flow controlled.
        //will result in sending Flow messages for each subsequent call to flow.. only need to do this
        // if we have called stop.
        if (_startedAtLeastOnce.getAndSet(true))
        {
            //then we stopped this and are restarting, so signal server to resume delivery
            suspendChannel(false);
        }

        if (hasMessageListeners())
        {
            startDistpatcherIfNecessary();
        }
    }

    private boolean hasMessageListeners()
    {
        return _hasMessageListeners;
    }

    void setHasMessageListeners()
    {
        _hasMessageListeners = true;
    }

    synchronized void startDistpatcherIfNecessary()
    {
        if (_dispatcher == null)
        {
            _dispatcher = new Dispatcher();
            _dispatcher.setDaemon(true);
            _dispatcher.start();
        }
        else
        {
            _dispatcher.setConnectionStopped(false);
        }
    }

    void stop() throws AMQException
    {
        //stop the server delivering messages to this session
        suspendChannel(true);

        if (_dispatcher != null)
        {
            _dispatcher.setConnectionStopped(true);
        }
    }

    /**
     * Callers must hold the failover mutex before calling this method.
     *
     * @param consumer
     *
     * @throws AMQException
     */
    void registerConsumer(BasicMessageConsumer consumer, boolean nowait) throws AMQException
    {
        AMQDestination amqd = consumer.getDestination();

        declareExchange(amqd.getExchangeName(), amqd.getExchangeClass());

        AMQShortString queueName = declareQueue(amqd);

        bindQueue(amqd, queueName, consumer.getRawSelectorFieldTable());

        try
        {
            consumeFromQueue(consumer, queueName, nowait, consumer.getMessageSelector());
        }
        catch (JMSException e) //thrown by getMessageSelector
        {
            throw new AMQException(e.getMessage(), e);
        }
    }

    /**
     * Called by the MessageConsumer when closing, to deregister the consumer from the map from consumerTag to consumer
     * instance.
     *
     * @param consumer the consum
     */
    void deregisterConsumer(BasicMessageConsumer consumer)
    {
        if (_consumers.remove(consumer.getConsumerTag()) != null)
        {
            String subscriptionName = _reverseSubscriptionMap.remove(consumer);
            if (subscriptionName != null)
            {
                _subscriptions.remove(subscriptionName);
            }

            Destination dest = consumer.getDestination();
            synchronized (dest)
            {
                if (_destinationConsumerCount.get(dest).decrementAndGet() == 0)
                {
                    _destinationConsumerCount.remove(dest);
                }
            }

            //ensure we remove the messages from the consumer even if the dispatcher hasn't started
            if (_dispatcher == null)
            {
                rejectMessagesForConsumerTag(consumer.getConsumerTag(), true);
            }// if the dispatcher is running we have to do the clean up in the Ok Handler.
        }
    }

    private void registerProducer(long producerId, MessageProducer producer)
    {
        _producers.put(new Long(producerId), producer);
    }

    void deregisterProducer(long producerId)
    {
        _producers.remove(new Long(producerId));
    }

    private long getNextProducerId()
    {
        return ++_nextProducerId;
    }

    /**
     * Resubscribes all producers and consumers. This is called when performing failover.
     *
     * @throws AMQException
     */
    void resubscribe() throws AMQException
    {
        resubscribeProducers();
        resubscribeConsumers();
    }

    private void resubscribeProducers() throws AMQException
    {
        ArrayList producers = new ArrayList(_producers.values());
        _logger.info(MessageFormat.format("Resubscribing producers = {0} producers.size={1}", producers, producers.size())); // FIXME: removeKey
        for (Iterator it = producers.iterator(); it.hasNext();)
        {
            BasicMessageProducer producer = (BasicMessageProducer) it.next();
            producer.resubscribe();
        }
    }

    private void resubscribeConsumers() throws AMQException
    {
        ArrayList consumers = new ArrayList(_consumers.values());
        _consumers.clear();

        for (Iterator it = consumers.iterator(); it.hasNext();)
        {
            BasicMessageConsumer consumer = (BasicMessageConsumer) it.next();
            registerConsumer(consumer, true);
        }
    }

    private void suspendChannel(boolean suspend) throws AMQException
    {
        synchronized (_suspensionLock)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Setting channel flow : " + (suspend ? "suspended" : "unsuspended"));
            }

            _suspended = suspend;

            AMQMethodBody flowBody = getAMQMethodFactory().createChannelFlow(!suspend);
            sendCommandReceiveResponse(flowBody);

        }
    }


    public void confirmConsumerCancelled(AMQShortString consumerTag)
    {
        BasicMessageConsumer consumer = (BasicMessageConsumer) _consumers.get(consumerTag);
        if (consumer != null)
        {
            if (consumer.isAutoClose())
            {
                consumer.closeWhenNoMessages(true);
            }
            else
            {
                consumer.rollback();
            }
        }

        //Flush any pending messages for this consumerTag
        if (_dispatcher != null)
        {
            _dispatcher.rejectPending(consumerTag);
        }
        else
        {
            rejectMessagesForConsumerTag(consumerTag, true);
        }
    }

    /*
     * I could have combined the last 3 methods, but this way it improves readability
     */
    private AMQTopic checkValidTopic(Topic topic) throws JMSException
    {
        if (topic == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Topic");
        }
        if ((topic instanceof TemporaryDestination) && ((TemporaryDestination) topic).getSession() != this)
        {
            throw new javax.jms.InvalidDestinationException("Cannot create a subscription on a temporary topic created in another session");
        }
        if (!(topic instanceof AMQTopic))
        {
            throw new javax.jms.InvalidDestinationException("Cannot create a subscription on topic created for another JMS Provider, class of topic provided is: " + topic.getClass().getName());
        }
        return (AMQTopic) topic;
    }

    private void checkValidQueue(Queue queue) throws InvalidDestinationException
    {
        if (queue == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }

    private void checkValidDestination(Destination destination) throws InvalidDestinationException
    {
        if (destination == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }


    public AMQShortString getTemporaryTopicExchangeName()
    {
        return _connection.getTemporaryTopicExchangeName();
    }

    public AMQShortString getTemporaryQueueExchangeName()
    {
        return _connection.getTemporaryQueueExchangeName();
    }


    public int getTicket()
    {
        return _ticket;
    }

    public void setTicket(int ticket)
    {
        _ticket = ticket;
    }


    public void requestAccess(AMQShortString realm, boolean exclusive, boolean passive, boolean active, boolean write, boolean read) throws AMQException
    {

        AccessRequestBody accessRequest = getAMQMethodFactory().createAccessRequest(active,exclusive,passive,read,realm,write);
        AccessRequestOkBody okBody = getProtocolOutputHandler().sendCommandReceiveResponse(_channelId,accessRequest, AccessRequestOkBody.class );
        setTicket(okBody.getTicket());
    }

    AMQMethodFactory getAMQMethodFactory()
    {
        return getProtocolOutputHandler().getAMQMethodFactory();
    }

    private class SuspenderRunner implements Runnable
    {
        private boolean _suspend;

        public SuspenderRunner(boolean suspend)
        {
            _suspend = suspend;
        }

        public void run()
        {
            try
            {
                suspendChannel(_suspend);
            }
            catch (AMQException e)
            {
                _logger.warn("Unable to suspend channel");
            }
        }
    }


    private void rejectAllMessages(boolean requeue)
    {
        rejectMessagesForConsumerTag(null, requeue);
    }

    /**
     * @param consumerTag The consumerTag to prune from queue or all if null
     * @param requeue     Should the removed messages be requeued (or discarded. Possibly to DLQ)
     */

    private void rejectMessagesForConsumerTag(AMQShortString consumerTag, boolean requeue)
    {
        Iterator messages = _queue.iterator();

        while (messages.hasNext())
        {
            UnprocessedMessage message = (UnprocessedMessage) messages.next();

            if (consumerTag == null || message.getDeliverBody().getConsumerTag().equals(consumerTag))
            {
                if (_logger.isTraceEnabled())
                {
                    _logger.trace("Removing message from _queue:" + message);
                }

                messages.remove();

                rejectMessage(message.getDeliverBody().getDeliveryTag(), requeue);

                _logger.debug("Rejected the message(" + message.getDeliverBody() + ") for consumer :" + consumerTag);
            }
            else
            {
                _logger.error("Pruned pending message for consumer:" + consumerTag);
            }
        }
    }

    public void rejectMessage(long deliveryTag, boolean requeue)
    {
        AMQMethodBody rejectBody = getAMQMethodFactory().createRejectBody(deliveryTag, requeue);
        sendCommand(rejectBody);

    }

    <T extends AMQMethodBody> T sendCommandReceiveResponse(AMQMethodBody command, Class<T> responseClass) throws AMQException
    {
        return getProtocolOutputHandler().sendCommandReceiveResponse(_channelId, command, responseClass);
    }
    AMQMethodBody sendCommandReceiveResponse(AMQMethodBody command) throws AMQException
    {
        return getProtocolOutputHandler().sendCommandReceiveResponse(_channelId, command);
    }
    AMQMethodBody sendCommandReceiveResponse(AMQMethodBody command, long timeout) throws AMQException
    {
        return getProtocolOutputHandler().sendCommandReceiveResponse(_channelId, command, timeout);
    }


    void sendCommand(AMQMethodBody command)
    {
        getProtocolOutputHandler().sendCommand(_channelId, command);
    }
}
