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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQChannelClosedException;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.AMQInvalidRoutingKeyException;
import org.apache.qpid.client.AMQDestination.DestSyntax;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverNoopSupport;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.AMQPEncodedMapMessage;
import org.apache.qpid.client.message.AMQPEncodedListMessage;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.CloseConsumerMessage;
import org.apache.qpid.client.message.JMSBytesMessage;
import org.apache.qpid.client.message.JMSMapMessage;
import org.apache.qpid.client.message.JMSObjectMessage;
import org.apache.qpid.client.message.JMSStreamMessage;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.util.FlowControllingBlockingQueue;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.Session;
import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.TransportException;

import javax.jms.*;
import javax.jms.IllegalStateException;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td>
 * </table>
 *
 * @todo Different FailoverSupport implementation are needed on the same method call, in different situations. For
 * example, when failing-over and reestablishing the bindings, the bind cannot be interrupted by a second
 * fail-over, if it fails with an exception, the fail-over process should also fail. When binding outside of
 * the fail-over process, the retry handler could be used to automatically retry the operation once the connection
 * has been reestablished. All fail-over protected operations should be placed in private methods, with
 * FailoverSupport passed in by the caller to provide the correct support for the calling context. Sometimes the
 * fail-over process sets a nowait flag and uses an async method call instead.
 * @todo Two new objects created on every failover supported method call. Consider more efficient ways of doing this,
 * after looking at worse bottlenecks first.
 */
public abstract class AMQSession<C extends BasicMessageConsumer, P extends BasicMessageProducer> extends Closeable implements Session, QueueSession, TopicSession
{
    /** Used for debugging. */
    private static final Logger _logger = LoggerFactory.getLogger(AMQSession.class);

    /** System property to enable strict AMQP compliance. */
    public static final String STRICT_AMQP = "STRICT_AMQP";

    /** Strict AMQP default setting. */
    public static final String STRICT_AMQP_DEFAULT = "false";

    /** System property to enable failure if strict AMQP compliance is violated. */
    public static final String STRICT_AMQP_FATAL = "STRICT_AMQP_FATAL";

    /** Strickt AMQP failure default. */
    public static final String STRICT_AMQP_FATAL_DEFAULT = "true";

    /** System property to enable immediate message prefetching. */
    public static final String IMMEDIATE_PREFETCH = "IMMEDIATE_PREFETCH";

    /** Immediate message prefetch default. */
    public static final String IMMEDIATE_PREFETCH_DEFAULT = "false";

    private final boolean _delareQueues =
        Boolean.parseBoolean(System.getProperty(ClientProperties.QPID_DECLARE_QUEUES_PROP_NAME, "true"));

    private final boolean _declareExchanges =
        Boolean.parseBoolean(System.getProperty(ClientProperties.QPID_DECLARE_EXCHANGES_PROP_NAME, "true"));

    private final boolean _useAMQPEncodedMapMessage;

    private final boolean _useAMQPEncodedStreamMessage;

    /**
     * Flag indicating to start dispatcher as a daemon thread
     */
    protected final boolean DEAMON_DISPATCHER_THREAD = Boolean.getBoolean(ClientProperties.DAEMON_DISPATCHER);

    /** The connection to which this session belongs. */
    private AMQConnection _connection;

    /** Used to indicate whether or not this is a transactional session. */
    private final boolean _transacted;

    /** Holds the sessions acknowledgement mode. */
    private final int _acknowledgeMode;

    /** Holds this session unique identifier, used to distinguish it from other sessions. */
    private int _channelId;

    private int _ticket;

    /** Holds the high mark for prefetched message, at which the session is suspended. */
    private int _prefetchHighMark;

    /** Holds the low mark for prefetched messages, below which the session is resumed. */
    private int _prefetchLowMark;

    /** Holds the message listener, if any, which is attached to this session. */
    private MessageListener _messageListener = null;

    /** Used to indicate that this session has been started at least once. */
    private AtomicBoolean _startedAtLeastOnce = new AtomicBoolean(false);

    private final ConcurrentHashMap<String, TopicSubscriberAdaptor<C>> _subscriptions =
            new ConcurrentHashMap<String, TopicSubscriberAdaptor<C>>();

    private final ConcurrentHashMap<C, String> _reverseSubscriptionMap = new ConcurrentHashMap<C, String>();

    private final Lock _subscriberDetails = new ReentrantLock(true);
    private final Lock _subscriberAccess = new ReentrantLock(true);

    private final FlowControllingBlockingQueue _queue;

    private final AtomicLong _highestDeliveryTag = new AtomicLong(-1);
    private final AtomicLong _rollbackMark = new AtomicLong(-1);

    private ConcurrentLinkedQueue<Long> _prefetchedMessageTags = new ConcurrentLinkedQueue<Long>();

    private ConcurrentLinkedQueue<Long> _unacknowledgedMessageTags = new ConcurrentLinkedQueue<Long>();

    private ConcurrentLinkedQueue<Long> _deliveredMessageTags = new ConcurrentLinkedQueue<Long>();

    private volatile Dispatcher _dispatcher;

    private volatile Thread _dispatcherThread;

    private MessageFactoryRegistry _messageFactoryRegistry;

    /** Holds all of the producers created by this session, keyed by their unique identifiers. */
    private Map<Long, MessageProducer> _producers = new ConcurrentHashMap<Long, MessageProducer>();

    /**
     * Used as a source of unique identifiers so that the consumers can be tagged to match them to BasicConsume
     * methods.
     */
    private int _nextTag = 1;

    private final IdToConsumerMap<C> _consumers = new IdToConsumerMap<C>();

    /**
     * Contains a list of consumers which have been removed but which might still have
     * messages to acknowledge, eg in client ack or transacted modes
     */
    private CopyOnWriteArrayList<C> _removedConsumers = new CopyOnWriteArrayList<C>();

    /** Provides a count of consumers on destinations, in order to be able to know if a destination has consumers. */
    private ConcurrentHashMap<Destination, AtomicInteger> _destinationConsumerCount =
            new ConcurrentHashMap<Destination, AtomicInteger>();

    /**
     * Used as a source of unique identifiers for producers within the session.
     *
     * <p/> Access to this id does not require to be synchronized since according to the JMS specification only one
     * thread of control is allowed to create producers for any given session instance.
     */
    private long _nextProducerId;

    /**
     * Set when recover is called. This is to handle the case where recover() is called by application code during
     * onMessage() processing to ensure that an auto ack is not sent.
     */
    private volatile boolean _sessionInRecovery;

    private volatile boolean _usingDispatcherForCleanup;

    /** Used to indicates that the connection to which this session belongs, has been stopped. */
    private boolean _connectionStopped;

    /** Used to indicate that this session has a message listener attached to it. */
    private boolean _hasMessageListeners;

    /** Used to indicate that this session has been suspended. */
    private boolean _suspended;

    /**
     * Used to protect the suspension of this session, so that critical code can be executed during suspension,
     * without the session being resumed by other threads.
     */
    private final Object _suspensionLock = new Object();

    private final AtomicBoolean _firstDispatcher = new AtomicBoolean(true);

    private final boolean _immediatePrefetch;

    private final boolean _strictAMQP;

    private final boolean _strictAMQPFATAL;
    private final Object _messageDeliveryLock = new Object();

    /** Session state : used to detect if commit is a) required b) allowed , i.e. does the tx span failover. */
    private boolean _dirty;
    /** Has failover occured on this session with outstanding actions to commit? */
    private boolean _failedOverDirty;

    /** Holds the highest received delivery tag. */
    protected AtomicLong getHighestDeliveryTag()
    {
        return _highestDeliveryTag;
    }

    /** Pre-fetched message tags */
    protected ConcurrentLinkedQueue<Long> getPrefetchedMessageTags()
    {
        return _prefetchedMessageTags;
    }

    /** All the not yet acknowledged message tags */
    protected ConcurrentLinkedQueue<Long> getUnacknowledgedMessageTags()
    {
        return _unacknowledgedMessageTags;
    }

    /** All the delivered message tags */
    protected ConcurrentLinkedQueue<Long> getDeliveredMessageTags()
    {
        return _deliveredMessageTags;
    }

    /** Holds the dispatcher thread for this session. */
    protected Dispatcher getDispatcher()
    {
        return _dispatcher;
    }

    protected Thread getDispatcherThread()
    {
        return _dispatcherThread;
    }

    /** Holds the message factory factory for this session. */
    protected MessageFactoryRegistry getMessageFactoryRegistry()
    {
        return _messageFactoryRegistry;
    }

    /**
     * Maps from identifying tags to message consumers, in order to pass dispatch incoming messages to the right
     * consumer.
     */
    protected IdToConsumerMap<C> getConsumers()
    {
        return _consumers;
    }

    protected void setUsingDispatcherForCleanup(boolean usingDispatcherForCleanup)
    {
        _usingDispatcherForCleanup = usingDispatcherForCleanup;
    }

    /** Used to indicate that the session should start pre-fetching messages as soon as it is started. */
    protected boolean isImmediatePrefetch()
    {
        return _immediatePrefetch;
    }

    public static final class IdToConsumerMap<C extends BasicMessageConsumer>
    {
        private final BasicMessageConsumer[] _fastAccessConsumers = new BasicMessageConsumer[16];
        private final ConcurrentHashMap<Integer, C> _slowAccessConsumers = new ConcurrentHashMap<Integer, C>();

        public C get(int id)
        {
            if ((id & 0xFFFFFFF0) == 0)
            {
                return (C) _fastAccessConsumers[id];
            }
            else
            {
                return _slowAccessConsumers.get(id);
            }
        }

        public C put(int id, C consumer)
        {
            C oldVal;
            if ((id & 0xFFFFFFF0) == 0)
            {
                oldVal = (C) _fastAccessConsumers[id];
                _fastAccessConsumers[id] = consumer;
            }
            else
            {
                oldVal = _slowAccessConsumers.put(id, consumer);
            }

            return oldVal;

        }

        public C remove(int id)
        {
            C consumer;
            if ((id & 0xFFFFFFF0) == 0)
            {
                consumer = (C) _fastAccessConsumers[id];
                _fastAccessConsumers[id] = null;
            }
            else
            {
                consumer = _slowAccessConsumers.remove(id);
            }

            return consumer;

        }

        public Collection<C> values()
        {
            ArrayList<C> values = new ArrayList<C>();

            for (int i = 0; i < 16; i++)
            {
                if (_fastAccessConsumers[i] != null)
                {
                    values.add((C) _fastAccessConsumers[i]);
                }
            }
            values.addAll(_slowAccessConsumers.values());

            return values;
        }

        public void clear()
        {
            _slowAccessConsumers.clear();
            for (int i = 0; i < 16; i++)
            {
                _fastAccessConsumers[i] = null;
            }
        }
    }

    /**
     * Creates a new session on a connection.
     *
     * @param con                     The connection on which to create the session.
     * @param channelId               The unique identifier for the session.
     * @param transacted              Indicates whether or not the session is transactional.
     * @param acknowledgeMode         The acknowledgement mode for the session.
     * @param messageFactoryRegistry  The message factory factory for the session.
     * @param defaultPrefetchHighMark The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLowMark  The number of prefetched messages at which to resume the session.
     */
    protected AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {
        _useAMQPEncodedMapMessage = con == null ? true : !con.isUseLegacyMapMessageFormat();
        _useAMQPEncodedStreamMessage = con == null ? false : !con.isUseLegacyStreamMessageFormat();
        _strictAMQP = Boolean.parseBoolean(System.getProperties().getProperty(STRICT_AMQP, STRICT_AMQP_DEFAULT));
        _strictAMQPFATAL =
                Boolean.parseBoolean(System.getProperties().getProperty(STRICT_AMQP_FATAL, STRICT_AMQP_FATAL_DEFAULT));
        _immediatePrefetch =
                _strictAMQP
                || Boolean.parseBoolean(System.getProperties().getProperty(IMMEDIATE_PREFETCH, IMMEDIATE_PREFETCH_DEFAULT));

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
        _prefetchHighMark = defaultPrefetchHighMark;
        _prefetchLowMark = defaultPrefetchLowMark;

        if (_acknowledgeMode == NO_ACKNOWLEDGE)
        {
            _queue =
                    new FlowControllingBlockingQueue(_prefetchHighMark, _prefetchLowMark,
                                                     new FlowControllingBlockingQueue.ThresholdListener()
                                                     {
                                                         private final AtomicBoolean _suspendState = new AtomicBoolean();

                                                         public void aboveThreshold(int currentValue)
                                                         {
                                                             // If the session has been closed don't waste time creating a thread to do
                                                             // flow control
                                                             if (!(AMQSession.this.isClosed() || AMQSession.this.isClosing()))
                                                             {   
                                                                 // Only execute change if previous state
                                                                 // was False
                                                                 if (!_suspendState.getAndSet(true))
                                                                 {
                                                                     if (_logger.isDebugEnabled())
                                                                     {
                                                                         _logger.debug(
                                                                                 "Above threshold(" + _prefetchHighMark
                                                                                 + ") so suspending channel. Current value is " + currentValue);
                                                                     }
                                                                     try
                                                                     {
                                                                         Threading.getThreadFactory().createThread(new SuspenderRunner(_suspendState)).start();
                                                                     }
                                                                     catch (Exception e)
                                                                     {
                                                                         throw new RuntimeException("Failed to create thread", e);
                                                                     }
                                                                 }
                                                             }
                                                         }

                                                         public void underThreshold(int currentValue)
                                                         {
                                                             // If the session has been closed don't waste time creating a thread to do
                                                             // flow control
                                                             if (!(AMQSession.this.isClosed() || AMQSession.this.isClosing()))
                                                             {
                                                                 // Only execute change if previous state
                                                                 // was true
                                                                 if (_suspendState.getAndSet(false))
                                                                 {
                                                                     if (_logger.isDebugEnabled())
                                                                     {

                                                                         _logger.debug(
                                                                                 "Below threshold(" + _prefetchLowMark
                                                                                 + ") so unsuspending channel. Current value is " + currentValue);
                                                                     }
                                                                     try
                                                                     {
                                                                         Threading.getThreadFactory().createThread(new SuspenderRunner(_suspendState)).start();
                                                                     }
                                                                     catch (Exception e)
                                                                     {
                                                                         throw new RuntimeException("Failed to create thread", e);
                                                                     }
                                                                 }
                                                             }
                                                         }
                                                     });
        }
        else
        {
            _queue = new FlowControllingBlockingQueue(_prefetchHighMark, null);
        }

        // Add creation logging to tie in with the existing close logging
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Created session:" + this);
        }
    }

    /**
     * Creates a new session on a connection with the default message factory factory.
     *
     * @param con                 The connection on which to create the session.
     * @param channelId           The unique identifier for the session.
     * @param transacted          Indicates whether or not the session is transactional.
     * @param acknowledgeMode     The acknowledgement mode for the session.
     * @param defaultPrefetchHigh The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLow  The number of prefetched messages at which to resume the session.
     */
    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetchHigh,
               int defaultPrefetchLow)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetchHigh,
             defaultPrefetchLow);
    }

    // ===== JMS Session methods.

    /**
     * Closes the session with no timeout.
     *
     * @throws JMSException If the JMS provider fails to close the session due to some internal error.
     */
    public void close() throws JMSException
    {
        close(-1);
    }

    public abstract AMQException getLastException();
    
    public void checkNotClosed() throws JMSException
    {
        try
        {
            super.checkNotClosed();
        }
        catch (IllegalStateException ise)
        {
            AMQException ex = getLastException();
            if (ex != null)
            {
                IllegalStateException ssnClosed = new IllegalStateException(
                        "Session has been closed", ex.getErrorCode().toString());

                ssnClosed.setLinkedException(ex);
                ssnClosed.initCause(ex);
                throw ssnClosed;
            } 
            else
            {
                throw ise;
            }
        }
    }

    public BytesMessage createBytesMessage() throws JMSException
    {
        checkNotClosed();
        JMSBytesMessage msg = new JMSBytesMessage(getMessageDelegateFactory());
        msg.setAMQSession(this);
        return msg;
    }

    /**
     * Acknowledges all unacknowledged messages on the session, for all message consumers on the session.
     *
     * @throws IllegalStateException If the session is closed.
     * @throws JMSException if there is a problem during acknowledge process.
     */
    public void acknowledge() throws IllegalStateException, JMSException
    {
        if (isClosed())
        {
            throw new IllegalStateException("Session is already closed");
        }
        else if (hasFailedOverDirty())
        {
            //perform an implicit recover in this scenario
            recover();

            //notify the consumer
            throw new IllegalStateException("has failed over");
        }

        try
        {
            acknowledgeImpl();
            markClean();
        }
        catch (TransportException e)
        {
            throw toJMSException("Exception while acknowledging message(s):" + e.getMessage(), e);
        }
    }

    protected abstract void acknowledgeImpl() throws JMSException;

    /**
     * Acknowledge one or many messages.
     *
     * @param deliveryTag The tag of the last message to be acknowledged.
     * @param multiple    <tt>true</tt> to acknowledge all messages up to and including the one specified by the
     *                    delivery tag, <tt>false</tt> to just acknowledge that message.
     *
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    public abstract void acknowledgeMessage(long deliveryTag, boolean multiple);

    /**
     * Binds the named queue, with the specified routing key, to the named exchange.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param queueName    The name of the queue to bind.
     * @param routingKey   The routing key to bind the queue with.
     * @param arguments    Additional arguments.
     * @param exchangeName The exchange to bind the queue on.
     *
     * @throws AMQException If the queue cannot be bound for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     * @todo Document the additional arguments that may be passed in the field table. Are these for headers exchanges?
     */
    public void bindQueue(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
                          final AMQShortString exchangeName, final AMQDestination destination) throws AMQException
    {
        bindQueue(queueName, routingKey, arguments, exchangeName, destination, false);
    }

    public void bindQueue(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
                          final AMQShortString exchangeName, final AMQDestination destination,
                          final boolean nowait) throws AMQException
    {
        /*new FailoverRetrySupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()*/
        new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
        {
            public Object execute() throws AMQException, FailoverException
            {
                sendQueueBind(queueName, routingKey, arguments, exchangeName, destination, nowait);
                return null;
            }
        }, _connection).execute();
    }

    public void addBindingKey(C consumer, AMQDestination amqd, String routingKey) throws AMQException
    {
        if (consumer.getQueuename() != null)
        {
            bindQueue(consumer.getQueuename(), new AMQShortString(routingKey), new FieldTable(), amqd.getExchangeName(), amqd);
        }
    }

    public abstract void sendQueueBind(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
                                       final AMQShortString exchangeName, AMQDestination destination,
                                       final boolean nowait) throws AMQException, FailoverException;

    /**
     * Closes the session.
     *
     * <p/>Note that this operation succeeds automatically if a fail-over interrupts the synchronous request to close
     * the channel. This is because the channel is marked as closed before the request to close it is made, so the
     * fail-over should not re-open it.
     *
     * @param timeout The timeout in milliseconds to wait for the session close acknowledgement from the broker.
     *
     * @throws JMSException If the JMS provider fails to close the session due to some internal error.
     * @todo Be aware of possible changes to parameter order as versions change.
     * @todo Not certain about the logic of ignoring the failover exception, because the channel won't be
     * re-opened. May need to examine this more carefully.
     * @todo Note that taking the failover mutex doesn't prevent this operation being interrupted by a failover,
     * because the failover process sends the failover event before acquiring the mutex itself.
     */
    public void close(long timeout) throws JMSException
    {
        close(timeout, true);
    }

    private void close(long timeout, boolean sendClose) throws JMSException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Closing session: " + this);
        }

        // Ensure we only try and close an open session.
        if (!setClosed())
        {
            setClosing(true);
            synchronized (getFailoverMutex())
            {
                // We must close down all producers and consumers in an orderly fashion. This is the only method
                // that can be called from a different thread of control from the one controlling the session.
                synchronized (_messageDeliveryLock)
                {
                    // we pass null since this is not an error case
                    closeProducersAndConsumers(null);

                    try
                    {
                        // If the connection is open or we are in the process
                        // of closing the connection then send a cance
                        // no point otherwise as the connection will be gone
                        if (!_connection.isClosed() || _connection.isClosing())
                        {
                            if (sendClose)
                            {
                                sendClose(timeout);
                            }
                        }
                    }
                    catch (AMQException e)
                    {
                        JMSException jmse = new JMSException("Error closing session: " + e);
                        jmse.setLinkedException(e);
                        jmse.initCause(e);
                        throw jmse;
                    }
                    // This is ignored because the channel is already marked as closed so the fail-over process will
                    // not re-open it.
                    catch (FailoverException e)
                    {
                        _logger.debug(
                                "Got FailoverException during channel close, ignored as channel already marked as closed.");
                    }
                    catch (TransportException e)
                    {
                        throw toJMSException("Error closing session:" + e.getMessage(), e);
                    }
                    finally
                    {
                        _connection.deregisterSession(_channelId);
                    }
                }
            }
        }
    }

    public abstract void sendClose(long timeout) throws AMQException, FailoverException;

    /**
     * Called when the server initiates the closure of the session unilaterally.
     *
     * @param e the exception that caused this session to be closed. Null causes the
     */
    public void closed(Throwable e) throws JMSException
    {
        // This method needs to be improved. Throwables only arrive here from the mina : exceptionRecived
        // calls through connection.closeAllSessions which is also called by the public connection.close()
        // with a null cause
        // When we are closing the Session due to a protocol session error we simply create a new AMQException
        // with the correct error code and text this is cleary WRONG as the instanceof check below will fail.
        // We need to determin here if the connection should be

        if (e instanceof AMQDisconnectedException)
        {
            // Failover failed and ain't coming back. Knife the dispatcher.
            stopDispatcherThread();

       }

        //if we don't have an exception then we can perform closing operations
        setClosing(e == null);

        if (!setClosed())
        {
            synchronized (_messageDeliveryLock)
            {
                // An AMQException has an error code and message already and will be passed in when closure occurs as a
                // result of a channel close request
                AMQException amqe;
                if (e instanceof AMQException)
                {
                    amqe = (AMQException) e;
                }
                else
                {
                    amqe = new AMQException("Closing session forcibly", e);
                }

                _connection.deregisterSession(_channelId);
                closeProducersAndConsumers(amqe);
            }
        }
    }

    protected void stopDispatcherThread()
    {
        if (_dispatcherThread != null)
        {
            _dispatcherThread.interrupt();
        }
    }
    /**
     * Commits all messages done in this transaction and releases any locks currently held.
     *
     * <p/>If the commit fails, because the commit itself is interrupted by a fail-over between requesting that the
     * commit be done, and receiving an acknowledgement that it has been done, then a JMSException will be thrown.
     * The client will be unable to determine whether or not the commit actually happened on the broker in this case.
     *
     * @throws JMSException If the JMS provider fails to commit the transaction due to some internal error. This does
     *                      not mean that the commit is known to have failed, merely that it is not known whether it
     *                      failed or not.
     */
    public void commit() throws JMSException
    {
        checkTransacted();

        //Check that we are clean to commit.
        if (_failedOverDirty)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Session " + _channelId + " was dirty whilst failing over. Rolling back.");
            }
            rollback();

            throw new TransactionRolledBackException("Connection failover has occured with uncommitted transaction activity." +
                                                     "The session transaction was rolled back.");
        }

        try
        {
            commitImpl();
            markClean();
        }
        catch (AMQException e)
        {
            throw toJMSException("Exception during commit: " + e.getMessage() + ":" + e.getCause(), e);
        }
        catch (FailoverException e)
        {
            throw new JMSAMQException("Fail-over interrupted commit. Status of the commit is uncertain.", e);
        }
        catch(TransportException e)
        {
            throw toJMSException("Session exception occured while trying to commit: " + e.getMessage(), e);
        }
    }

    protected abstract void commitImpl() throws AMQException, FailoverException, TransportException;

    public void confirmConsumerCancelled(int consumerTag)
    {

        // Remove the consumer from the map
        C consumer = _consumers.get(consumerTag);
        if (consumer != null)
        {
            if (!consumer.isBrowseOnly())  // Normal Consumer
            {
                // Clean the Maps up first
                // Flush any pending messages for this consumerTag
                if (_dispatcher != null)
                {
                    _logger.debug("Dispatcher is not null");
                }
                else
                {
                    _logger.debug("Dispatcher is null so created stopped dispatcher");
                    startDispatcherIfNecessary(true);
                }

                _dispatcher.rejectPending(consumer);
            }
            else // Queue Browser
            {
                // Just close the consumer
                // fixme  the CancelOK is being processed before the arriving messages..
                // The dispatcher is still to process them so the server sent in order but the client
                // has yet to receive before the close comes in

                if (consumer.isAutoClose())
                {
                    // There is a small window where the message is between the two queues in the dispatcher.
                    if (consumer.isClosed())
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Closing consumer:" + consumer.debugIdentity());
                        }

                        deregisterConsumer(consumer);
                    }
                    else
                    {
                        _queue.add(new CloseConsumerMessage(consumer));
                    }
                }
            }
        }
    }

    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        if (isStrictAMQP())
        {
            throw new UnsupportedOperationException();
        }

        return createBrowser(queue, null);
    }

    /**
     * Create a queue browser if the destination is a valid queue.
     */
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        if (isStrictAMQP())
        {
            throw new UnsupportedOperationException();
        }

        checkNotClosed();
        checkValidQueue(queue);

        return new AMQQueueBrowser(this, queue, messageSelector);
    }

    protected MessageConsumer createBrowserConsumer(Destination destination, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, _prefetchHighMark, _prefetchLowMark, noLocal, false,
                                  messageSelector, null, true, true);
    }

    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, _prefetchHighMark, _prefetchLowMark, false, (destination instanceof Topic), null, null,
                                  isBrowseOnlyDestination(destination), false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, _prefetchHighMark, _prefetchLowMark, false, (destination instanceof Topic),
                                  messageSelector, null, isBrowseOnlyDestination(destination), false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, _prefetchHighMark, _prefetchLowMark, noLocal, (destination instanceof Topic),
                                  messageSelector, null, isBrowseOnlyDestination(destination), false);
    }

    public MessageConsumer createConsumer(Destination destination, int prefetch, boolean noLocal, boolean exclusive,
                                          String selector) throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, prefetch, prefetch / 2, noLocal, exclusive, selector, null, isBrowseOnlyDestination(destination), false);
    }

    public MessageConsumer createConsumer(Destination destination, int prefetchHigh, int prefetchLow, boolean noLocal,
                                              boolean exclusive, String selector) throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive, selector, null, isBrowseOnlyDestination(destination), false);
    }

    public MessageConsumer createConsumer(Destination destination, int prefetchHigh, int prefetchLow, boolean noLocal,
                                          boolean exclusive, String selector, FieldTable rawSelector) throws JMSException
    {
        checkValidDestination(destination);

        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive, selector, rawSelector, isBrowseOnlyDestination(destination),
                                  false);
    }

    public  TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        // Delegate the work to the {@link #createDurableSubscriber(Topic, String, String, boolean)} method
        return createDurableSubscriber(topic, name, null, false);
    }
    
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String selector, boolean noLocal)
            throws JMSException
    {
        checkNotClosed();
        Topic origTopic = checkValidTopic(topic, true);
        
        AMQTopic dest = AMQTopic.createDurableTopic(origTopic, name, _connection);
        if (dest.getDestSyntax() == DestSyntax.ADDR &&
            !dest.isAddressResolved())
        {
            try
            {
                resolveAddress(dest,false,noLocal);
                if (dest.getAddressType() !=  AMQDestination.TOPIC_TYPE)
                {
                    throw new JMSException("Durable subscribers can only be created for Topics");
                }
            }
            catch(AMQException e)
            {
                throw toJMSException("Error when verifying destination",e);
            }
            catch(TransportException e)
            {
                throw toJMSException("Error when verifying destination", e);
            }
        }
        
        String messageSelector = ((selector == null) || (selector.trim().length() == 0)) ? null : selector;
        
        _subscriberDetails.lock();
        try
        {
            TopicSubscriberAdaptor<C> subscriber = _subscriptions.get(name);
            
            // Not subscribed to this name in the current session
            if (subscriber == null)
            {
                // After the address is resolved routing key will not be null.
                AMQShortString topicName = dest.getRoutingKey();
                
                if (_strictAMQP)
                {
                    if (_strictAMQPFATAL)
                    {
                        throw new UnsupportedOperationException("JMS Durable not currently supported by AMQP.");
                    }
                    else
                    {
                        _logger.warn("Unable to determine if subscription already exists for '" + topicName
                                        + "' for creation durableSubscriber. Requesting queue deletion regardless.");
                    }

                    deleteQueue(dest.getAMQQueueName());
                }
                else
                {
                    Map<String,Object> args = new HashMap<String,Object>();
                    
                    // We must always send the selector argument even if empty, so that we can tell when a selector is removed from a 
                    // durable topic subscription that the broker arguments don't match any more. This is because it is not otherwise
                    // possible to determine  when querying the broker whether there are no arguments or just a non-matching selector
                    // argument, as specifying null for the arguments when querying means they should not be checked at all
                    args.put(AMQPFilterTypes.JMS_SELECTOR.getValue().toString(), messageSelector == null ? "" : messageSelector);
                    if(noLocal)
                    {
                        args.put(AMQPFilterTypes.NO_LOCAL.getValue().toString(), true);
                    }

                    // if the queue is bound to the exchange but NOT for this topic and selector, then the JMS spec
                    // says we must trash the subscription.
                    boolean isQueueBound = isQueueBound(dest.getExchangeName(), dest.getAMQQueueName());
                    boolean isQueueBoundForTopicAndSelector = 
                                isQueueBound(dest.getExchangeName().asString(), dest.getAMQQueueName().asString(), topicName.asString(), args);

                    if (isQueueBound && !isQueueBoundForTopicAndSelector)
                    {
                        deleteQueue(dest.getAMQQueueName());
                    }
                }
            }
            else 
            {
                // Subscribed with the same topic and no current / previous or same selector
                if (subscriber.getTopic().equals(topic)
                    && ((messageSelector == null && subscriber.getMessageSelector() == null)
                            || (messageSelector != null && messageSelector.equals(subscriber.getMessageSelector()))))
                {
                    throw new IllegalStateException("Already subscribed to topic " + topic + " with subscription name " + name
                            + (messageSelector != null ? " and selector " + messageSelector : ""));
                }
                else
                {
                    unsubscribe(name, true);
                }

            }

            _subscriberAccess.lock();
            try
            {
                C consumer = (C) createConsumer(dest, messageSelector, noLocal);
                subscriber = new TopicSubscriberAdaptor<C>(dest, consumer);

                // Save subscription information
                _subscriptions.put(name, subscriber);
                _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);
            }
            finally
            {
                _subscriberAccess.unlock();
            }
    
            return subscriber;
        }
        catch (TransportException e)
        {
            throw toJMSException("Exception while creating durable subscriber:" + e.getMessage(), e);
        }
        finally
        {
            _subscriberDetails.unlock();
        }
    }

    public ListMessage createListMessage() throws JMSException
    {
        checkNotClosed();
        AMQPEncodedListMessage msg = new AMQPEncodedListMessage(getMessageDelegateFactory());
        msg.setAMQSession(this);
        return msg;
    }

    public MapMessage createMapMessage() throws JMSException
    {
        checkNotClosed();
        if (_useAMQPEncodedMapMessage)
        {
            AMQPEncodedMapMessage msg = new AMQPEncodedMapMessage(getMessageDelegateFactory());
            msg.setAMQSession(this);
            return msg;
        }
        else
        {
            JMSMapMessage msg = new JMSMapMessage(getMessageDelegateFactory());
            msg.setAMQSession(this);
            return msg;
        }
    }

    public javax.jms.Message createMessage() throws JMSException
    {
        return createBytesMessage();
    }

    public ObjectMessage createObjectMessage() throws JMSException
    {
        checkNotClosed();
         JMSObjectMessage msg = new JMSObjectMessage(getMessageDelegateFactory());
         msg.setAMQSession(this);
         return msg;
    }

    public ObjectMessage createObjectMessage(Serializable object) throws JMSException
    {
        ObjectMessage msg = createObjectMessage();
        msg.setObject(object);

        return msg;
    }

    public P createProducer(Destination destination) throws JMSException
    {
        return createProducerImpl(destination, null, null);
    }

    public P createProducer(Destination destination, boolean immediate) throws JMSException
    {
        return createProducerImpl(destination, null, immediate);
    }

    public P createProducer(Destination destination, boolean mandatory, boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate);
    }

    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        checkNotClosed();

        return new TopicPublisherAdapter((P) createProducer(topic, false, false), topic);
    }

    public Queue createQueue(String queueName) throws JMSException
    {
        checkNotClosed();
        try
        {
            if (queueName.indexOf('/') == -1 && queueName.indexOf(';') == -1)
            {
                DestSyntax syntax = AMQDestination.getDestType(queueName);
                if (syntax == AMQDestination.DestSyntax.BURL)
                {
                    // For testing we may want to use the prefix
                    return new AMQQueue(getDefaultQueueExchangeName(), 
                                        new AMQShortString(AMQDestination.stripSyntaxPrefix(queueName)));
                }
                else
                {
                    AMQQueue queue = new AMQQueue(queueName);
                    return queue;
                    
                }
            }
            else
            {
                return new AMQQueue(queueName);            
            }
        }
        catch (URISyntaxException urlse)
        {
            _logger.error("", urlse);
            JMSException jmse = new JMSException(urlse.getReason());
            jmse.setLinkedException(urlse);
            jmse.initCause(urlse);
            throw jmse;
        }

    }

    /**
     * Declares the named queue.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param name       The name of the queue to declare.
     * @param autoDelete
     * @param durable    Flag to indicate that the queue is durable.
     * @param exclusive  Flag to indicate that the queue is exclusive to this client.
     *
     * @throws AMQException If the queue cannot be declared for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    public void createQueue(final AMQShortString name, final boolean autoDelete, final boolean durable,
                            final boolean exclusive) throws AMQException
    {
        createQueue(name, autoDelete, durable, exclusive, null);
    }

    /**
     * Declares the named queue.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param name       The name of the queue to declare.
     * @param autoDelete
     * @param durable    Flag to indicate that the queue is durable.
     * @param exclusive  Flag to indicate that the queue is exclusive to this client.
     * @param arguments  Arguments used to set special properties of the queue
     *
     * @throws AMQException If the queue cannot be declared for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    public void createQueue(final AMQShortString name, final boolean autoDelete, final boolean durable,
                            final boolean exclusive, final Map<String, Object> arguments) throws AMQException
    {
        new FailoverRetrySupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
        {
            public Object execute() throws AMQException, FailoverException
            {
                sendCreateQueue(name, autoDelete, durable, exclusive, arguments);
                return null;
            }
        }, _connection).execute();
    }

    public abstract void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable,
                                         final boolean exclusive, final Map<String, Object> arguments) throws AMQException, FailoverException;

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
        Queue dest = validateQueue(destination);
        C consumer = (C) createConsumer(dest);

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
        Queue dest = validateQueue(destination);
        C consumer = (C) createConsumer(dest, messageSelector);

        return new QueueReceiverAdaptor(dest, consumer);
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
        Queue dest = validateQueue(queue);
        C consumer = (C) createConsumer(dest);

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
        Queue dest = validateQueue(queue);
        C consumer = (C) createConsumer(dest, messageSelector);

        return new QueueReceiverAdaptor(dest, consumer);
    }
    
    private Queue validateQueue(Destination dest) throws InvalidDestinationException
    {
        if (dest instanceof AMQDestination && dest instanceof javax.jms.Queue)
        {
            return (Queue)dest;
        }
        else
        {
            throw new InvalidDestinationException("The destination object used is not from this provider or of type javax.jms.Queue");
        }
    }

    public QueueSender createSender(Queue queue) throws JMSException
    {
        checkNotClosed();

        return new QueueSenderAdapter(createProducer(queue), queue);
    }

    public StreamMessage createStreamMessage() throws JMSException
    {
        checkNotClosed();
        if (_useAMQPEncodedStreamMessage)
        {
            AMQPEncodedListMessage msg = new AMQPEncodedListMessage(getMessageDelegateFactory());
            msg.setAMQSession(this);
            return msg;
        }
        else
        {
            JMSStreamMessage msg = new JMSStreamMessage(getMessageDelegateFactory());
            msg.setAMQSession(this);
            return msg;
        }
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
        checkValidTopic(topic);

        return new TopicSubscriberAdaptor<C>(topic,
                createConsumerImpl(topic, _prefetchHighMark, _prefetchLowMark, false, true, null, null, false, false));
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
        checkValidTopic(topic);

        return new TopicSubscriberAdaptor<C>(topic,
                                             createConsumerImpl(topic, _prefetchHighMark, _prefetchLowMark, noLocal,
                                                                true, messageSelector, null, false, false));
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkNotClosed();
        try
        {
            AMQTemporaryQueue result = new AMQTemporaryQueue(this);

            // this is done so that we can produce to a temporary queue before we create a consumer
            result.setQueueName(result.getRoutingKey());
            createQueue(result.getAMQQueueName(), result.isAutoDelete(),
                        result.isDurable(), result.isExclusive());
            bindQueue(result.getAMQQueueName(), result.getRoutingKey(),
                    new FieldTable(), result.getExchangeName(), result);
            return result;
        }
        catch (AMQException e)
        {
           throw toJMSException("Cannot create temporary queue",e);
        }
        catch(TransportException e)
        {
            throw toJMSException("Cannot create temporary queue: " + e.getMessage(), e);
        }
        catch(Exception e)
        {
            throw new JMSAMQException("Cannot create temporary queue: " + e.getMessage(), e);
        }
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        checkNotClosed();

        return new AMQTemporaryTopic(this);
    }

    public TextMessage createTextMessage() throws JMSException
    {
        synchronized (getFailoverMutex())
        {
            checkNotClosed();

            JMSTextMessage msg = new JMSTextMessage(getMessageDelegateFactory());
            msg.setAMQSession(this);
            return msg;
        }
    }

    protected Object getFailoverMutex()
    {
        return _connection.getFailoverMutex();
    }

    public TextMessage createTextMessage(String text) throws JMSException
    {

        TextMessage msg = createTextMessage();
        msg.setText(text);

        return msg;
    }

    public Topic createTopic(String topicName) throws JMSException
    {
        checkNotClosed();
        try
        {
            if (topicName.indexOf('/') == -1 && topicName.indexOf(';') == -1)
            {
                DestSyntax syntax = AMQDestination.getDestType(topicName);
                // for testing we may want to use the prefix to indicate our choice.
                topicName = AMQDestination.stripSyntaxPrefix(topicName);
                if (syntax == AMQDestination.DestSyntax.BURL)
                {
                    return new AMQTopic(getDefaultTopicExchangeName(), new AMQShortString(topicName));
                }
                else
                {
                    return new AMQTopic("ADDR:" + getDefaultTopicExchangeName() + "/" + topicName);
                }
            }
            else
            {
                return new AMQTopic(topicName);            
            }
        
        }
        catch (URISyntaxException urlse)
        {
            _logger.error("", urlse);
            JMSException jmse = new JMSException(urlse.getReason());
            jmse.setLinkedException(urlse);
            jmse.initCause(urlse);
            throw jmse;
        }
    }

    public void declareExchange(AMQShortString name, AMQShortString type, boolean nowait) throws AMQException
    {
        declareExchange(name, type, nowait, false, false, false);
    }

    abstract public void sync() throws AMQException;

    public int getAcknowledgeMode()
    {
        return _acknowledgeMode;
    }

    public AMQConnection getAMQConnection()
    {
        return _connection;
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public int getDefaultPrefetch()
    {
        return _prefetchHighMark;
    }

    public int getDefaultPrefetchHigh()
    {
        return _prefetchHighMark;
    }

    public int getDefaultPrefetchLow()
    {
        return _prefetchLowMark;
    }

    public int getPrefetch()
    {
        return _prefetchHighMark;
    }

    public AMQShortString getDefaultQueueExchangeName()
    {
        return _connection.getDefaultQueueExchangeName();
    }

    public AMQShortString getDefaultTopicExchangeName()
    {
        return _connection.getDefaultTopicExchangeName();
    }

    public MessageListener getMessageListener() throws JMSException
    {
        return _messageListener;
    }

    public AMQShortString getTemporaryQueueExchangeName()
    {
        return _connection.getTemporaryQueueExchangeName();
    }

    public AMQShortString getTemporaryTopicExchangeName()
    {
        return _connection.getTemporaryTopicExchangeName();
    }

    public int getTicket()
    {
        return _ticket;
    }

    /**
     * Indicates whether the session is in transacted mode.
     *
     * @return true if the session is in transacted mode
     * @throws IllegalStateException - if session is closed.
     */
    public boolean getTransacted() throws JMSException
    {
        // Sun TCK checks that javax.jms.IllegalStateException is thrown for closed session
        // nowhere else this behavior is documented
        checkNotClosed();
        return _transacted;
    }

    /**
     * Indicates whether the session is in transacted mode.
     */
    public boolean isTransacted()
    {
        return _transacted;
    }

    public boolean hasConsumer(Destination destination)
    {
        AtomicInteger counter = _destinationConsumerCount.get(destination);

        return (counter != null) && (counter.get() != 0);
    }

    /** Indicates that warnings should be generated on violations of the strict AMQP. */
    public boolean isStrictAMQP()
    {
        return _strictAMQP;
    }

    public boolean isSuspended()
    {
        return _suspended;
    }

    protected void addUnacknowledgedMessage(long id)
    {
        _unacknowledgedMessageTags.add(id);
    }

    protected void addDeliveredMessage(long id)
    {
        _deliveredMessageTags.add(id);
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
            _logger.debug("Message[" + message.toString() + "] received in session");
        }
        _highestDeliveryTag.set(message.getDeliveryTag());
        _queue.add(message);        
    }

    public void declareAndBind(AMQDestination amqd)
            throws
            AMQException
    {
        declareExchange(amqd, false);
        AMQShortString queueName = declareQueue(amqd, false);
        bindQueue(queueName, amqd.getRoutingKey(), new FieldTable(), amqd.getExchangeName(), amqd);
    }

    /**
     * Stops message delivery in this session, and restarts message delivery with the oldest unacknowledged message.
     *
     * <p/>All consumers deliver messages in a serial order. Acknowledging a received message automatically acknowledges
     * all messages that have been delivered to the client.
     *
     * <p/>Restarting a session causes it to take the following actions:
     *
     * <ul>
     * <li>Stop message delivery.</li>
     * <li>Mark all messages that might have been delivered but not acknowledged as "redelivered".
     * <li>Restart the delivery sequence including all unacknowledged messages that had been previously delivered.
     * Redelivered messages do not have to be delivered in exactly their original delivery order.</li>
     * </ul>
     *
     * <p/>If the recover operation is interrupted by a fail-over, between asking that the broker begin recovery and
     * receiving acknowledgment that it has then a JMSException will be thrown. In this case it will not be possible
     * for the client to determine whether the broker is going to recover the session or not.
     *
     * @throws JMSException If the JMS provider fails to stop and restart message delivery due to some internal error.
     *                      Not that this does not necessarily mean that the recovery has failed, but simply that it is
     *                      not possible to tell if it has or not.
     * @todo Be aware of possible changes to parameter order as versions change.
     * 
     * Strategy for handling recover.
     * Flush any acks not yet sent.
     * Stop the message flow.
     * Clear the dispatch queue and the consumer queues.
     * Release/Reject all messages received but not yet acknowledged.
     * Start the message flow.
     */
    public void recover() throws JMSException
    {
        // Ensure that the session is open.
        checkNotClosed();

        // Ensure that the session is not transacted.
        checkNotTransacted();


        try
        {
            // flush any acks we are holding in the buffer.
            flushAcknowledgments();

            // this is only set true here, and only set false when the consumers preDeliver method is called
            _sessionInRecovery = true;

            boolean isSuspended = isSuspended();

            if (!isSuspended)
            {
                suspendChannel(true);
            }

            // Set to true to short circuit delivery of anything currently
            //in the pre-dispatch queue.
            _usingDispatcherForCleanup = true;

            syncDispatchQueue();

            // Set to false before sending the recover as 0-8/9/9-1 will
            //send messages back before the recover completes, and we
            //probably shouldn't clean those! ;-)
            _usingDispatcherForCleanup = false;

            if (_dispatcher != null)
            {
                _dispatcher.recover();
            }

            sendRecover();
            
            markClean();

            if (!isSuspended)
            {
                suspendChannel(false);
            }
        }
        catch (AMQException e)
        {
            throw toJMSException("Recover failed: " + e.getMessage(), e);
        }
        catch (FailoverException e)
        {
            throw new JMSAMQException("Recovery was interrupted by fail-over. Recovery status is not known.", e);
        }
        catch(TransportException e)
        {
            throw toJMSException("Recover failed: " + e.getMessage(), e);
        }
    }

    protected abstract void sendRecover() throws AMQException, FailoverException;

    protected abstract void flushAcknowledgments();
    
    public void rejectMessage(UnprocessedMessage message, boolean requeue)
    {

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting Unacked message:" + message.getDeliveryTag());
        }

        rejectMessage(message.getDeliveryTag(), requeue);
    }

    public void rejectMessage(AbstractJMSMessage message, boolean requeue)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting Abstract message:" + message.getDeliveryTag());
        }

        rejectMessage(message.getDeliveryTag(), requeue);

    }

    public abstract void rejectMessage(long deliveryTag, boolean requeue);

    /**
     * Commits all messages done in this transaction and releases any locks currently held.
     *
     * <p/>If the rollback fails, because the rollback itself is interrupted by a fail-over between requesting that the
     * rollback be done, and receiving an acknowledgement that it has been done, then a JMSException will be thrown.
     * The client will be unable to determine whether or not the rollback actually happened on the broker in this case.
     *
     * @throws JMSException If the JMS provider fails to rollback the transaction due to some internal error. This does
     *                      not mean that the rollback is known to have failed, merely that it is not known whether it
     *                      failed or not.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    public void rollback() throws JMSException
    {
        synchronized (_suspensionLock)
        {
            checkTransacted();

            try
            {
                boolean isSuspended = isSuspended();

                if (!isSuspended)
                {
                    suspendChannel(true);
                }

                setRollbackMark();

                syncDispatchQueue();

                _dispatcher.rollback();

                releaseForRollback();

                sendRollback();

                markClean();

                if (!isSuspended)
                {
                    suspendChannel(false);
                }
            }
            catch (AMQException e)
            {
                throw toJMSException("Failed to rollback: " + e, e);
            }
            catch (FailoverException e)
            {
                throw new JMSAMQException("Fail-over interrupted rollback. Status of the rollback is uncertain.", e);
            }
            catch (TransportException e)
            {
                throw toJMSException("Failure to rollback:" + e.getMessage(), e);
            }
        }
    }

    public abstract void releaseForRollback();

    public abstract void sendRollback() throws AMQException, FailoverException;

    public void run()
    {
        throw new java.lang.UnsupportedOperationException();
    }

    public void setMessageListener(MessageListener listener) throws JMSException
    {
    }
    
    /**
     * @see #unsubscribe(String, boolean)
     */
    public void unsubscribe(String name) throws JMSException
    {
        try
        {
            unsubscribe(name, false);
        }
        catch (TransportException e)
        {
            throw toJMSException("Exception while unsubscribing:" + e.getMessage(), e);
        }
    }
    
    /**
     * Unsubscribe from a subscription.
     * 
     * @param name the name of the subscription to unsubscribe
     * @param safe allows safe unsubscribe operation that will not throw an {@link InvalidDestinationException} if the
     * queue is not bound, possibly due to the subscription being closed.
     * @throws JMSException on 
     * @throws InvalidDestinationException
     */
    private void unsubscribe(String name, boolean safe) throws JMSException
    {
        TopicSubscriberAdaptor<C> subscriber;
        
        _subscriberDetails.lock();
        try
        {
            checkNotClosed();
            subscriber = _subscriptions.get(name);
            if (subscriber != null)
            {
                // Remove saved subscription information
                _subscriptions.remove(name);
                _reverseSubscriptionMap.remove(subscriber.getMessageConsumer());
            }
        }
        finally
        {
            _subscriberDetails.unlock();
        }
        
        if (subscriber != null)
        {
            subscriber.close();
            
            // send a queue.delete for the subscription
            deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
        }
        else
        {
            if (_strictAMQP)
            {
                if (_strictAMQPFATAL)
                {
                    throw new UnsupportedOperationException("JMS Durable not currently supported by AMQP.");
                }
                else
                {
                    _logger.warn("Unable to determine if subscription already exists for '" + name + "' for unsubscribe."
                                 + " Requesting queue deletion regardless.");
                }
                
                deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
            }
            else // Queue Browser
            {

                if (isQueueBound(getDefaultTopicExchangeName(), AMQTopic.getDurableTopicQueueName(name, _connection)))
                {
                    deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
                }
                else if (!safe)
                {
                    throw new InvalidDestinationException("Unknown subscription name: " + name);
                }
            }
        }
    }

    protected C createConsumerImpl(final Destination destination, final int prefetchHigh,
                                                 final int prefetchLow, final boolean noLocal, final boolean exclusive, String selector, final FieldTable rawSelector,
                                                 final boolean noConsume, final boolean autoClose) throws JMSException
    {
        checkTemporaryDestination(destination);

        if(!noConsume && isBrowseOnlyDestination(destination))
        {
            throw new InvalidDestinationException("The consumer being created is not 'noConsume'," +
                                                  "but a 'browseOnly' Destination has been supplied.");
        }

        final String messageSelector;

        if (_strictAMQP && !((selector == null) || selector.equals("")))
        {
            if (_strictAMQPFATAL)
            {
                throw new UnsupportedOperationException("Selectors not currently supported by AMQP.");
            }
            else
            {
                messageSelector = null;
            }
        }
        else
        {
            messageSelector = selector;
        }

        return new FailoverRetrySupport<C, JMSException>(
                new FailoverProtectedOperation<C, JMSException>()
                {
                    public C execute() throws JMSException, FailoverException
                    {
                        checkNotClosed();

                        AMQDestination amqd = (AMQDestination) destination;

                        C consumer;
                        try
                        {
                            consumer = createMessageConsumer(amqd, prefetchHigh, prefetchLow,
                                                             noLocal, exclusive, messageSelector, rawSelector, noConsume, autoClose);
                        }
                        catch(TransportException e)
                        {
                            throw toJMSException("Exception while creating consumer: " + e.getMessage(), e);
                        }

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
                            JMSException jmse = new InvalidSelectorException(ise.getMessage());
                            jmse.setLinkedException(ise);
                            jmse.initCause(ise);
                            throw jmse;
                        }
                        catch (AMQInvalidRoutingKeyException e)
                        {
                            JMSException jmse = new InvalidDestinationException("Invalid routing key:" + amqd.getRoutingKey().toString());
                            jmse.setLinkedException(e);
                            jmse.initCause(e);
                            throw jmse;
                        }
                        catch (AMQException e)
                        {
                            if (e instanceof AMQChannelClosedException)
                            {
                                close(-1, false);
                            }

                            throw toJMSException("Error registering consumer: " + e,e);
                        }
                        catch (TransportException e)
                        {
                            throw toJMSException("Exception while registering consumer:" + e.getMessage(), e);
                        }
                        return consumer;
                    }
                }, _connection).execute();
    }

    public abstract C createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
                                                               final int prefetchLow, final boolean noLocal, final boolean exclusive, String selector, final FieldTable arguments,
                                                               final boolean noConsume, final boolean autoClose) throws JMSException;

    /**
     * Called by the MessageConsumer when closing, to deregister the consumer from the map from consumerTag to consumer
     * instance.
     *
     * @param consumer the consum
     */
    void deregisterConsumer(C consumer)
    {
        if (_consumers.remove(consumer.getConsumerTag()) != null)
        {
            _subscriberAccess.lock();
            try
            {
                String subscriptionName = _reverseSubscriptionMap.remove(consumer);
                if (subscriptionName != null)
                {
                    _subscriptions.remove(subscriptionName);
                }
            }
            finally
            {
                _subscriberAccess.unlock();
            }

            Destination dest = consumer.getDestination();
            synchronized (dest)
            {
                // Provide additional NPE check
                // This would occur if the consumer was closed before it was
                // fully opened.
                if (_destinationConsumerCount.get(dest) != null)
                {
                    if (_destinationConsumerCount.get(dest).decrementAndGet() == 0)
                    {
                        _destinationConsumerCount.remove(dest);
                    }
                }
            }

            // Consumers that are closed in a transaction must be stored
            // so that messages they have received can be acknowledged on commit
            if (_transacted)
            {
                _removedConsumers.add(consumer);
            }
        }
    }

    void deregisterProducer(long producerId)
    {
        _producers.remove(new Long(producerId));
    }

    boolean isInRecovery()
    {
        return _sessionInRecovery;
    }

    boolean isQueueBound(AMQShortString exchangeName, AMQShortString queueName) throws JMSException
    {
        return isQueueBound(exchangeName, queueName, null);
    }

    /**
     * Tests whether or not the specified queue is bound to the specified exchange under a particular routing key.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param exchangeName The exchange name to test for binding against.
     * @param queueName    The queue name to check if bound.
     * @param routingKey   The routing key to check if the queue is bound under.
     *
     * @return <tt>true</tt> if the queue is bound to the exchange and routing key, <tt>false</tt> if not.
     *
     * @throws JMSException If the query fails for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    public abstract boolean isQueueBound(final AMQShortString exchangeName, final AMQShortString queueName, final AMQShortString routingKey)
            throws JMSException;

    public abstract boolean isQueueBound(final AMQDestination destination) throws JMSException;
    
    public abstract boolean isQueueBound(String exchangeName, String queueName, String bindingKey, Map<String,Object> args) throws JMSException;

    /**
     * Called to mark the session as being closed. Useful when the session needs to be made invalid, e.g. after failover
     * when the client has veoted resubscription. <p/> The caller of this method must already hold the failover mutex.
     */
    void markClosed()
    {
        setClosed();
        _connection.deregisterSession(_channelId);
        markClosedProducersAndConsumers();

    }

    void failoverPrep()
    {
        syncDispatchQueue();
    }

    void syncDispatchQueue()
    {
        if (Thread.currentThread() == _dispatcherThread)
        {
            while (!super.isClosed() && !_queue.isEmpty())
            {
                Dispatchable disp;
                try
                {
                    disp = (Dispatchable) _queue.take();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }

                // Check just in case _queue becomes empty, it shouldn't but
                // better than an NPE.
                if (disp == null)
                {
                    _logger.debug("_queue became empty during sync.");
                    break;
                }

                disp.dispatch(AMQSession.this);
            }
        }
        else
        {
            startDispatcherIfNecessary();

            final CountDownLatch signal = new CountDownLatch(1);

            _queue.add(new Dispatchable()
            {
                public void dispatch(AMQSession ssn)
                {
                    signal.countDown();
                }
            });

            try
            {
                signal.await();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    void drainDispatchQueue()
    {
        if (Thread.currentThread() == _dispatcherThread)
        {
            while (!super.isClosed() && !_queue.isEmpty())
            {
                Dispatchable disp;
                try
                {
                    disp = (Dispatchable) _queue.take();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }

                // Check just in case _queue becomes empty, it shouldn't but
                // better than an NPE.
                if (disp == null)
                {
                    _logger.debug("_queue became empty during sync.");
                    break;
                }

                disp.dispatch(AMQSession.this);
            }
        }
        else
        {
            startDispatcherIfNecessary(false);

            final CountDownLatch signal = new CountDownLatch(1);

            _queue.add(new Dispatchable()
            {
                public void dispatch(AMQSession ssn)
                {
                    signal.countDown();
                }
            });

            try
            {
                signal.await();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Resubscribes all producers and consumers. This is called when performing failover.
     *
     * @throws AMQException
     */
    void resubscribe() throws AMQException
    {
        if (_dirty)
        {
            _failedOverDirty = true;
        }

        _rollbackMark.set(-1);
        resubscribeProducers();
        resubscribeConsumers();
    }

    void setHasMessageListeners()
    {
        _hasMessageListeners = true;
    }

    void setInRecovery(boolean inRecovery)
    {
        _sessionInRecovery = inRecovery;
    }

    boolean isStarted()
    {
        return _startedAtLeastOnce.get();
    }

    /**
     * Starts the session, which ensures that it is not suspended and that its event dispatcher is running.
     *
     * @throws AMQException If the session cannot be started for any reason.
     * @todo This should be controlled by _stopped as it pairs with the stop method fixme or check the
     * FlowControlledBlockingQueue _queue to see if we have flow controlled. will result in sending Flow messages
     * for each subsequent call to flow.. only need to do this if we have called stop.
     */
    void start() throws AMQException
    {
        // Check if the session has previously been started and suspended, in which case it must be unsuspended.
        if (_startedAtLeastOnce.getAndSet(true))
        {
            suspendChannel(false);
        }

        // If the event dispatcher is not running then start it too.
        if (hasMessageListeners())
        {
            startDispatcherIfNecessary();
        }
    }

    void startDispatcherIfNecessary()
    {
        //If we are the dispatcher then we don't need to check we are started
        if (Thread.currentThread() == _dispatcherThread)
        {
            return;
        }

        // If IMMEDIATE_PREFETCH is not set then we need to start fetching
        // This is final per session so will be multi-thread safe.
        if (!_immediatePrefetch)
        {
            // We do this now if this is the first call on a started connection
            if (isSuspended() && _startedAtLeastOnce.get() && _firstDispatcher.getAndSet(false))
            {
                try
                {
                    suspendChannel(false);
                }
                catch (AMQException e)
                {
                    _logger.info("Unsuspending channel threw an exception:", e);
                }
            }
        }

        startDispatcherIfNecessary(false);
    }

    synchronized void startDispatcherIfNecessary(boolean initiallyStopped)
    {
        if (_dispatcher == null)
        {
            _dispatcher = new Dispatcher();
            try
            {
                _dispatcherThread = Threading.getThreadFactory().createThread(_dispatcher);

            }
            catch(Exception e)
            {
                throw new Error("Error creating Dispatcher thread",e);
            }

            String dispatcherThreadName = "Dispatcher-" + _channelId + "-Conn-" + _connection.getConnectionNumber();

            _dispatcherThread.setName(dispatcherThreadName);
            _dispatcherThread.setDaemon(DEAMON_DISPATCHER_THREAD);
            _dispatcher.setConnectionStopped(initiallyStopped);
            _dispatcherThread.start();
            if (_dispatcherLogger.isDebugEnabled())
            {
                _dispatcherLogger.debug(_dispatcherThread.getName() + " created");
            }
        }
        else
        {
            _dispatcher.setConnectionStopped(initiallyStopped);
        }
    }

    void stop() throws AMQException
    {
        // Stop the server delivering messages to this session.
        suspendChannel(true);

        if (_dispatcher != null)
        {
            _dispatcher.setConnectionStopped(true);
        }
    }

    private void checkNotTransacted() throws JMSException
    {
        if (getTransacted())
        {
            throw new IllegalStateException("Session is transacted");
        }
    }

    private void checkTemporaryDestination(Destination destination) throws JMSException
    {
        if ((destination instanceof TemporaryDestination))
        {
            _logger.debug("destination is temporary");
            final TemporaryDestination tempDest = (TemporaryDestination) destination;
            if (tempDest.getSession() != this)
            {
                _logger.debug("destination is on different session");
                throw new JMSException("Cannot consume from a temporary destination created on another session");
            }

            if (tempDest.isDeleted())
            {
                _logger.debug("destination is deleted");
                throw new JMSException("Cannot consume from a deleted destination");
            }
        }
    }

    protected void checkTransacted() throws JMSException
    {
        if (!getTransacted())
        {
            throw new IllegalStateException("Session is not transacted");
        }
    }

    private void checkValidDestination(Destination destination) throws InvalidDestinationException
    {
        if (destination == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }

    private void checkValidQueue(Queue queue) throws InvalidDestinationException
    {
        if (queue == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }

    /*
     * I could have combined the last 3 methods, but this way it improves readability
     */
    protected Topic checkValidTopic(Topic topic, boolean durable) throws JMSException
    {
        if (topic == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Topic");
        }

        if ((topic instanceof TemporaryDestination) && (((TemporaryDestination) topic).getSession() != this))
        {
            throw new javax.jms.InvalidDestinationException(
                    "Cannot create a subscription on a temporary topic created in another session");
        }

        if ((topic instanceof TemporaryDestination) && durable)
        {
            throw new javax.jms.InvalidDestinationException
                ("Cannot create a durable subscription with a temporary topic: " + topic);
        }

        if (!(topic instanceof AMQDestination && topic instanceof javax.jms.Topic))
        {
            throw new javax.jms.InvalidDestinationException(
                    "Cannot create a subscription on topic created for another JMS Provider, class of topic provided is: "
                    + topic.getClass().getName());
        }

        return topic;
    }

    protected Topic checkValidTopic(Topic topic) throws JMSException
    {
        return checkValidTopic(topic, false);
    }

    /**
     * Called to close message consumers cleanly. This may or may <b>not</b> be as a result of an error.
     *
     * @param error not null if this is a result of an error occurring at the connection level
     */
    private void closeConsumers(Throwable error) throws JMSException
    {
        // we need to clone the list of consumers since the close() method updates the _consumers collection
        // which would result in a concurrent modification exception
        final ArrayList<C> clonedConsumers = new ArrayList<C>(_consumers.values());

        final Iterator<C> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final C con = it.next();
            if (error != null)
            {
                con.notifyError(error);
            }
            else
            {
                con.close(false);
            }
        }
        // at this point the _consumers map will be empty
        if (_dispatcher != null)
        {
            _dispatcher.close();
            _dispatcher = null;
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
            final P prod = (P) it.next();
            prod.close();
        }
        // at this point the _producers map is empty
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

        if (jmse != null)
        {
            throw jmse;
        }
    }

    /**
     * Register to consume from the queue.
     * @param queueName
     */
    private void consumeFromQueue(C consumer, AMQShortString queueName, boolean nowait) throws AMQException, FailoverException
    {
        int tagId = _nextTag++;

        consumer.setConsumerTag(tagId);
        // we must register the consumer in the map before we actually start listening
        _consumers.put(tagId, consumer);

        synchronized (consumer.getDestination())
        {
            _destinationConsumerCount.putIfAbsent(consumer.getDestination(), new AtomicInteger());
            _destinationConsumerCount.get(consumer.getDestination()).incrementAndGet();
        }


        try
        {
            sendConsume(consumer, queueName, nowait, tagId);
        }
        catch (AMQException e)
        {
            // clean-up the map in the event of an error
            _consumers.remove(tagId);
            throw e;
        }
    }

    public abstract void sendConsume(C consumer, AMQShortString queueName,
                                     boolean nowait, int tag) throws AMQException, FailoverException;

    private P createProducerImpl(final Destination destination, final Boolean mandatory, final Boolean immediate)
            throws JMSException
    {
        return new FailoverRetrySupport<P, JMSException>(
                new FailoverProtectedOperation<P, JMSException>()
                {
                    public P execute() throws JMSException, FailoverException
                    {
                        checkNotClosed();
                        long producerId = getNextProducerId();

                        P producer;
                        try
                        {
                            producer = createMessageProducer(destination, mandatory,
                                    immediate, producerId);
                        }
                        catch (TransportException e)
                        {
                            throw toJMSException("Exception while creating producer:" + e.getMessage(), e);
                        }

                        registerProducer(producerId, producer);

                        return producer;
                    }
                }, _connection).execute();
    }

    public abstract P createMessageProducer(final Destination destination, final Boolean mandatory,
                                            final Boolean immediate, final long producerId) throws JMSException;

    private void declareExchange(AMQDestination amqd, boolean nowait) throws AMQException
    {
        declareExchange(amqd.getExchangeName(), amqd.getExchangeClass(), nowait, amqd.isExchangeDurable(),
                        amqd.isExchangeAutoDelete(), amqd.isExchangeInternal());
    }

    /**
     * Returns the number of messages currently queued for the given destination.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param amqd The destination to be checked
     *
     * @return the number of queued messages.
     *
     * @throws AMQException If the queue cannot be declared for any reason.
     */
    public long getQueueDepth(final AMQDestination amqd)
            throws AMQException
    {
        return getQueueDepth(amqd, false);
    }

    /**
     * Returns the number of messages currently queued by the given
     * destination. Syncs session before receiving the queue depth if sync is
     * set to true.
     *
     * @param amqd AMQ destination to get the depth value
     * @param sync flag to sync session before receiving the queue depth
     * @return queue depth
     * @throws AMQException
     */
    public long getQueueDepth(final AMQDestination amqd, final boolean sync) throws AMQException
    {
        return new FailoverNoopSupport<Long, AMQException>(new FailoverProtectedOperation<Long, AMQException>()
        {
            public Long execute() throws AMQException, FailoverException
            {
                try
                {
                    return requestQueueDepth(amqd, sync);
                }
                catch (TransportException e)
                {
                    throw new AMQException(AMQConstant.getConstant(getErrorCode(e)), e.getMessage(), e);
                }
            }
        }, _connection).execute();
    }

    protected abstract Long requestQueueDepth(AMQDestination amqd, boolean sync) throws AMQException, FailoverException;

    /**
     * Declares the named exchange and type of exchange.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param name            The name of the exchange to declare.
     * @param type            The type of the exchange to declare.
     * @param nowait
     * @param durable
     * @param autoDelete
     * @param internal
     * @throws AMQException If the exchange cannot be declared for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    private void declareExchange(final AMQShortString name, final AMQShortString type,
                                 final boolean nowait, final boolean durable,
                                 final boolean autoDelete, final boolean internal) throws AMQException
    {
        new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
        {
            public Object execute() throws AMQException, FailoverException
            {
                sendExchangeDeclare(name, type, nowait, durable, autoDelete, internal);
                return null;
            }
        }, _connection).execute();
    }

    public abstract void sendExchangeDeclare(final AMQShortString name, final AMQShortString type, final boolean nowait,
                                             boolean durable, boolean autoDelete, boolean internal) throws AMQException, FailoverException;

    /**
     * Declares a queue for a JMS destination.
     *
     * <p/>Note that for queues but not topics the name is generated in the client rather than the server. This allows
     * the name to be reused on failover if required. In general, the destination indicates whether it wants a name
     * generated or not.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     *
     * @param amqd            The destination to declare as a queue.
     * @return The name of the decalred queue. This is useful where the broker is generating a queue name on behalf of
     *         the client.
     *
     *
     *
     * @throws AMQException If the queue cannot be declared for any reason.
     * @todo Verify the destiation is valid or throw an exception.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    protected AMQShortString declareQueue(final AMQDestination amqd,
                                          final boolean noLocal) throws AMQException
    {
        return declareQueue(amqd, noLocal, false);
    }

    protected AMQShortString declareQueue(final AMQDestination amqd,
                                          final boolean noLocal, final boolean nowait)
                throws AMQException
    {
        return declareQueue(amqd, noLocal, nowait, false);
    }

    protected abstract AMQShortString declareQueue(final AMQDestination amqd,
                                          final boolean noLocal, final boolean nowait, final boolean passive) throws AMQException;

    /**
     * Undeclares the specified queue.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param queueName The name of the queue to delete.
     *
     * @throws JMSException If the queue could not be deleted for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    protected void deleteQueue(final AMQShortString queueName) throws JMSException
    {
        try
        {
            new FailoverRetrySupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
            {
                public Object execute() throws AMQException, FailoverException
                {
                    sendQueueDelete(queueName);
                    return null;
                }
            }, _connection).execute();
        }
        catch (AMQException e)
        {
            throw toJMSException("The queue deletion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Undeclares the specified temporary queue/topic.
     *
     * <p/>Note that this operation automatically retries in the event of fail-over.
     *
     * @param amqQueue The name of the temporary destination to delete.
     *
     * @throws JMSException If the queue could not be deleted for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    protected void deleteTemporaryDestination(final TemporaryDestination amqQueue) throws JMSException
    {
        deleteQueue(amqQueue.getAMQQueueName());
    }

    public abstract void sendQueueDelete(final AMQShortString queueName) throws AMQException, FailoverException;

    private long getNextProducerId()
    {
        return ++_nextProducerId;
    }

    protected boolean hasMessageListeners()
    {
        return _hasMessageListeners;
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
        final ArrayList<C> clonedConsumers = new ArrayList<C>(_consumers.values());

        final Iterator<C> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final C con = it.next();
            con.markClosed();
        }
        // at this point the _consumers map will be empty
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
     * Callers must hold the failover mutex before calling this method.
     *
     * @param consumer
     *
     * @throws AMQException
     */
    private void registerConsumer(C consumer, boolean nowait) throws AMQException // , FailoverException
    {
        AMQDestination amqd = consumer.getDestination();

        if (amqd.getDestSyntax() == DestSyntax.ADDR)
        {
            resolveAddress(amqd,true,consumer.isNoLocal());
        }
        else
        {
            if (_declareExchanges)
            {
                declareExchange(amqd, nowait);
            }
    
            if (_delareQueues || amqd.isNameRequired())
            {
                declareQueue(amqd, consumer.isNoLocal(), nowait);
            }
            bindQueue(amqd.getAMQQueueName(), amqd.getRoutingKey(), consumer.getArguments(), amqd.getExchangeName(), amqd, nowait);
        }
        
        AMQShortString queueName = amqd.getAMQQueueName();

        // store the consumer queue name
        consumer.setQueuename(queueName);

        // If IMMEDIATE_PREFETCH is not required then suspsend the channel to delay prefetch
        if (!_immediatePrefetch)
        {
            // The dispatcher will be null if we have just created this session
            // so suspend the channel before we register our consumer so that we don't
            // start prefetching until a receive/mListener is set.
            if (_dispatcher == null)
            {
                if (!isSuspended())
                {
                    try
                    {
                        suspendChannel(true);
                        _logger.debug(
                                "Prefetching delayed existing messages will not flow until requested via receive*() or setML().");
                    }
                    catch (AMQException e)
                    {
                        _logger.info("Suspending channel threw an exception:", e);
                    }
                }
            }
        }
        else
        {
            _logger.debug("Immediately prefetching existing messages to new consumer.");
        }

        try
        {
            consumeFromQueue(consumer, queueName, nowait);
        }
        catch (FailoverException e)
        {
            throw new AMQException(null, "Fail-over exception interrupted basic consume.", e);
        }
    }

    public abstract void resolveAddress(AMQDestination dest,
                                                       boolean isConsumer,
                                                       boolean noLocal) throws AMQException;
    
    private void registerProducer(long producerId, MessageProducer producer)
    {
        _producers.put(new Long(producerId), producer);
    }

    /**
     * @param consumerTag The consumerTag to prune from queue or all if null
     * @param requeue     Should the removed messages be requeued (or discarded. Possibly to DLQ)
     * @param rejectAllConsumers
     */

    private void rejectMessagesForConsumerTag(int consumerTag, boolean requeue, boolean rejectAllConsumers)
    {
        Iterator messages = _queue.iterator();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting messages from _queue for Consumer tag(" + consumerTag + ") (PDispatchQ) requeue:"
                         + requeue);

            if (messages.hasNext())
            {
                _logger.debug("Checking all messages in _queue for Consumer tag(" + consumerTag + ")");
            }
            else
            {
                _logger.debug("No messages in _queue to reject");
            }
        }
        while (messages.hasNext())
        {
            UnprocessedMessage message = (UnprocessedMessage) messages.next();

            if (rejectAllConsumers || (message.getConsumerTag() == consumerTag))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Removing message(" + System.identityHashCode(message) + ") from _queue DT:"
                                  + message.getDeliveryTag());
                }

                messages.remove();

                rejectMessage(message, requeue);

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Rejected the message(" + message.toString() + ") for consumer :" + consumerTag);
                }
            }
        }
    }

    private void resubscribeConsumers() throws AMQException
    {
        ArrayList<C> consumers = new ArrayList<C>(_consumers.values());
        _consumers.clear();

        for (C consumer : consumers)
        {
            consumer.failedOverPre();
            registerConsumer(consumer, true);
            consumer.failedOverPost();
        }
    }

    private void resubscribeProducers() throws AMQException
    {
        ArrayList producers = new ArrayList(_producers.values());
        _logger.debug(MessageFormat.format("Resubscribing producers = {0} producers.size={1}", producers, producers.size())); // FIXME: removeKey
        for (Iterator it = producers.iterator(); it.hasNext();)
        {
            P producer = (P) it.next();
            producer.resubscribe();
        }
    }

    /**
     * Suspends or unsuspends this session.
     *
     * @param suspend <tt>true</tt> indicates that the session should be suspended, <tt>false<tt> indicates that it
     *                should be unsuspended.
     *
     * @throws AMQException If the session cannot be suspended for any reason.
     * @todo Be aware of possible changes to parameter order as versions change.
     */
    protected void suspendChannel(boolean suspend) throws AMQException // , FailoverException
    {
        synchronized (_suspensionLock)
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Setting channel flow : " + (suspend ? "suspended" : "unsuspended"));
                }

                _suspended = suspend;
                sendSuspendChannel(suspend);
            }
            catch (FailoverException e)
            {
                throw new AMQException(null, "Fail-over interrupted suspend/unsuspend channel.", e);
            }
            catch (TransportException e)
            {
                throw new AMQException(AMQConstant.getConstant(getErrorCode(e)), e.getMessage(), e);
            }
        }
    }

    public abstract void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException;

    Object getMessageDeliveryLock()
    {
        return _messageDeliveryLock;
    }

    /**
     * Indicates whether this session consumers pre-fetche messages
     *
     * @return true if this session consumers pre-fetche messages false otherwise
     */
    public boolean prefetch()
    {
        return _prefetchHighMark > 0;
    }

    /** Signifies that the session has pending sends to commit. */
    public void markDirty()
    {
        _dirty = true;
    }

    /** Signifies that the session has no pending sends to commit. */
    public void markClean()
    {
        _dirty = false;
        _failedOverDirty = false;
    }

    /**
     * Check to see if failover has occured since the last call to markClean(commit or rollback).
     *
     * @return boolean true if failover has occured.
     */
    public boolean hasFailedOverDirty()
    {
        return _failedOverDirty;
    }

    public void setTicket(int ticket)
    {
        _ticket = ticket;
    }

    /**
     * Tests whether flow to this session is blocked.
     *
     * @return true if flow is blocked or false otherwise.
     */
    public abstract boolean isFlowBlocked();

    public abstract void setFlowControl(final boolean active);

    public interface Dispatchable
    {
        void dispatch(AMQSession ssn);
    }

    public void dispatch(UnprocessedMessage message)
    {
        if (_dispatcher == null)
        {
            throw new java.lang.IllegalStateException("dispatcher is not started");
        }

        _dispatcher.dispatchMessage(message);
    }

    /** Used for debugging in the dispatcher. */
    private static final Logger _dispatcherLogger = LoggerFactory.getLogger("org.apache.qpid.client.AMQSession.Dispatcher");

    /** Responsible for decoding a message fragment and passing it to the appropriate message consumer. */
    class Dispatcher implements Runnable
    {

        /** Track the 'stopped' state of the dispatcher, a session starts in the stopped state. */
        private final AtomicBoolean _closed = new AtomicBoolean(false);

        private final Object _lock = new Object();
        private final String dispatcherID = "" + System.identityHashCode(this);

        public Dispatcher()
        {
        }

        public void close()
        {
            _closed.set(true);
            _dispatcherThread.interrupt();

            // fixme awaitTermination

        }

        private AtomicBoolean getClosed()
        {
            return _closed;
        }

        public void rejectPending(C consumer)
        {
            // Reject messages on pre-receive queue
            consumer.rollbackPendingMessages();

            // Reject messages on pre-dispatch queue
            rejectMessagesForConsumerTag(consumer.getConsumerTag(), true, false);

            // closeConsumer
            consumer.markClosed();

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

                setRollbackMark();

                _dispatcherLogger.debug("Session Pre Dispatch Queue cleared");

                for (C consumer : _consumers.values())
                {
                    if (!consumer.isBrowseOnly())
                    {
                        consumer.rollback();
                    }
                    else
                    {
                        // should perhaps clear the _SQ here.
                        consumer.clearReceiveQueue();
                    }

                }

                for (int i = 0; i < _removedConsumers.size(); i++)
                {
                    // Sends acknowledgement to server
                    _removedConsumers.get(i).rollback();
                    _removedConsumers.remove(i);
                }

                setConnectionStopped(isStopped);
            }

        }

        public void recover()
        {

            synchronized (_lock)
            {
                boolean isStopped = connectionStopped();

                if (!isStopped)
                {
                    setConnectionStopped(true);
                }

                _dispatcherLogger.debug("Session clearing the consumer queues");

                for (C consumer : _consumers.values())
                {
                    List<Long> tags = consumer.drainReceiverQueueAndRetrieveDeliveryTags();
                    _prefetchedMessageTags.addAll(tags);
                }

                setConnectionStopped(isStopped);
            }

        }

        
        public void run()
        {
            if (_dispatcherLogger.isDebugEnabled())
            {
                _dispatcherLogger.debug(_dispatcherThread.getName() + " started");
            }

            // Allow disptacher to start stopped
            synchronized (_lock)
            {
                while (!_closed.get() && connectionStopped())
                {
                    try
                    {
                        _lock.wait();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            try
            {
                Dispatchable disp;
                while (((disp = (Dispatchable) _queue.take()) != null) && !_closed.get())
                {
                    disp.dispatch(AMQSession.this);
                }
            }
            catch (InterruptedException e)
            {
                // ignored as run will exit immediately
            }

            if (_dispatcherLogger.isDebugEnabled())
            {
                _dispatcherLogger.debug(_dispatcherThread.getName() + " thread terminating for channel " + _channelId + ":" + AMQSession.this);
            }

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

                if (_dispatcherLogger.isDebugEnabled())
                {
                    _dispatcherLogger.debug("Set Dispatcher Connection " + (connectionStopped ? "Stopped" : "Started")
                                            + ": Currently " + (currently ? "Stopped" : "Started"));
                }
            }

            return currently;
        }

        private void dispatchMessage(UnprocessedMessage message)
        {
            long deliveryTag = message.getDeliveryTag();

            synchronized (_lock)
            {

                try
                {
                    while (connectionStopped())
                    {
                        _lock.wait();
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }

                if (!(message instanceof CloseConsumerMessage)
                    && tagLE(deliveryTag, _rollbackMark.get()))
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Rejecting message because delivery tag " + deliveryTag
                                + " <= rollback mark " + _rollbackMark.get());
                    }
                    rejectMessage(message, true);
                }
                else if (_usingDispatcherForCleanup)
                {
                    _prefetchedMessageTags.add(deliveryTag);
                }
                else
                {
                    synchronized (_messageDeliveryLock)
                    {
                        notifyConsumer(message);
                    }
                }
            }

            long current = _rollbackMark.get();
            if (updateRollbackMark(current, deliveryTag))
            {
                _rollbackMark.compareAndSet(current, deliveryTag);
            }            
        }

        private void notifyConsumer(UnprocessedMessage message)
        {
            final C consumer = _consumers.get(message.getConsumerTag());

            if ((consumer == null) || consumer.isClosed() || consumer.isClosing())
            {
                if (_dispatcherLogger.isInfoEnabled())
                {
                    if (consumer == null)
                    {
                        _dispatcherLogger.info("Dispatcher(" + dispatcherID + ")Received a message("
                                               + System.identityHashCode(message) + ")" + "["
                                               + message.getDeliveryTag() + "] from queue "
                                               + message.getConsumerTag() + " )without a handler - rejecting(requeue)...");
                    }
                    else
                    {
                        if (consumer.isBrowseOnly())
                        {
                            _dispatcherLogger.info("Received a message("
                                                   + System.identityHashCode(message) + ")" + "["
                                                   + message.getDeliveryTag() + "] from queue " + " consumer("
                                                   + message.getConsumerTag() + ") is closed and a browser so dropping...");
                            //DROP MESSAGE
                            return;

                        }
                        else
                        {
                            _dispatcherLogger.info("Received a message("
                                                   + System.identityHashCode(message) + ")" + "["
                                                   + message.getDeliveryTag() + "] from queue " + " consumer("
                                                   + message.getConsumerTag() + ") is closed rejecting(requeue)...");
                        }
                    }
                }
                // Don't reject if we're already closing
                if (!_closed.get())
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Rejecting message with delivery tag " + message.getDeliveryTag()
                                + " for closing consumer " + String.valueOf(consumer == null? null: consumer.getConsumerTag()));
                    }
                    rejectMessage(message, true);
                }
            }
            else
            {
                consumer.notifyMessage(message);
            }
        }
    }

    protected abstract boolean tagLE(long tag1, long tag2);

    protected abstract boolean updateRollbackMark(long current, long deliveryTag);

    public abstract AMQMessageDelegateFactory getMessageDelegateFactory();

    private class SuspenderRunner implements Runnable
    {
        private AtomicBoolean _suspend;

        public SuspenderRunner(AtomicBoolean suspend)
        {
            _suspend = suspend;
        }

        public void run()
        {
            try
            {
                synchronized (_suspensionLock)
                {
                    // If the session has closed by the time we get here
                    // then we should not attempt to write to the sesion/channel.
                    if (!(AMQSession.this.isClosed() || AMQSession.this.isClosing()))
                    {
                        suspendChannel(_suspend.get());
                    }
                }
            }
            catch (AMQException e)
            {
                _logger.warn("Unable to " + (_suspend.get() ? "suspend" : "unsuspend") + " session " + AMQSession.this + " due to: ", e);
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Is the _queue empty?" + _queue.isEmpty());
                    _logger.debug("Is the dispatcher closed?" + (_dispatcher == null ? "it's Null" : _dispatcher.getClosed()));
                }
            }
        }
    }

    /**
     * Checks if the Session and its parent connection are closed
     *
     * @return <tt>true</tt> if this is closed, <tt>false</tt> otherwise.
     */
    @Override
    public boolean isClosed()
    {
        return super.isClosed() || _connection.isClosed();
    }

    /**
     * Checks if the Session and its parent connection are capable of performing
     * closing operations
     *
     * @return <tt>true</tt> if we are closing, <tt>false</tt> otherwise.
     */
    @Override
    public boolean isClosing()
    {
        return super.isClosing() || _connection.isClosing();
    }
    
    public boolean isDeclareExchanges()
    {
    	return _declareExchanges;
    }

    JMSException toJMSException(String message, TransportException e)
    {
        int code = getErrorCode(e);
        JMSException jmse = new JMSException(message, Integer.toString(code));
        jmse.setLinkedException(e);
        jmse.initCause(e);
        return jmse;
    }

    private int getErrorCode(TransportException e)
    {
        int code = AMQConstant.INTERNAL_ERROR.getCode();
        if (e instanceof SessionException)
        {
            SessionException se = (SessionException) e;
            if(se.getException() != null && se.getException().getErrorCode() != null)
            {
                code = se.getException().getErrorCode().getValue();
            }
        }
        return code;
    }

    JMSException toJMSException(String message, AMQException e)
    {
        JMSException ex;
        if (e.getErrorCode() == AMQConstant.ACCESS_REFUSED)
        {
            ex = new JMSSecurityException(message);
        }
        else
        {
            ex = new JMSException(message);
        }
        ex.initCause(e);
        ex.setLinkedException(e);
        return ex;
    }

    private boolean isBrowseOnlyDestination(Destination destination)
    {
        return ((destination instanceof AMQDestination)  && ((AMQDestination)destination).isBrowseOnly());
    }

    private void setRollbackMark()
    {
        // Let the dispatcher know that all the incomming messages
        // should be rolled back(reject/release)
        _rollbackMark.set(_highestDeliveryTag.get());
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rollback mark is set to " + _rollbackMark.get());
        }
    }

}

