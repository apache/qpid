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
package org.apache.qpid.server.protocol.v0_8;

import static org.apache.qpid.transport.util.Functions.hex;

import java.nio.ByteBuffer;
import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.TransactionTimeoutHelper;
import org.apache.qpid.server.TransactionTimeoutHelper.CloseAction;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.filter.ArrivalTimeFilter;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.flow.MessageOnlyCreditManager;
import org.apache.qpid.server.flow.Pre0_10CreditManager;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.logging.subjects.ChannelLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.NoFactoryForTypeException;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UnknownConfiguredObjectException;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.CapacityChecker;
import org.apache.qpid.server.protocol.ConsumerListener;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.AsyncAutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.LocalTransaction.ActivityTimeAccessor;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.TransportException;

public class AMQChannel
        implements AMQSessionModel<AMQChannel, AMQProtocolEngine>,
                   AsyncAutoCommitTransaction.FutureRecorder,
                   ServerChannelMethodProcessor
{
    public static final int DEFAULT_PREFETCH = 4096;

    private static final Logger _logger = Logger.getLogger(AMQChannel.class);
    private final DefaultQueueAssociationClearingTask
            _defaultQueueAssociationClearingTask = new DefaultQueueAssociationClearingTask();

    //TODO use Broker property to configure message authorization requirements
    private boolean _messageAuthorizationRequired = Boolean.getBoolean(BrokerProperties.PROPERTY_MSG_AUTH);

    private final int _channelId;


    private final Pre0_10CreditManager _creditManager = new Pre0_10CreditManager(0l,0l);

    /**
     * The delivery tag is unique per channel. This is pre-incremented before putting into the deliver frame so that
     * value of this represents the <b>last</b> tag sent out
     */
    private long _deliveryTag = 0;

    /** A channel has a default queue (the last declared) that is used when no queue name is explicitly set */
    private volatile AMQQueue<?> _defaultQueue;

    /** This tag is unique per subscription to a queue. The server returns this in response to a basic.consume request. */
    private int _consumerTag;

    /**
     * The current message - which may be partial in the sense that not all frames have been received yet - which has
     * been received by this channel. As the frames are received the message gets updated and once all frames have been
     * received the message can then be routed.
     */
    private IncomingMessage _currentMessage;

    /** Maps from consumer tag to subscription instance. Allows us to unsubscribe from a queue. */
    private final Map<AMQShortString, ConsumerTarget_0_8> _tag2SubscriptionTargetMap = new HashMap<AMQShortString, ConsumerTarget_0_8>();

    private final MessageStore _messageStore;

    private final LinkedList<AsyncCommand> _unfinishedCommandsQueue = new LinkedList<AsyncCommand>();

    private UnacknowledgedMessageMap _unacknowledgedMessageMap = new UnacknowledgedMessageMapImpl(DEFAULT_PREFETCH);

    private final AtomicBoolean _suspended = new AtomicBoolean(false);

    private ServerTransaction _transaction;

    private final AtomicLong _txnStarts = new AtomicLong(0);
    private final AtomicLong _txnCommits = new AtomicLong(0);
    private final AtomicLong _txnRejects = new AtomicLong(0);
    private final AtomicLong _txnCount = new AtomicLong(0);

    private final AMQProtocolEngine _connection;
    private AtomicBoolean _closing = new AtomicBoolean(false);

    private final Set<Object> _blockingEntities = Collections.synchronizedSet(new HashSet<Object>());

    private final AtomicBoolean _blocking = new AtomicBoolean(false);


    private LogSubject _logSubject;
    private volatile boolean _rollingBack;

    private List<MessageInstance> _resendList = new ArrayList<MessageInstance>();
    private static final
    AMQShortString IMMEDIATE_DELIVERY_REPLY_TEXT = new AMQShortString("Immediate delivery is not possible.");

    private final ClientDeliveryMethod _clientDeliveryMethod;

    private final TransactionTimeoutHelper _transactionTimeoutHelper;
    private final UUID _id = UUID.randomUUID();

    private final List<Action<? super AMQChannel>> _taskList =
            new CopyOnWriteArrayList<Action<? super AMQChannel>>();


    private final CapacityCheckAction _capacityCheckAction = new CapacityCheckAction();
    private final ImmediateAction _immediateAction = new ImmediateAction();
    private Subject _subject;
    private final CopyOnWriteArrayList<Consumer<?>> _consumers = new CopyOnWriteArrayList<Consumer<?>>();
    private final ConfigurationChangeListener _consumerClosedListener = new ConsumerClosedListener();
    private final CopyOnWriteArrayList<ConsumerListener> _consumerListeners = new CopyOnWriteArrayList<ConsumerListener>();
    private Session<?> _modelObject;
    private long _blockTime;
    private long _blockingTimeout;
    private boolean _confirmOnPublish;
    private long _confirmedMessageCounter;
    private volatile long _uncommittedMessageSize;
    private final List<StoredMessage<MessageMetaData>> _uncommittedMessages = new ArrayList<>();
    private long _maxUncommittedInMemorySize;

    public AMQChannel(AMQProtocolEngine connection, int channelId, final MessageStore messageStore)
    {
        _connection = connection;
        _channelId = channelId;

        _subject = new Subject(false, connection.getAuthorizedSubject().getPrincipals(),
                               connection.getAuthorizedSubject().getPublicCredentials(),
                               connection.getAuthorizedSubject().getPrivateCredentials());
        _subject.getPrincipals().add(new SessionPrincipal(this));
        _maxUncommittedInMemorySize = connection.getVirtualHost().getContextValue(Long.class, Connection.MAX_UNCOMMITTED_IN_MEMORY_SIZE);
        _logSubject = new ChannelLogSubject(this);

        _messageStore = messageStore;
        _blockingTimeout = connection.getBroker().getContextValue(Long.class,
                                                                  Broker.CHANNEL_FLOW_CONTROL_ENFORCEMENT_TIMEOUT);
        // by default the session is non-transactional
        _transaction = new AsyncAutoCommitTransaction(_messageStore, this);

        _clientDeliveryMethod = connection.createDeliveryMethod(_channelId);

        _transactionTimeoutHelper = new TransactionTimeoutHelper(_logSubject, new CloseAction()
        {
            @Override
            public void doTimeoutAction(String reason)
            {
                try
                {
                    closeConnection(reason);
                }
                catch (AMQException e)
                {
                    throw new ConnectionScopedRuntimeException(e);
                }
            }
        }, getVirtualHost());

        Subject.doAs(_subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                getVirtualHost().getEventLogger().message(ChannelMessages.CREATE());

                return null;
            }
        });

    }

    private boolean performGet(final AMQQueue queue,
                               final boolean acks)
            throws MessageSource.ExistingConsumerPreventsExclusive,
                   MessageSource.ExistingExclusiveConsumer, MessageSource.ConsumerAccessRefused
    {

        final FlowCreditManager singleMessageCredit = new MessageOnlyCreditManager(1L);

        final GetDeliveryMethod getDeliveryMethod =
                new GetDeliveryMethod(singleMessageCredit, queue);
        final RecordDeliveryMethod getRecordMethod = new RecordDeliveryMethod()
        {

            public void recordMessageDelivery(final ConsumerImpl sub,
                                              final MessageInstance entry,
                                              final long deliveryTag)
            {
                addUnacknowledgedMessage(entry, deliveryTag, null);
            }
        };

        ConsumerTarget_0_8 target;
        EnumSet<ConsumerImpl.Option> options = EnumSet.of(ConsumerImpl.Option.TRANSIENT, ConsumerImpl.Option.ACQUIRES,
                                                          ConsumerImpl.Option.SEES_REQUEUES);
        if (acks)
        {

            target = ConsumerTarget_0_8.createAckTarget(this,
                                                        AMQShortString.EMPTY_STRING, null,
                                                        singleMessageCredit, getDeliveryMethod, getRecordMethod);
        }
        else
        {
            target = ConsumerTarget_0_8.createGetNoAckTarget(this,
                                                             AMQShortString.EMPTY_STRING, null,
                                                             singleMessageCredit, getDeliveryMethod, getRecordMethod);
        }

        ConsumerImpl sub = queue.addConsumer(target, null, AMQMessage.class, "", options);
        sub.flush();
        sub.close();
        return getDeliveryMethod.hasDeliveredMessage();


    }

    /** Sets this channel to be part of a local transaction */
    public void setLocalTransactional()
    {
        _transaction = new LocalTransaction(_messageStore, new ActivityTimeAccessor()
        {
            @Override
            public long getActivityTime()
            {
                return _connection.getLastReceivedTime();
            }
        });
        _txnStarts.incrementAndGet();
    }

    public boolean isTransactional()
    {
        return _transaction.isTransactional();
    }

    public void receivedComplete()
    {
        sync();
    }

    private void incrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 1 if 0.
            _txnCount.compareAndSet(0,1);
        }
    }

    private void decrementOutstandingTxnsIfNecessary()
    {
        if(isTransactional())
        {
            //There can currently only be at most one outstanding transaction
            //due to only having LocalTransaction support. Set value to 0 if 1.
            _txnCount.compareAndSet(1,0);
        }
    }

    public Long getTxnCommits()
    {
        return _txnCommits.get();
    }

    public Long getTxnRejects()
    {
        return _txnRejects.get();
    }

    public Long getTxnCount()
    {
        return _txnCount.get();
    }

    public Long getTxnStart()
    {
        return _txnStarts.get();
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public void setPublishFrame(MessagePublishInfo info, final MessageDestination e)
    {
        String routingKey = info.getRoutingKey() == null ? null : info.getRoutingKey().asString();
        VirtualHostImpl virtualHost = getVirtualHost();
        SecurityManager securityManager = virtualHost.getSecurityManager();

        securityManager.authorisePublish(info.isImmediate(), routingKey, e.getName(), virtualHost.getName());

        _currentMessage = new IncomingMessage(info);
        _currentMessage.setMessageDestination(e);
    }

    public void publishContentHeader(ContentHeaderBody contentHeaderBody)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Content header received on channel " + _channelId);
        }

        _currentMessage.setContentHeaderBody(contentHeaderBody);

        deliverCurrentMessageIfComplete();
    }

    private void deliverCurrentMessageIfComplete()
    {
        // check and deliver if header says body length is zero
        if (_currentMessage.allContentReceived())
        {
            if(_confirmOnPublish)
            {
                _confirmedMessageCounter++;
            }
            Runnable finallyAction = null;
            ContentHeaderBody contentHeader = _currentMessage.getContentHeader();

            long bodySize = _currentMessage.getSize();
            long timestamp = contentHeader.getProperties().getTimestamp();

            try
            {

                final MessagePublishInfo messagePublishInfo = _currentMessage.getMessagePublishInfo();
                final MessageDestination destination = _currentMessage.getDestination();

                final MessageMetaData messageMetaData =
                        new MessageMetaData(messagePublishInfo,
                                            contentHeader,
                                            getConnection().getLastReceivedTime());

                final StoredMessage<MessageMetaData> handle = _messageStore.addMessage(messageMetaData);
                final AMQMessage amqMessage = createAMQMessage(handle);
                MessageReference reference = amqMessage.newReference();
                try
                {
                    int bodyCount = _currentMessage.getBodyCount();
                    if(bodyCount > 0)
                    {
                        long bodyLengthReceived = 0;
                        for(int i = 0 ; i < bodyCount ; i++)
                        {
                            ContentBody contentChunk = _currentMessage.getContentChunk(i);
                            handle.addContent((int)bodyLengthReceived, ByteBuffer.wrap(contentChunk.getPayload()));
                            bodyLengthReceived += contentChunk.getSize();
                        }
                    }

                    _currentMessage = null;

                    if(!checkMessageUserId(contentHeader))
                    {
                        if(_confirmOnPublish)
                        {
                            _connection.writeFrame(new AMQFrame(_channelId, new BasicNackBody(_confirmedMessageCounter, false, false)));
                        }
                        _transaction.addPostTransactionAction(new WriteReturnAction(AMQConstant.ACCESS_REFUSED, "Access Refused", amqMessage));
                    }
                    else
                    {
                        final boolean immediate = messagePublishInfo.isImmediate();

                        final InstanceProperties instanceProperties =
                                new InstanceProperties()
                                {
                                    @Override
                                    public Object getProperty(final Property prop)
                                    {
                                        switch(prop)
                                        {
                                            case EXPIRATION:
                                                return amqMessage.getExpiration();
                                            case IMMEDIATE:
                                                return immediate;
                                            case PERSISTENT:
                                                return amqMessage.isPersistent();
                                            case MANDATORY:
                                                return messagePublishInfo.isMandatory();
                                            case REDELIVERED:
                                                return false;
                                        }
                                        return null;
                                    }
                                };

                        int enqueues = destination.send(amqMessage,
                                                        amqMessage.getInitialRoutingAddress(),
                                                        instanceProperties, _transaction,
                                                        immediate ? _immediateAction : _capacityCheckAction
                                                       );
                        if(enqueues == 0)
                        {
                            finallyAction = handleUnroutableMessage(amqMessage);
                        }
                        else
                        {
                            if(_confirmOnPublish)
                            {
                                BasicAckBody responseBody = _connection.getMethodRegistry()
                                        .createBasicAckBody(_confirmedMessageCounter, false);
                                _connection.writeFrame(responseBody.generateFrame(_channelId));
                            }
                            incrementUncommittedMessageSize(handle);
                            incrementOutstandingTxnsIfNecessary();
                        }
                    }
                }
                finally
                {
                    reference.release();
                    if(finallyAction != null)
                    {
                        finallyAction.run();
                    }
                }

            }
            finally
            {
                _connection.registerMessageReceived(bodySize, timestamp);
                _currentMessage = null;
            }
        }

    }

    private void incrementUncommittedMessageSize(final StoredMessage<MessageMetaData> handle)
    {
        if (isTransactional())
        {
            _uncommittedMessageSize += handle.getMetaData().getContentSize();
            if (_uncommittedMessageSize > getMaxUncommittedInMemorySize())
            {
                handle.flowToDisk();
                if(!_uncommittedMessages.isEmpty() || _uncommittedMessageSize == handle.getMetaData().getContentSize())
                {
                    getVirtualHost().getEventLogger()
                            .message(_logSubject, ChannelMessages.LARGE_TRANSACTION_WARN(_uncommittedMessageSize));
                }

                if(!_uncommittedMessages.isEmpty())
                {
                    for (StoredMessage<MessageMetaData> uncommittedHandle : _uncommittedMessages)
                    {
                        uncommittedHandle.flowToDisk();
                    }
                    _uncommittedMessages.clear();
                }
            }
            else
            {
                _uncommittedMessages.add(handle);
            }
        }
    }

    /**
     * Either throws a {@link AMQConnectionException} or returns the message
     *
     * Pre-requisite: the current message is judged to have no destination queues.
     *
     * @throws AMQConnectionException if the message is mandatory close-on-no-route
     * @see AMQProtocolEngine#isCloseWhenNoRoute()
     */
    private Runnable handleUnroutableMessage(AMQMessage message)
    {
        boolean mandatory = message.isMandatory();

        String exchangeName = message.getMessagePublishInfo().getExchange() == null
                ? null : message.getMessagePublishInfo().getExchange().asString();
        String routingKey = message.getMessagePublishInfo().getRoutingKey() == null
                ? null : message.getMessagePublishInfo().getRoutingKey().asString();

        final String description = String.format(
                "[Exchange: %s, Routing key: %s]",
                exchangeName,
                routingKey);

        boolean closeOnNoRoute = _connection.isCloseWhenNoRoute();
        Runnable returnVal = null;
        if(_logger.isDebugEnabled())
        {
            _logger.debug(String.format(
                    "Unroutable message %s, mandatory=%s, transactionalSession=%s, closeOnNoRoute=%s",
                    description, mandatory, isTransactional(), closeOnNoRoute));
        }

        if (mandatory && isTransactional() && !_confirmOnPublish && _connection.isCloseWhenNoRoute())
        {
            returnVal = new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                _connection.closeConnection(AMQConstant.NO_ROUTE,
                                        "No route for message " + description, _channelId);

                            }
                        };
        }
        else
        {
            if (mandatory || message.isImmediate())
            {
                if(_confirmOnPublish)
                {
                    _connection.writeFrame(new AMQFrame(_channelId, new BasicNackBody(_confirmedMessageCounter, false, false)));
                }
                _transaction.addPostTransactionAction(new WriteReturnAction(AMQConstant.NO_ROUTE,
                                                                            "No Route for message "
                                                                            + description,
                                                                            message));
            }
            else
            {

                getVirtualHost().getEventLogger().message(ExchangeMessages.DISCARDMSG(exchangeName, routingKey));
            }
        }
        return returnVal;
    }

    public void publishContentBody(ContentBody contentBody)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug(debugIdentity() + " content body received on channel " + _channelId);
        }

        try
        {
            long currentSize = _currentMessage.addContentBodyFrame(contentBody);
            if(currentSize > _currentMessage.getSize())
            {
                _connection.closeConnection(AMQConstant.FRAME_ERROR,
                                            "More message data received than content header defined",
                                            _channelId);
            }
            else
            {
                deliverCurrentMessageIfComplete();
            }
        }
        catch (RuntimeException e)
        {
            // we want to make sure we don't keep a reference to the message in the
            // event of an error
            _currentMessage = null;
            throw e;
        }
    }

    public long getNextDeliveryTag()
    {
        return ++_deliveryTag;
    }

    public int getNextConsumerTag()
    {
        return ++_consumerTag;
    }


    public ConsumerTarget getSubscription(AMQShortString tag)
    {
        return _tag2SubscriptionTargetMap.get(tag);
    }

    /**
     * Subscribe to a queue. We register all subscriptions in the channel so that if the channel is closed we can clean
     * up all subscriptions, even if the client does not explicitly unsubscribe from all queues.
     *
     *
     * @param tag       the tag chosen by the client (if null, server will generate one)
     * @param sources     the queues to subscribe to
     * @param acks      Are acks enabled for this subscriber
     * @param arguments   Filters to apply to this subscriber
     *
     * @param exclusive Flag requesting exclusive access to the queue
     * @return the consumer tag. This is returned to the subscriber and used in subsequent unsubscribe requests
     *
     * @throws org.apache.qpid.AMQException                  if something goes wrong
     */
    public AMQShortString consumeFromSource(AMQShortString tag, Collection<MessageSource> sources, boolean acks,
                                            FieldTable arguments, boolean exclusive, boolean noLocal)
            throws MessageSource.ExistingConsumerPreventsExclusive,
                   MessageSource.ExistingExclusiveConsumer,
                   AMQInvalidArgumentException,
                   MessageSource.ConsumerAccessRefused, ConsumerTagInUseException
    {
        if (tag == null)
        {
            tag = new AMQShortString("sgen_" + getNextConsumerTag());
        }

        if (_tag2SubscriptionTargetMap.containsKey(tag))
        {
            throw new ConsumerTagInUseException("Consumer already exists with same tag: " + tag);
        }

        ConsumerTarget_0_8 target;
        EnumSet<ConsumerImpl.Option> options = EnumSet.noneOf(ConsumerImpl.Option.class);

        if(arguments != null && Boolean.TRUE.equals(arguments.get(AMQPFilterTypes.NO_CONSUME.getValue())))
        {
            target = ConsumerTarget_0_8.createBrowserTarget(this, tag, arguments, _creditManager);
        }
        else if(acks)
        {
            target = ConsumerTarget_0_8.createAckTarget(this, tag, arguments, _creditManager);
            options.add(ConsumerImpl.Option.ACQUIRES);
            options.add(ConsumerImpl.Option.SEES_REQUEUES);
        }
        else
        {
            target = ConsumerTarget_0_8.createNoAckTarget(this, tag, arguments, _creditManager);
            options.add(ConsumerImpl.Option.ACQUIRES);
            options.add(ConsumerImpl.Option.SEES_REQUEUES);
        }

        if(exclusive)
        {
            options.add(ConsumerImpl.Option.EXCLUSIVE);
        }


        // So to keep things straight we put before the call and catch all exceptions from the register and tidy up.
        // We add before we register as the Async Delivery process may AutoClose the subscriber
        // so calling _cT2QM.remove before we have done put which was after the register succeeded.
        // So to keep things straight we put before the call and catch all exceptions from the register and tidy up.

        _tag2SubscriptionTargetMap.put(tag, target);

        try
        {
            FilterManager filterManager = FilterManagerFactory.createManager(FieldTable.convertToMap(arguments));
            if(noLocal)
            {
                if(filterManager == null)
                {
                    filterManager = new FilterManager();
                }
                final Object connectionReference = getConnectionReference();
                MessageFilter filter = new MessageFilter()
                {

                    @Override
                    public String getName()
                    {
                        return AMQPFilterTypes.NO_LOCAL.toString();
                    }

                    @Override
                    public boolean matches(final Filterable message)
                    {
                        return message.getConnectionReference() != connectionReference;
                    }
                };
                filterManager.add(filter.getName(), filter);
            }

            if(arguments != null && arguments.containsKey(AMQPFilterTypes.REPLAY_PERIOD.toString()))
            {
                Object value = arguments.get(AMQPFilterTypes.REPLAY_PERIOD.toString());
                final long period;
                if(value instanceof Number)
                {
                    period = ((Number)value).longValue();
                }
                else if(value instanceof String)
                {
                    try
                    {
                        period = Long.parseLong(value.toString());
                    }
                    catch (NumberFormatException e)
                    {
                        throw new AMQInvalidArgumentException("Cannot parse value " + value + " as a number for filter " + AMQPFilterTypes.REPLAY_PERIOD.toString());
                    }
                }
                else
                {
                    throw new AMQInvalidArgumentException("Cannot parse value " + value + " as a number for filter " + AMQPFilterTypes.REPLAY_PERIOD.toString());
                }

                final long startingFrom = System.currentTimeMillis() - (1000l * period);
                if(filterManager == null)
                {
                    filterManager = new FilterManager();
                }
                MessageFilter filter = new ArrivalTimeFilter(startingFrom);
                filterManager.add(filter.getName(), filter);

            }

            for(MessageSource source : sources)
            {
                ConsumerImpl sub =
                        source.addConsumer(target,
                                           filterManager,
                                           AMQMessage.class,
                                           AMQShortString.toString(tag),
                                           options);
                if (sub instanceof Consumer<?>)
                {
                    final Consumer<?> modelConsumer = (Consumer<?>) sub;
                    consumerAdded(modelConsumer);
                    modelConsumer.addChangeListener(_consumerClosedListener);
                    _consumers.add(modelConsumer);
                }
            }
        }
        catch (AccessControlException
                | MessageSource.ExistingExclusiveConsumer
                | MessageSource.ExistingConsumerPreventsExclusive
                | AMQInvalidArgumentException
                | MessageSource.ConsumerAccessRefused e)
        {
            _tag2SubscriptionTargetMap.remove(tag);
            throw e;
        }
        return tag;
    }

    /**
     * Unsubscribe a consumer from a queue.
     * @param consumerTag
     * @return true if the consumerTag had a mapped queue that could be unregistered.
     */
    public boolean unsubscribeConsumer(AMQShortString consumerTag)
    {

        ConsumerTarget_0_8 target = _tag2SubscriptionTargetMap.remove(consumerTag);
        Collection<ConsumerImpl> subs = target == null ? null : target.getConsumers();
        if (subs != null)
        {
            for(ConsumerImpl sub : subs)
            {
                sub.close();
                if (sub instanceof Consumer<?>)
                {
                    _consumers.remove(sub);
                }
            }
            return true;
        }
        else
        {
            _logger.warn("Attempt to unsubscribe consumer with tag '" + consumerTag + "' which is not registered.");
        }
        return false;
    }

    /**
     * Called from the protocol session to close this channel and clean up. T
     */
    @Override
    public void close()
    {
        close(null, null);
    }

    public void close(AMQConstant cause, String message)
    {
        if(!_closing.compareAndSet(false, true))
        {
            //Channel is already closing
            return;
        }

        LogMessage operationalLogMessage = cause == null ?
                ChannelMessages.CLOSE() :
                ChannelMessages.CLOSE_FORCED(cause.getCode(), message);
        getVirtualHost().getEventLogger().message(_logSubject, operationalLogMessage);

        unsubscribeAllConsumers();
        setDefaultQueue(null);
        for (Action<? super AMQChannel> task : _taskList)
        {
            task.performAction(this);
        }


        _transaction.rollback();

        try
        {
            requeue();
        }
        catch (TransportException e)
        {
            _logger.error("Caught TransportException whilst attempting to requeue:" + e);
        }
    }

    private void unsubscribeAllConsumers()
    {
        if (_logger.isInfoEnabled())
        {
            if (!_tag2SubscriptionTargetMap.isEmpty())
            {
                _logger.info("Unsubscribing all consumers on channel " + toString());
            }
            else
            {
                _logger.info("No consumers to unsubscribe on channel " + toString());
            }
        }

        for (Map.Entry<AMQShortString, ConsumerTarget_0_8> me : _tag2SubscriptionTargetMap.entrySet())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Unsubscribing consumer '" + me.getKey() + "' on channel " + toString());
            }

            Collection<ConsumerImpl> subs = me.getValue().getConsumers();

            if(subs != null)
            {
                for(ConsumerImpl sub : subs)
                {
                    sub.close();
                }
            }
        }

        _tag2SubscriptionTargetMap.clear();
    }

    /**
     * Add a message to the channel-based list of unacknowledged messages
     *
     * @param entry       the record of the message on the queue that was delivered
     * @param deliveryTag the delivery tag used when delivering the message (see protocol spec for description of the
     *                    delivery tag)
     * @param consumer The consumer that is to acknowledge this message.
     */
    public void addUnacknowledgedMessage(MessageInstance entry, long deliveryTag, ConsumerImpl consumer)
    {
        if (_logger.isDebugEnabled())
        {
                    _logger.debug(debugIdentity() + " Adding unacked message(" + entry.getMessage().toString() + " DT:" + deliveryTag
                               + ") for " + consumer + " on " + entry.getOwningResource().getName());

        }

        _unacknowledgedMessageMap.add(deliveryTag, entry);

    }

    private final String id = "(" + System.identityHashCode(this) + ")";

    public String debugIdentity()
    {
        return _channelId + id;
    }

    /**
     * Called to attempt re-delivery all outstanding unacknowledged messages on the channel. May result in delivery to
     * this same channel or to other subscribers.
     *
     */
    public void requeue()
    {
        // we must create a new map since all the messages will get a new delivery tag when they are redelivered
        Collection<MessageInstance> messagesToBeDelivered = _unacknowledgedMessageMap.cancelAllMessages();

        if (!messagesToBeDelivered.isEmpty())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Requeuing " + messagesToBeDelivered.size() + " unacked messages. for " + toString());
            }

        }

        for (MessageInstance unacked : messagesToBeDelivered)
        {
            // Mark message redelivered
            unacked.setRedelivered();

            // Ensure message is released for redelivery
            unacked.release();
        }

    }

    /**
     * Requeue a single message
     *
     * @param deliveryTag The message to requeue
     *
     */
    public void requeue(long deliveryTag)
    {
        MessageInstance unacked = _unacknowledgedMessageMap.remove(deliveryTag);

        if (unacked != null)
        {
            // Mark message redelivered
            unacked.setRedelivered();

            // Ensure message is released for redelivery
            unacked.release();

        }
        else
        {
            _logger.warn("Requested requeue of message:" + deliveryTag + " but no such delivery tag exists."
                      + _unacknowledgedMessageMap.size());

        }

    }

    public boolean isMaxDeliveryCountEnabled(final long deliveryTag)
    {
        final MessageInstance queueEntry = _unacknowledgedMessageMap.get(deliveryTag);
        if (queueEntry != null)
        {
            final int maximumDeliveryCount = queueEntry.getMaximumDeliveryCount();
            return maximumDeliveryCount > 0;
        }

        return false;
    }

    public boolean isDeliveredTooManyTimes(final long deliveryTag)
    {
        final MessageInstance queueEntry = _unacknowledgedMessageMap.get(deliveryTag);
        if (queueEntry != null)
        {
            final int maximumDeliveryCount = queueEntry.getMaximumDeliveryCount();
            final int numDeliveries = queueEntry.getDeliveryCount();
            return maximumDeliveryCount != 0 && numDeliveries >= maximumDeliveryCount;
        }

        return false;
    }

    /**
     * Called to resend all outstanding unacknowledged messages to this same channel.
     *
     */
    public void resend()
    {


        final Map<Long, MessageInstance> msgToRequeue = new LinkedHashMap<Long, MessageInstance>();
        final Map<Long, MessageInstance> msgToResend = new LinkedHashMap<Long, MessageInstance>();

        if (_logger.isDebugEnabled())
        {
            _logger.debug("unacked map Size:" + _unacknowledgedMessageMap.size());
        }

        // Process the Unacked-Map.
        // Marking messages who still have a consumer for to be resent
        // and those that don't to be requeued.
        _unacknowledgedMessageMap.visit(new ExtractResendAndRequeue(_unacknowledgedMessageMap,
                                                                    msgToRequeue,
                                                                    msgToResend
        ));


        // Process Messages to Resend
        if (_logger.isDebugEnabled())
        {
            if (!msgToResend.isEmpty())
            {
                _logger.debug("Preparing (" + msgToResend.size() + ") message to resend.");
            }
            else
            {
                _logger.debug("No message to resend.");
            }
        }

        for (Map.Entry<Long, MessageInstance> entry : msgToResend.entrySet())
        {
            MessageInstance message = entry.getValue();
            long deliveryTag = entry.getKey();

            //Amend the delivery counter as the client hasn't seen these messages yet.
            message.decrementDeliveryCount();

            // Without any details from the client about what has been processed we have to mark
            // all messages in the unacked map as redelivered.
            message.setRedelivered();

            if (!message.resend())
            {
                msgToRequeue.put(deliveryTag, message);
            }
        } // for all messages
        // } else !isSuspend

        if (_logger.isInfoEnabled())
        {
            if (!msgToRequeue.isEmpty())
            {
                _logger.info("Preparing (" + msgToRequeue.size() + ") message to requeue to.");
            }
        }

        // Process Messages to Requeue at the front of the queue
        for (Map.Entry<Long, MessageInstance> entry : msgToRequeue.entrySet())
        {
            MessageInstance message = entry.getValue();
            long deliveryTag = entry.getKey();

            //Amend the delivery counter as the client hasn't seen these messages yet.
            message.decrementDeliveryCount();

            _unacknowledgedMessageMap.remove(deliveryTag);

            message.setRedelivered();
            message.release();

        }
    }


    /**
     * Acknowledge one or more messages.
     *
     * @param deliveryTag the last delivery tag
     * @param multiple    if true will acknowledge all messages up to an including the delivery tag. if false only
     *                    acknowledges the single message specified by the delivery tag
     *
     */
    public void acknowledgeMessage(long deliveryTag, boolean multiple)
    {
        Collection<MessageInstance> ackedMessages = getAckedMessages(deliveryTag, multiple);
        _transaction.dequeue(ackedMessages, new MessageAcknowledgeAction(ackedMessages));
    }

    private Collection<MessageInstance> getAckedMessages(long deliveryTag, boolean multiple)
    {

        return _unacknowledgedMessageMap.acknowledge(deliveryTag, multiple);

    }

    /**
     * Used only for testing purposes.
     *
     * @return the map of unacknowledged messages
     */
    public UnacknowledgedMessageMap getUnacknowledgedMessageMap()
    {
        return _unacknowledgedMessageMap;
    }

    /**
     * Called from the ChannelFlowHandler to suspend this Channel
     * @param suspended boolean, should this Channel be suspended
     */
    public void setSuspended(boolean suspended)
    {
        boolean wasSuspended = _suspended.getAndSet(suspended);
        if (wasSuspended != suspended)
        {
            // Log Flow Started before we start the subscriptions
            if (!suspended)
            {
                getVirtualHost().getEventLogger().message(_logSubject, ChannelMessages.FLOW("Started"));
            }


            // This section takes two different approaches to perform to perform
            // the same function. Ensuring that the Subscription has taken note
            // of the change in Channel State

            // Here we have become unsuspended and so we ask each the queue to
            // perform an Async delivery for each of the subscriptions in this
            // Channel. The alternative would be to ensure that the subscription
            // had received the change in suspension state. That way the logic
            // behind deciding to start an async delivery was located with the
            // Subscription.
            if (wasSuspended)
            {
                // may need to deliver queued messages
                for (ConsumerTarget_0_8 s : _tag2SubscriptionTargetMap.values())
                {
                    for(ConsumerImpl sub : s.getConsumers())
                    {
                        sub.externalStateChange();
                    }
                }
            }


            // Here we have become suspended so we need to ensure that each of
            // the Subscriptions has noticed this change so that we can be sure
            // they are not still sending messages. Again the code here is a
            // very simplistic approach to ensure that the change of suspension
            // has been noticed by each of the Subscriptions. Unlike the above
            // case we don't actually need to do anything else.
            if (!wasSuspended)
            {
                // may need to deliver queued messages
                for (ConsumerTarget_0_8 s : _tag2SubscriptionTargetMap.values())
                {
                    try
                    {
                        s.getSendLock();
                    }
                    finally
                    {
                        s.releaseSendLock();
                    }
                }
            }


            // Log Suspension only after we have confirmed all suspensions are
            // stopped.
            if (suspended)
            {
                getVirtualHost().getEventLogger().message(_logSubject, ChannelMessages.FLOW("Stopped"));
            }

        }
    }

    public boolean isSuspended()
    {
        return _suspended.get()  || _closing.get() || _connection.isClosing();
    }


    public void commit(final Runnable immediateAction, boolean async)
    {


        if(async && _transaction instanceof LocalTransaction)
        {

            ((LocalTransaction)_transaction).commitAsync(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        immediateAction.run();
                    }
                    finally
                    {
                        _txnCommits.incrementAndGet();
                        _txnStarts.incrementAndGet();
                        decrementOutstandingTxnsIfNecessary();
                    }
                }
            });
        }
        else
        {
            _transaction.commit(immediateAction);

            _txnCommits.incrementAndGet();
            _txnStarts.incrementAndGet();
            decrementOutstandingTxnsIfNecessary();
        }
        resetUncommittedMessages();
    }

    private void resetUncommittedMessages()
    {
        _uncommittedMessageSize = 0l;
        _uncommittedMessages.clear();
    }

    public void rollback(Runnable postRollbackTask)
    {

        // stop all subscriptions
        _rollingBack = true;
        boolean requiresSuspend = _suspended.compareAndSet(false,true);

        // ensure all subscriptions have seen the change to the channel state
        for(ConsumerTarget_0_8 sub : _tag2SubscriptionTargetMap.values())
        {
            sub.getSendLock();
            sub.releaseSendLock();
        }

        try
        {
            _transaction.rollback();
        }
        finally
        {
            _rollingBack = false;

            _txnRejects.incrementAndGet();
            _txnStarts.incrementAndGet();
            decrementOutstandingTxnsIfNecessary();
            resetUncommittedMessages();
        }

        postRollbackTask.run();

        for(MessageInstance entry : _resendList)
        {
            ConsumerImpl sub = entry.getDeliveredConsumer();
            if(sub == null || sub.isClosed())
            {
                entry.release();
            }
            else
            {
                entry.resend();
            }
        }
        _resendList.clear();

        if(requiresSuspend)
        {
            _suspended.set(false);
            for(ConsumerTarget_0_8 target : _tag2SubscriptionTargetMap.values())
            {
                for(ConsumerImpl sub : target.getConsumers())
                {
                    sub.externalStateChange();
                }
            }

        }
    }

    public String toString()
    {
        return "("+ _suspended.get() + ", " + _closing.get() + ", " + _connection.isClosing() + ") "+"["+ _connection.toString()+":"+_channelId+"]";
    }

    public boolean isClosing()
    {
        return _closing.get();
    }

    public AMQProtocolEngine getConnection()
    {
        return _connection;
    }

    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
    }

    public void setCredit(final long prefetchSize, final int prefetchCount)
    {
        getVirtualHost().getEventLogger().message(ChannelMessages.PREFETCH_SIZE(prefetchSize, prefetchCount));
        _creditManager.setCreditLimits(prefetchSize, prefetchCount);
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    public ClientDeliveryMethod getClientDeliveryMethod()
    {
        return _clientDeliveryMethod;
    }

    private final RecordDeliveryMethod _recordDeliveryMethod = new RecordDeliveryMethod()
        {

            public void recordMessageDelivery(final ConsumerImpl sub, final MessageInstance entry, final long deliveryTag)
            {
                addUnacknowledgedMessage(entry, deliveryTag, sub);
            }
        };

    public RecordDeliveryMethod getRecordDeliveryMethod()
    {
        return _recordDeliveryMethod;
    }


    private AMQMessage createAMQMessage(StoredMessage<MessageMetaData> handle)
    {

        AMQMessage message = new AMQMessage(handle, _connection.getReference());

        return message;
    }

    private boolean checkMessageUserId(ContentHeaderBody header)
    {
        AMQShortString userID = header.getProperties().getUserId();
        return (!_messageAuthorizationRequired || _connection.getAuthorizedPrincipal().getName().equals(userID == null? "" : userID.toString()));

    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public AMQProtocolEngine getConnectionModel()
    {
        return _connection;
    }

    public String getClientID()
    {
        return String.valueOf(_connection.getContextKey());
    }

    public LogSubject getLogSubject()
    {
        return _logSubject;
    }

    @Override
    public int compareTo(AMQSessionModel o)
    {
        return getId().compareTo(o.getId());
    }

    @Override
    public void addDeleteTask(final Action<? super AMQChannel> task)
    {
        _taskList.add(task);
    }

    @Override
    public void removeDeleteTask(final Action<? super AMQChannel> task)
    {
        _taskList.remove(task);
    }

    public Subject getSubject()
    {
        return _subject;
    }

    public boolean hasCurrentMessage()
    {
        return _currentMessage != null;
    }

    public long getMaxUncommittedInMemorySize()
    {
        return _maxUncommittedInMemorySize;
    }

    private class GetDeliveryMethod implements ClientDeliveryMethod
    {

        private final FlowCreditManager _singleMessageCredit;
        private final AMQQueue _queue;
        private boolean _deliveredMessage;

        public GetDeliveryMethod(final FlowCreditManager singleMessageCredit,
                                 final AMQQueue queue)
        {
            _singleMessageCredit = singleMessageCredit;
            _queue = queue;
        }

        @Override
        public long deliverToClient(final ConsumerImpl sub, final ServerMessage message,
                                    final InstanceProperties props, final long deliveryTag)
        {
            _singleMessageCredit.useCreditForMessage(message.getSize());
            long size = _connection.getProtocolOutputConverter().writeGetOk(message,
                                                                            props,
                                                                            AMQChannel.this.getChannelId(),
                                                                            deliveryTag,
                                                                            _queue.getQueueDepthMessages());

            _deliveredMessage = true;
            return size;
        }

        public boolean hasDeliveredMessage()
        {
            return _deliveredMessage;
        }
    }


    private class ImmediateAction implements Action<MessageInstance>
    {

        public ImmediateAction()
        {
        }

        public void performAction(MessageInstance entry)
        {
            TransactionLogResource queue = entry.getOwningResource();

            if (!entry.getDeliveredToConsumer() && entry.acquire())
            {

                ServerTransaction txn = new LocalTransaction(_messageStore);
                final AMQMessage message = (AMQMessage) entry.getMessage();
                MessageReference ref = message.newReference();
                try
                {
                    entry.delete();
                    txn.dequeue(queue, message,
                                new ServerTransaction.Action()
                                {
                                    @Override
                                    public void postCommit()
                                    {
                                        final ProtocolOutputConverter outputConverter =
                                                    _connection.getProtocolOutputConverter();

                                        outputConverter.writeReturn(message.getMessagePublishInfo(),
                                                                    message.getContentHeaderBody(),
                                                                    message,
                                                                    _channelId,
                                                                    AMQConstant.NO_CONSUMERS.getCode(),
                                                                    IMMEDIATE_DELIVERY_REPLY_TEXT);

                                    }

                                    @Override
                                    public void onRollback()
                                    {

                                    }
                                }
                               );
                    txn.commit();
                }
                finally
                {
                    ref.release();
                }


            }
            else
            {
                if(queue instanceof CapacityChecker)
                {
                    ((CapacityChecker)queue).checkCapacity(AMQChannel.this);
                }
            }

        }
    }

    private final class CapacityCheckAction implements Action<MessageInstance>
    {
        @Override
        public void performAction(final MessageInstance entry)
        {
            TransactionLogResource queue = entry.getOwningResource();
            if(queue instanceof CapacityChecker)
            {
                ((CapacityChecker)queue).checkCapacity(AMQChannel.this);
            }
        }
    }

    private class MessageAcknowledgeAction implements ServerTransaction.Action
    {
        private final Collection<MessageInstance> _ackedMessages;

        public MessageAcknowledgeAction(Collection<MessageInstance> ackedMessages)
        {
            _ackedMessages = ackedMessages;
        }

        public void postCommit()
        {
            try
            {
                for(MessageInstance entry : _ackedMessages)
                {
                    entry.delete();
                }
            }
            finally
            {
                _ackedMessages.clear();
            }

        }

        public void onRollback()
        {
            // explicit rollbacks resend the message after the rollback-ok is sent
            if(_rollingBack)
            {
                for(MessageInstance entry : _ackedMessages)
                {
                    entry.unlockAcquisition();
                }
                _resendList.addAll(_ackedMessages);
            }
            else
            {
                try
                {
                    for(MessageInstance entry : _ackedMessages)
                    {
                        entry.release();
                    }
                }
                finally
                {
                    _ackedMessages.clear();
                }
            }

        }
    }

    private class WriteReturnAction implements ServerTransaction.Action
    {
        private final AMQConstant _errorCode;
        private final String _description;
        private final MessageReference<AMQMessage> _reference;

        public WriteReturnAction(AMQConstant errorCode,
                                 String description,
                                 AMQMessage message)
        {
            _errorCode = errorCode;
            _description = description;
            _reference = message.newReference();
        }

        public void postCommit()
        {
            AMQMessage message = _reference.getMessage();
            _connection.getProtocolOutputConverter().writeReturn(message.getMessagePublishInfo(),
                                                          message.getContentHeaderBody(),
                                                          message,
                                                          _channelId,
                                                          _errorCode.getCode(),
                                                          AMQShortString.validValueOf(_description));
            _reference.release();
        }

        public void onRollback()
        {
            _reference.release();
        }
    }

    public synchronized void block()
    {
        if(_blockingEntities.add(this))
        {
            if(_blocking.compareAndSet(false,true))
            {
                getVirtualHost().getEventLogger().message(_logSubject,
                                                          ChannelMessages.FLOW_ENFORCED("** All Queues **"));
                flow(false);
                _blockTime = System.currentTimeMillis();
            }
        }
    }

    public synchronized void unblock()
    {
        if(_blockingEntities.remove(this))
        {
            if(_blockingEntities.isEmpty() && _blocking.compareAndSet(true,false))
            {
                getVirtualHost().getEventLogger().message(_logSubject, ChannelMessages.FLOW_REMOVED());

                flow(true);
            }
        }
    }

    public synchronized void block(AMQQueue queue)
    {
        if(_blockingEntities.add(queue))
        {

            if(_blocking.compareAndSet(false,true))
            {
                getVirtualHost().getEventLogger().message(_logSubject, ChannelMessages.FLOW_ENFORCED(queue.getName()));
                flow(false);
                _blockTime = System.currentTimeMillis();

            }
        }
    }

    public synchronized void unblock(AMQQueue queue)
    {
        if(_blockingEntities.remove(queue))
        {
            if(_blockingEntities.isEmpty() && _blocking.compareAndSet(true,false) && !isClosing())
            {
                getVirtualHost().getEventLogger().message(_logSubject, ChannelMessages.FLOW_REMOVED());
                flow(true);
            }
        }
    }

    @Override
    public Object getConnectionReference()
    {
        return getConnection().getReference();
    }

    public int getUnacknowledgedMessageCount()
    {
        return getUnacknowledgedMessageMap().size();
    }

    private void flow(boolean flow)
    {
        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createChannelFlowBody(flow);
        _connection.writeFrame(responseBody.generateFrame(_channelId));
    }

    @Override
    public boolean getBlocking()
    {
        return _blocking.get();
    }

    public VirtualHostImpl getVirtualHost()
    {
        return getConnection().getVirtualHost();
    }

    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose)
    {
        _transactionTimeoutHelper.checkIdleOrOpenTimes(_transaction, openWarn, openClose, idleWarn, idleClose);
    }

    /**
     * Typically called from the HouseKeepingThread instead of the main receiver thread,
     * therefore uses a lock to close the connection in a thread-safe manner.
     */
    private void closeConnection(String reason) throws AMQException
    {
        Lock receivedLock = _connection.getReceivedLock();
        receivedLock.lock();
        try
        {
            _connection.close(AMQConstant.RESOURCE_ERROR, reason);
        }
        finally
        {
            receivedLock.unlock();
        }
    }

    public void deadLetter(long deliveryTag)
    {
        final UnacknowledgedMessageMap unackedMap = getUnacknowledgedMessageMap();
        final MessageInstance rejectedQueueEntry = unackedMap.remove(deliveryTag);

        if (rejectedQueueEntry == null)
        {
            _logger.warn("No message found, unable to DLQ delivery tag: " + deliveryTag);
        }
        else
        {
            final ServerMessage msg = rejectedQueueEntry.getMessage();


            int requeues = rejectedQueueEntry.routeToAlternate(new Action<MessageInstance>()
                {
                    @Override
                    public void performAction(final MessageInstance requeueEntry)
                    {
                        getVirtualHost().getEventLogger().message(_logSubject,
                                                                  ChannelMessages.DEADLETTERMSG(msg.getMessageNumber(),
                                                                                                requeueEntry.getOwningResource()
                                                                                                        .getName()));
                    }
                }, null);

            if(requeues == 0)
            {

                final TransactionLogResource owningResource = rejectedQueueEntry.getOwningResource();
                if(owningResource instanceof AMQQueue)
                {
                    final AMQQueue queue = (AMQQueue) owningResource;

                    final Exchange altExchange = queue.getAlternateExchange();

                    if (altExchange == null)
                    {
                        _logger.debug("No alternate exchange configured for queue, must discard the message as unable to DLQ: delivery tag: " + deliveryTag);
                        getVirtualHost().getEventLogger().message(_logSubject,
                                                                  ChannelMessages.DISCARDMSG_NOALTEXCH(msg.getMessageNumber(),
                                                                                                       queue.getName(),
                                                                                                       msg.getInitialRoutingAddress()));

                    }
                    else
                    {
                        _logger.debug(
                                "Routing process provided no queues to enqueue the message on, must discard message as unable to DLQ: delivery tag: "
                                + deliveryTag);
                        getVirtualHost().getEventLogger().message(_logSubject,
                                                                  ChannelMessages.DISCARDMSG_NOROUTE(msg.getMessageNumber(),
                                                                                                     altExchange.getName()));
                    }
                }
            }

        }
    }

    public void recordFuture(final StoreFuture future, final ServerTransaction.Action action)
    {
        _unfinishedCommandsQueue.add(new AsyncCommand(future, action));
    }

    public void sync()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("sync() called on channel " + debugIdentity());
        }

        AsyncCommand cmd;
        while((cmd = _unfinishedCommandsQueue.poll()) != null)
        {
            cmd.awaitReadyForCompletion();
            cmd.complete();
        }
        if(_transaction instanceof LocalTransaction)
        {
            ((LocalTransaction)_transaction).sync();
        }
    }

    private static class AsyncCommand
    {
        private final StoreFuture _future;
        private ServerTransaction.Action _action;

        public AsyncCommand(final StoreFuture future, final ServerTransaction.Action action)
        {
            _future = future;
            _action = action;
        }

        void awaitReadyForCompletion()
        {
            _future.waitForCompletion();
        }

        void complete()
        {
            if(!_future.isComplete())
            {
                _future.waitForCompletion();
            }
            _action.postCommit();
            _action = null;
        }
    }

    @Override
    public int getConsumerCount()
    {
        return _tag2SubscriptionTargetMap.size();
    }

    @Override
    public Collection<Consumer<?>> getConsumers()
    {
        return Collections.unmodifiableCollection(_consumers);
    }

    private class ConsumerClosedListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(final ConfiguredObject object, final State oldState, final State newState)
        {
            if(newState == State.DELETED)
            {
                consumerRemoved((Consumer<?>)object);
            }
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void attributeSet(final ConfiguredObject object,
                                 final String attributeName,
                                 final Object oldAttributeValue,
                                 final Object newAttributeValue)
        {

        }
    }

    private void consumerAdded(final Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerAdded(consumer);
        }
    }

    private void consumerRemoved(final Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerRemoved(consumer);
        }
    }

    @Override
    public void addConsumerListener(ConsumerListener listener)
    {
        _consumerListeners.add(listener);
    }

    @Override
    public void removeConsumerListener(ConsumerListener listener)
    {
        _consumerListeners.remove(listener);
    }

    @Override
    public void setModelObject(final Session<?> session)
    {
        _modelObject = session;
    }

    @Override
    public Session<?> getModelObject()
    {
        return _modelObject;
    }

    @Override
    public long getTransactionStartTime()
    {
        ServerTransaction serverTransaction = _transaction;
        if (serverTransaction.isTransactional())
        {
            return serverTransaction.getTransactionStartTime();
        }
        else
        {
            return 0L;
        }
    }

    @Override
    public long getTransactionUpdateTime()
    {
        ServerTransaction serverTransaction = _transaction;
        if (serverTransaction.isTransactional())
        {
            return serverTransaction.getTransactionUpdateTime();
        }
        else
        {
            return 0L;
        }
    }

    @Override
    public void receiveAccessRequest(final AMQShortString realm,
                                     final boolean exclusive,
                                     final boolean passive,
                                     final boolean active, final boolean write, final boolean read)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] AccessRequest[" +" realm: " + realm +
                          " exclusive: " + exclusive +
                          " passive: " + passive +
                          " active: " + active +
                          " write: " + write + " read: " + read + " ]");
        }

        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        if (ProtocolVersion.v0_91.equals(_connection.getProtocolVersion()))
        {
            _connection.closeConnection(AMQConstant.COMMAND_INVALID,
                                                    "AccessRequest not present in AMQP versions other than 0-8, 0-9",
                                                    _channelId);
        }
        else
        {
            // We don't implement access control class, but to keep clients happy that expect it
            // always use the "0" ticket.
            AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);
            sync();
            _connection.writeFrame(response.generateFrame(_channelId));
        }
    }

    @Override
    public void receiveBasicAck(final long deliveryTag, final boolean multiple)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicAck[" +" deliveryTag: " + deliveryTag + " multiple: " + multiple + " ]");
        }

        acknowledgeMessage(deliveryTag, multiple);
    }

    @Override
    public void receiveBasicCancel(final AMQShortString consumerTag, final boolean nowait)
    {

        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicCancel[" +" consumerTag: " + consumerTag + " noWait: " + nowait + " ]");
        }

        unsubscribeConsumer(consumerTag);
        if (!nowait)
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            BasicCancelOkBody cancelOkBody = methodRegistry.createBasicCancelOkBody(consumerTag);
            sync();
            _connection.writeFrame(cancelOkBody.generateFrame(_channelId));
        }
    }

    @Override
    public void receiveBasicConsume(final AMQShortString queue,
                                    final AMQShortString consumerTag,
                                    final boolean noLocal,
                                    final boolean noAck,
                                    final boolean exclusive, final boolean nowait, final FieldTable arguments)
    {

        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicConsume[" +" queue: " + queue +
                          " consumerTag: " + consumerTag +
                          " noLocal: " + noLocal +
                          " noAck: " + noAck +
                          " exclusive: " + exclusive + " nowait: " + nowait + " arguments: " + arguments + " ]");
        }

        AMQShortString consumerTag1 = consumerTag;
        VirtualHostImpl<?, ?, ?> vHost = _connection.getVirtualHost();
        sync();
        String queueName = queue == null ? null : queue.asString();

        MessageSource queue1 = queueName == null ? getDefaultQueue() : vHost.getQueue(queueName);
        final Collection<MessageSource> sources = new HashSet<>();
        if (queue1 != null)
        {
            sources.add(queue1);
        }
        else if (vHost.getContextValue(Boolean.class, "qpid.enableMultiQueueConsumers")
                 && arguments != null
                 && arguments.get("x-multiqueue") instanceof Collection)
        {
            for (Object object : (Collection<Object>) arguments.get("x-multiqueue"))
            {
                String sourceName = String.valueOf(object);
                sourceName = sourceName.trim();
                if (sourceName.length() != 0)
                {
                    MessageSource source = vHost.getMessageSource(sourceName);
                    if (source == null)
                    {
                        sources.clear();
                        break;
                    }
                    else
                    {
                        sources.add(source);
                    }
                }
            }
            queueName = arguments.get("x-multiqueue").toString();
        }

        if (sources.isEmpty())
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("No queue for '" + queueName + "'");
            }
            if (queueName != null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "No such queue, '" + queueName + "'");
            }
            else
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                            "No queue name provided, no default queue defined.", _channelId);
            }
        }
        else
        {
            try
            {
                consumerTag1 = consumeFromSource(consumerTag1,
                                                 sources,
                                                 !noAck,
                                                 arguments,
                                                 exclusive,
                                                 noLocal);
                if (!nowait)
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createBasicConsumeOkBody(consumerTag1);
                    _connection.writeFrame(responseBody.generateFrame(_channelId));

                }
            }
            catch (ConsumerTagInUseException cte)
            {

                _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                            "Non-unique consumer tag, '" + consumerTag1
                                            + "'", _channelId);
            }
            catch (AMQInvalidArgumentException ise)
            {
                _connection.closeConnection(AMQConstant.ARGUMENT_INVALID, ise.getMessage(), _channelId);


            }
            catch (AMQQueue.ExistingExclusiveConsumer e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED,
                                            "Cannot subscribe to queue '"
                                                                    + queue1.getName()
                                                                    + "' as it already has an existing exclusive consumer", _channelId);

            }
            catch (AMQQueue.ExistingConsumerPreventsExclusive e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED,
                                            "Cannot subscribe to queue '"
                                                                    + queue1.getName()
                                                                    + "' exclusively as it already has a consumer", _channelId);

            }
            catch (AccessControlException e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED, "Cannot subscribe to queue '"
                                                                    + queue1.getName()
                                                                    + "' permission denied", _channelId);

            }
            catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED,
                                            "Cannot subscribe to queue '"
                                                                    + queue1.getName()
                                                                    + "' as it already has an incompatible exclusivity policy", _channelId);

            }

        }
    }

    @Override
    public void receiveBasicGet(final AMQShortString queueName, final boolean noAck)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicGet[" +" queue: " + queueName + " noAck: " + noAck + " ]");
        }

        VirtualHostImpl vHost = _connection.getVirtualHost();
        sync();
        AMQQueue queue = queueName == null ? getDefaultQueue() : vHost.getQueue(queueName.toString());
        if (queue == null)
        {
            _logger.info("No queue for '" + queueName + "'");
            if (queueName != null)
            {
                _connection.closeConnection(AMQConstant.NOT_FOUND, "No such queue, '" + queueName + "'", _channelId);

            }
            else
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                            "No queue name provided, no default queue defined.", _channelId);

            }
        }
        else
        {

            try
            {
                if (!performGet(queue, !noAck))
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();

                    BasicGetEmptyBody responseBody = methodRegistry.createBasicGetEmptyBody(null);

                    _connection.writeFrame(responseBody.generateFrame(_channelId));
                }
            }
            catch (AccessControlException e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), _channelId);
            }
            catch (MessageSource.ExistingExclusiveConsumer e)
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Queue has an exclusive consumer", _channelId);
            }
            catch (MessageSource.ExistingConsumerPreventsExclusive e)
            {
                _connection.closeConnection(AMQConstant.INTERNAL_ERROR,
                                            "The GET request has been evaluated as an exclusive consumer, " +
                                        "this is likely due to a programming error in the Qpid broker", _channelId);
            }
            catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                            "Queue has an incompatible exclusivity policy", _channelId);
            }
        }
    }

    @Override
    public void receiveBasicPublish(final AMQShortString exchangeName,
                                    final AMQShortString routingKey,
                                    final boolean mandatory,
                                    final boolean immediate)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicPublish[" +" exchange: " + exchangeName +
                          " routingKey: " + routingKey +
                          " mandatory: " + mandatory +
                          " immediate: " + immediate + " ]");
        }



        VirtualHostImpl vHost = _connection.getVirtualHost();

        if(blockingTimeoutExceeded())
        {
            getVirtualHost().getEventLogger().message(ChannelMessages.FLOW_CONTROL_IGNORED());
            closeChannel(AMQConstant.MESSAGE_TOO_LARGE,
                         "Channel flow control was requested, but not enforced by sender");
        }
        else
        {
            MessageDestination destination;

            if (isDefaultExchange(exchangeName))
            {
                destination = vHost.getDefaultDestination();
            }
            else
            {
                destination = vHost.getMessageDestination(exchangeName.toString());
            }

            // if the exchange does not exist we raise a channel exception
            if (destination == null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "Unknown exchange name: '" + exchangeName + "'");
            }
            else
            {

                MessagePublishInfo info = new MessagePublishInfo(exchangeName,
                                                                 immediate,
                                                                 mandatory,
                                                                 routingKey);

                try
                {
                    setPublishFrame(info, destination);
                }
                catch (AccessControlException e)
                {
                    _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());

                }
            }
        }
    }

    private boolean blockingTimeoutExceeded()
    {

        return _blocking.get() && (System.currentTimeMillis() - _blockTime) > _blockingTimeout;
    }

    @Override
    public void receiveBasicQos(final long prefetchSize, final int prefetchCount, final boolean global)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicQos[" +" prefetchSize: " + prefetchSize + " prefetchCount: " + prefetchCount + " global: " + global + " ]");
        }

        sync();
        setCredit(prefetchSize, prefetchCount);

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createBasicQosOkBody();
        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveBasicRecover(final boolean requeue, final boolean sync)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicRecover[" + " requeue: " + requeue + " sync: " + sync + " ]");
        }

        resend();

        if (sync)
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody recoverOk = methodRegistry.createBasicRecoverSyncOkBody();
            sync();
            _connection.writeFrame(recoverOk.generateFrame(getChannelId()));

        }

    }

    @Override
    public void receiveBasicReject(final long deliveryTag, final boolean requeue)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicReject[" +" deliveryTag: " + deliveryTag + " requeue: " + requeue + " ]");
        }

        MessageInstance message = getUnacknowledgedMessageMap().get(deliveryTag);

        if (message == null)
        {
            _logger.warn("Dropping reject request as message is null for tag:" + deliveryTag);
        }
        else
        {

            if (message.getMessage() == null)
            {
                _logger.warn("Message has already been purged, unable to Reject.");
            }
            else
            {

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Rejecting: DT:" + deliveryTag
                                                             + "-" + message.getMessage() +
                                  ": Requeue:" + requeue
                                  +
                                  " on channel:" + debugIdentity());
                }

                if (requeue)
                {
                    message.decrementDeliveryCount();

                    requeue(deliveryTag);
                }
                else
                {
                    // Since the Java client abuses the reject flag for requeing after rollback, we won't set reject here
                    // as it would prevent redelivery
                    // message.reject();

                    final boolean maxDeliveryCountEnabled = isMaxDeliveryCountEnabled(deliveryTag);
                    _logger.debug("maxDeliveryCountEnabled: "
                                  + maxDeliveryCountEnabled
                                  + " deliveryTag "
                                  + deliveryTag);
                    if (maxDeliveryCountEnabled)
                    {
                        final boolean deliveredTooManyTimes = isDeliveredTooManyTimes(deliveryTag);
                        _logger.debug("deliveredTooManyTimes: "
                                      + deliveredTooManyTimes
                                      + " deliveryTag "
                                      + deliveryTag);
                        if (deliveredTooManyTimes)
                        {
                            deadLetter(deliveryTag);
                        }
                        else
                        {
                            //this requeue represents a message rejected because of a recover/rollback that we
                            //are not ready to DLQ. We rely on the reject command to resend from the unacked map
                            //and therefore need to increment the delivery counter so we cancel out the effect
                            //of the AMQChannel#resend() decrement.
                            message.incrementDeliveryCount();
                        }
                    }
                    else
                    {
                        requeue(deliveryTag);
                    }
                }
            }
        }
    }

    @Override
    public void receiveChannelClose(final int replyCode,
                                    final AMQShortString replyText,
                                    final int classId,
                                    final int methodId)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ChannelClose[" +" replyCode: " + replyCode + " replyText: " + replyText + " classId: " + classId + " methodId: " + methodId + " ]");
        }


        sync();
        _connection.closeChannel(this);

        _connection.writeFrame(new AMQFrame(getChannelId(),
                                            _connection.getMethodRegistry().createChannelCloseOkBody()));
    }

    @Override
    public void receiveChannelCloseOk()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ChannelCloseOk");
        }

        _connection.closeChannelOk(getChannelId());
    }

    @Override
    public void receiveMessageContent(final byte[] data)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] MessageContent[" +" data: " + hex(data,_connection.getBinaryDataLimit()) + " ] ");
        }

        if(hasCurrentMessage())
        {
            publishContentBody(new ContentBody(data));
        }
        else
        {
            _connection.closeConnection(AMQConstant.COMMAND_INVALID,
                                        "Attempt to send a content header without first sending a publish frame",
                                        _channelId);
        }
    }

    @Override
    public void receiveMessageHeader(final BasicContentHeaderProperties properties, final long bodySize)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] MessageHeader[ properties: {" + properties + "} bodySize: " + bodySize + " ]");
        }

        if(hasCurrentMessage())
        {
            if(bodySize > _connection.getMaxMessageSize())
            {
                closeChannel(AMQConstant.MESSAGE_TOO_LARGE,
                             "Message size of " + bodySize + " greater than allowed maximum of " + _connection.getMaxMessageSize());
            }
            publishContentHeader(new ContentHeaderBody(properties, bodySize));
        }
        else
        {
            _connection.closeConnection(AMQConstant.COMMAND_INVALID,
                                        "Attempt to send a content header without first sending a publish frame",
                                        _channelId);
        }
    }

    @Override
    public boolean ignoreAllButCloseOk()
    {
        return _connection.ignoreAllButCloseOk() || _connection.channelAwaitingClosure(_channelId);
    }

    @Override
    public void receiveBasicNack(final long deliveryTag, final boolean multiple, final boolean requeue)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] BasicNack[" +" deliveryTag: " + deliveryTag + " multiple: " + multiple + " requeue: " + requeue + " ]");
        }

        Map<Long, MessageInstance> nackedMessageMap = new LinkedHashMap<>();
        _unacknowledgedMessageMap.collect(deliveryTag, multiple, nackedMessageMap);

        for(MessageInstance message : nackedMessageMap.values())
        {

            if (message == null)
            {
                _logger.warn("Ignoring nack request as message is null for tag:" + deliveryTag);
            }
            else
            {

                if (message.getMessage() == null)
                {
                    _logger.warn("Message has already been purged, unable to nack.");
                }
                else
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Nack-ing: DT:" + deliveryTag
                                      + "-" + message.getMessage() +
                                      ": Requeue:" + requeue
                                      +
                                      " on channel:" + debugIdentity());
                    }

                    if (requeue)
                    {
                        message.decrementDeliveryCount();

                        requeue(deliveryTag);
                    }
                    else
                    {
                        message.reject();

                        final boolean maxDeliveryCountEnabled = isMaxDeliveryCountEnabled(deliveryTag);
                        _logger.debug("maxDeliveryCountEnabled: "
                                      + maxDeliveryCountEnabled
                                      + " deliveryTag "
                                      + deliveryTag);
                        if (maxDeliveryCountEnabled)
                        {
                            final boolean deliveredTooManyTimes = isDeliveredTooManyTimes(deliveryTag);
                            _logger.debug("deliveredTooManyTimes: "
                                          + deliveredTooManyTimes
                                          + " deliveryTag "
                                          + deliveryTag);
                            if (deliveredTooManyTimes)
                            {
                                deadLetter(deliveryTag);
                            }
                            else
                            {
                                message.incrementDeliveryCount();
                            }
                        }
                        else
                        {
                            requeue(deliveryTag);
                        }
                    }
                }
            }

        }

    }

    @Override
    public void receiveChannelFlow(final boolean active)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ChannelFlow[" +" active: " + active + " ]");
        }


        sync();
        setSuspended(!active);

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createChannelFlowOkBody(active);
        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveChannelFlowOk(final boolean active)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ChannelFlowOk[" +" active: " + active + " ]");
        }

        // TODO - should we do anything here?
    }

    @Override
    public void receiveExchangeBound(final AMQShortString exchangeName,
                                     final AMQShortString routingKey,
                                     final AMQShortString queueName)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ExchangeBound[" +" exchange: " + exchangeName + " routingKey: " +
                          routingKey + " queue: " + queueName + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        sync();

        int replyCode;
        String replyText;

        if (isDefaultExchange(exchangeName))
        {
            if (routingKey == null)
            {
                if (queueName == null)
                {
                    replyCode = virtualHost.getQueues().isEmpty()
                            ? ExchangeBoundOkBody.NO_BINDINGS
                            : ExchangeBoundOkBody.OK;
                    replyText = null;

                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {
                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                }
            }
            else
            {
                if (queueName == null)
                {
                    replyCode = virtualHost.getQueue(routingKey.toString()) == null
                            ? ExchangeBoundOkBody.NO_QUEUE_BOUND_WITH_RK
                            : ExchangeBoundOkBody.OK;
                    replyText = null;
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        replyCode = queueName.equals(routingKey)
                                ? ExchangeBoundOkBody.OK
                                : ExchangeBoundOkBody.SPECIFIC_QUEUE_NOT_BOUND_WITH_RK;
                        replyText = null;
                    }
                }
            }
        }
        else
        {
            ExchangeImpl exchange = virtualHost.getExchange(exchangeName.toString());
            if (exchange == null)
            {

                replyCode = ExchangeBoundOkBody.EXCHANGE_NOT_FOUND;
                replyText = "Exchange '" + exchangeName + "' not found";
            }
            else if (routingKey == null)
            {
                if (queueName == null)
                {
                    if (exchange.hasBindings())
                    {
                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.NO_BINDINGS;
                        replyText = null;
                    }
                }
                else
                {

                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {
                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        if (exchange.isBound(queue))
                        {
                            replyCode = ExchangeBoundOkBody.OK;
                            replyText = null;
                        }
                        else
                        {
                            replyCode = ExchangeBoundOkBody.QUEUE_NOT_BOUND;
                            replyText = "Queue '"
                                        + queueName
                                        + "' not bound to exchange '"
                                        + exchangeName
                                        + "'";
                        }
                    }
                }
            }
            else if (queueName != null)
            {
                AMQQueue queue = virtualHost.getQueue(queueName.toString());
                if (queue == null)
                {
                    replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                    replyText = "Queue '" + queueName + "' not found";
                }
                else
                {
                    String bindingKey = routingKey == null ? null : routingKey.asString();
                    if (exchange.isBound(bindingKey, queue))
                    {

                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.SPECIFIC_QUEUE_NOT_BOUND_WITH_RK;
                        replyText = "Queue '" + queueName + "' not bound with routing key '" +
                                    routingKey + "' to exchange '" + exchangeName + "'";

                    }
                }
            }
            else
            {
                if (exchange.isBound(routingKey == null ? "" : routingKey.asString()))
                {

                    replyCode = ExchangeBoundOkBody.OK;
                    replyText = null;
                }
                else
                {
                    replyCode = ExchangeBoundOkBody.NO_QUEUE_BOUND_WITH_RK;
                    replyText =
                            "No queue bound with routing key '" + routingKey + "' to exchange '" + exchangeName + "'";
                }
            }
        }

        ExchangeBoundOkBody exchangeBoundOkBody =
                methodRegistry.createExchangeBoundOkBody(replyCode, AMQShortString.validValueOf(replyText));

        _connection.writeFrame(exchangeBoundOkBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveExchangeDeclare(final AMQShortString exchangeName,
                                       final AMQShortString type,
                                       final boolean passive,
                                       final boolean durable,
                                       final boolean autoDelete,
                                       final boolean internal,
                                       final boolean nowait,
                                       final FieldTable arguments)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ExchangeDeclare[" +" exchange: " + exchangeName +
                          " type: " + type +
                          " passive: " + passive +
                          " durable: " + durable +
                          " autoDelete: " + autoDelete +
                          " internal: " + internal + " nowait: " + nowait + " arguments: " + arguments + " ]");
        }

        ExchangeImpl exchange;
        VirtualHostImpl<?, ?, ?> virtualHost = _connection.getVirtualHost();
        if (isDefaultExchange(exchangeName))
        {
            if (!new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_CLASS).equals(type))
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare default exchange: "
                                                                 + " of type "
                                                                 + ExchangeDefaults.DIRECT_EXCHANGE_CLASS
                                                                 + " to " + type + ".", getChannelId());
            }
            else if (!nowait)
            {
                MethodRegistry methodRegistry = _connection.getMethodRegistry();
                AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                sync();
                _connection.writeFrame(responseBody.generateFrame(getChannelId()));
            }

        }
        else
        {
            if (passive)
            {
                exchange = virtualHost.getExchange(exchangeName.toString());
                if (exchange == null)
                {
                    closeChannel(AMQConstant.NOT_FOUND, "Unknown exchange: '" + exchangeName + "'");
                }
                else if (!(type == null || type.length() == 0) && !exchange.getType().equals(type.asString()))
                {

                    _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: '"
                                                                         + exchangeName
                                                                         + "' of type "
                                                                         + exchange.getType()
                                                                         + " to "
                                                                         + type
                                                                         + ".", getChannelId());
                }
                else if (!nowait)
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                    sync();
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }

            }
            else
            {
                try
                {
                    String name = exchangeName == null ? null : exchangeName.intern().toString();
                    String typeString = type == null ? null : type.intern().toString();

                    Map<String, Object> attributes = new HashMap<String, Object>();
                    if (arguments != null)
                    {
                        attributes.putAll(FieldTable.convertToMap(arguments));
                    }
                    attributes.put(Exchange.NAME, name);
                    attributes.put(Exchange.TYPE, typeString);
                    attributes.put(Exchange.DURABLE, durable);
                    attributes.put(Exchange.LIFETIME_POLICY,
                                   autoDelete ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
                    if (!attributes.containsKey(Exchange.ALTERNATE_EXCHANGE))
                    {
                        attributes.put(Exchange.ALTERNATE_EXCHANGE, null);
                    }
                    exchange = virtualHost.createExchange(attributes);

                    if (!nowait)
                    {
                        sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                        _connection.writeFrame(responseBody.generateFrame(
                                getChannelId()));
                    }

                }
                catch (ReservedExchangeNameException e)
                {
                    _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                                "Attempt to declare exchange: '" + exchangeName +
                                                                         "' which begins with reserved prefix.", getChannelId());


                }
                catch (ExchangeExistsException e)
                {
                    exchange = e.getExistingExchange();
                    if (!new AMQShortString(exchange.getType()).equals(type))
                    {
                        _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: '"
                                                                                 + exchangeName + "' of type "
                                                                                 + exchange.getType()
                                                                                 + " to " + type + ".", getChannelId());

                    }
                    else
                    {
                        if (!nowait)
                        {
                            sync();
                            MethodRegistry methodRegistry = _connection.getMethodRegistry();
                            AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                            _connection.writeFrame(responseBody.generateFrame(
                                    getChannelId()));
                        }
                    }
                }
                catch (NoFactoryForTypeException e)
                {
                    _connection.closeConnection(AMQConstant.COMMAND_INVALID, "Unknown exchange type '"
                                                                             + e.getType()
                                                                             + "' for exchange '"
                                                                             + exchangeName
                                                                             + "'", getChannelId());

                }
                catch (AccessControlException e)
                {
                    _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());

                }
                catch (UnknownConfiguredObjectException e)
                {
                    // note - since 0-8/9/9-1 can't set the alt. exchange this exception should never occur
                    final String message = "Unknown alternate exchange "
                                           + (e.getName() != null
                            ? "name: '" + e.getName() + "'"
                            : "id: " + e.getId());
                    _connection.closeConnection(AMQConstant.NOT_FOUND, message, getChannelId());

                }
                catch (IllegalArgumentException e)
                {
                    _connection.closeConnection(AMQConstant.COMMAND_INVALID, "Error creating exchange '"
                                                                             + exchangeName
                                                                             + "': "
                                                                             + e.getMessage(), getChannelId());

                }
            }
        }

    }

    @Override
    public void receiveExchangeDelete(final AMQShortString exchangeStr, final boolean ifUnused, final boolean nowait)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ExchangeDelete[" +" exchange: " + exchangeStr + " ifUnused: " + ifUnused + " nowait: " + nowait + " ]");
        }


        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        sync();
        try
        {

            if (isDefaultExchange(exchangeStr))
            {
                _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                            "Default Exchange cannot be deleted", getChannelId());

            }

            else
            {
                final String exchangeName = exchangeStr.toString();

                final ExchangeImpl exchange = virtualHost.getExchange(exchangeName);
                if (exchange == null)
                {
                    closeChannel(AMQConstant.NOT_FOUND, "No such exchange: '" + exchangeStr + "'");
                }
                else
                {
                    virtualHost.removeExchange(exchange, !ifUnused);

                    ExchangeDeleteOkBody responseBody = _connection.getMethodRegistry().createExchangeDeleteOkBody();

                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }
            }
        }
        catch (ExchangeIsAlternateException e)
        {
            closeChannel(AMQConstant.NOT_ALLOWED, "Exchange in use as an alternate exchange");
        }
        catch (RequiredExchangeException e)
        {
            closeChannel(AMQConstant.NOT_ALLOWED,
                         "Exchange '" + exchangeStr + "' cannot be deleted");
        }
        catch (AccessControlException e)
        {
            _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());
        }
    }

    @Override
    public void receiveQueueBind(final AMQShortString queueName,
                                 final AMQShortString exchange,
                                 AMQShortString routingKey,
                                 final boolean nowait,
                                 final FieldTable argumentsTable)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] QueueBind[" +" queue: " + queueName +
                          " exchange: " + exchange +
                          " bindingKey: " + routingKey +
                          " nowait: " + nowait + " arguments: " + argumentsTable + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        AMQQueue<?> queue;
        if (queueName == null)
        {

            queue = getDefaultQueue();

            if (queue != null)
            {
                if (routingKey == null)
                {
                    routingKey = AMQShortString.valueOf(queue.getName());
                }
                else
                {
                    routingKey = routingKey.intern();
                }
            }
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
            routingKey = routingKey == null ? AMQShortString.EMPTY_STRING : routingKey.intern();
        }

        if (queue == null)
        {
            String message = queueName == null
                    ? "No default queue defined on channel and queue was null"
                    : "Queue " + queueName + " does not exist.";
                closeChannel(AMQConstant.NOT_FOUND, message);
        }
        else if (isDefaultExchange(exchange))
        {
            _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                        "Cannot bind the queue '" + queueName + "' to the default exchange", getChannelId());

        }
        else
        {

            final String exchangeName = exchange.toString();

            final ExchangeImpl exch = virtualHost.getExchange(exchangeName);
            if (exch == null)
            {
                closeChannel(AMQConstant.NOT_FOUND,
                             "Exchange '" + exchangeName + "' does not exist.");
            }
            else
            {

                try
                {

                    Map<String, Object> arguments = FieldTable.convertToMap(argumentsTable);
                    String bindingKey = String.valueOf(routingKey);

                    if (!exch.isBound(bindingKey, arguments, queue))
                    {

                        if (!exch.addBinding(bindingKey, queue, arguments)
                            && ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(
                                exch.getType()))
                        {
                            exch.replaceBinding(bindingKey, queue, arguments);
                        }
                    }

                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Binding queue "
                                     + queue
                                     + " to exchange "
                                     + exch
                                     + " with routing key "
                                     + routingKey);
                    }
                    if (!nowait)
                    {
                        sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createQueueBindOkBody();
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                    }
                }
                catch (AccessControlException e)
                {
                    _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());
                }
            }
        }
    }

    @Override
    public void receiveQueueDeclare(final AMQShortString queueStr,
                                    final boolean passive,
                                    final boolean durable,
                                    final boolean exclusive,
                                    final boolean autoDelete,
                                    final boolean nowait,
                                    final FieldTable arguments)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] QueueDeclare[" +" queue: " + queueStr +
                          " passive: " + passive +
                          " durable: " + durable +
                          " exclusive: " + exclusive +
                          " autoDelete: " + autoDelete + " nowait: " + nowait + " arguments: " + arguments + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        final AMQShortString queueName;

        // if we aren't given a queue name, we create one which we return to the client
        if ((queueStr == null) || (queueStr.length() == 0))
        {
            queueName = new AMQShortString("tmp_" + UUID.randomUUID());
        }
        else
        {
            queueName = queueStr.intern();
        }

        AMQQueue<?> queue;

        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?


        if (passive)
        {
            queue = virtualHost.getQueue(queueName.toString());
            if (queue == null)
            {
                closeChannel(AMQConstant.NOT_FOUND,
                                                     "Queue: '"
                                                     + queueName
                                                     + "' not found on VirtualHost '"
                                                     + virtualHost.getName()
                                                     + "'.");
            }
            else
            {
                if (!queue.verifySessionAccess(this))
                {
                    _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Queue '"
                                                + queue.getName()
                                                + "' is exclusive, but not created on this Connection.", getChannelId());
                }
                else
                {
                    //set this as the default queue on the channel:
                    setDefaultQueue(queue);
                    if (!nowait)
                    {
                        sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeclareOkBody responseBody =
                                methodRegistry.createQueueDeclareOkBody(queueName,
                                                                        queue.getQueueDepthMessages(),
                                                                        queue.getConsumerCount());
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                        _logger.info("Queue " + queueName + " declared successfully");
                    }
                }
            }
        }
        else
        {

            try
            {
                Map<String, Object> attributes =
                        QueueArgumentsConverter.convertWireArgsToModel(FieldTable.convertToMap(arguments));
                final String queueNameString = AMQShortString.toString(queueName);
                attributes.put(Queue.NAME, queueNameString);
                attributes.put(Queue.DURABLE, durable);

                LifetimePolicy lifetimePolicy;
                ExclusivityPolicy exclusivityPolicy;

                if (exclusive)
                {
                    lifetimePolicy = autoDelete
                            ? LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS
                            : durable ? LifetimePolicy.PERMANENT : LifetimePolicy.DELETE_ON_CONNECTION_CLOSE;
                    exclusivityPolicy = durable ? ExclusivityPolicy.CONTAINER : ExclusivityPolicy.CONNECTION;
                }
                else
                {
                    lifetimePolicy = autoDelete ? LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS : LifetimePolicy.PERMANENT;
                    exclusivityPolicy = ExclusivityPolicy.NONE;
                }

                attributes.put(Queue.EXCLUSIVE, exclusivityPolicy);
                attributes.put(Queue.LIFETIME_POLICY, lifetimePolicy);


                queue = virtualHost.createQueue(attributes);

                setDefaultQueue(queue);

                if (!nowait)
                {
                    sync();
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    QueueDeclareOkBody responseBody =
                            methodRegistry.createQueueDeclareOkBody(queueName,
                                                                    queue.getQueueDepthMessages(),
                                                                    queue.getConsumerCount());
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                    _logger.info("Queue " + queueName + " declared successfully");
                }
            }
            catch (QueueExistsException qe)
            {

                queue = qe.getExistingQueue();

                if (!queue.verifySessionAccess(this))
                {
                    _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Queue '"
                                                + queue.getName()
                                                + "' is exclusive, but not created on this Connection.", getChannelId());

                }
                else if (queue.isExclusive() != exclusive)
                {

                    closeChannel(AMQConstant.ALREADY_EXISTS,
                                                         "Cannot re-declare queue '"
                                                         + queue.getName()
                                                         + "' with different exclusivity (was: "
                                                         + queue.isExclusive()
                                                         + " requested "
                                                         + exclusive
                                                         + ")");
                }
                else if ((autoDelete
                          && queue.getLifetimePolicy() != LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS)
                         || (!autoDelete && queue.getLifetimePolicy() != ((exclusive
                                                                           && !durable)
                        ? LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                        : LifetimePolicy.PERMANENT)))
                {
                    closeChannel(AMQConstant.ALREADY_EXISTS,
                                                         "Cannot re-declare queue '"
                                                         + queue.getName()
                                                         + "' with different lifetime policy (was: "
                                                         + queue.getLifetimePolicy()
                                                         + " requested autodelete: "
                                                         + autoDelete
                                                         + ")");
                }
                else
                {
                    setDefaultQueue(queue);
                    if (!nowait)
                    {
                        sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeclareOkBody responseBody =
                                methodRegistry.createQueueDeclareOkBody(queueName,
                                                                        queue.getQueueDepthMessages(),
                                                                        queue.getConsumerCount());
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                        _logger.info("Queue " + queueName + " declared successfully");
                    }
                }
            }
            catch (AccessControlException e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());
            }

        }
    }

    @Override
    public void receiveQueueDelete(final AMQShortString queueName,
                                   final boolean ifUnused,
                                   final boolean ifEmpty,
                                   final boolean nowait)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] QueueDelete[" +" queue: " + queueName + " ifUnused: " + ifUnused + " ifEmpty: " + ifEmpty + " nowait: " + nowait + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        sync();
        AMQQueue queue;
        if (queueName == null)
        {

            //get the default queue on the channel:
            queue = getDefaultQueue();
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
        }

        if (queue == null)
        {
            closeChannel(AMQConstant.NOT_FOUND, "Queue '" + queueName + "' does not exist.");

        }
        else
        {
            if (ifEmpty && !queue.isEmpty())
            {
                closeChannel(AMQConstant.IN_USE, "Queue: '" + queueName + "' is not empty.");
            }
            else if (ifUnused && !queue.isUnused())
            {
                // TODO - Error code
                closeChannel(AMQConstant.IN_USE, "Queue: '" + queueName + "' is still used.");
            }
            else
            {
                if (!queue.verifySessionAccess(this))
                {
                    _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Queue '"
                                                + queue.getName()
                                                + "' is exclusive, but not created on this Connection.", getChannelId());

                }
                else
                {
                    try
                    {
                        int purged = virtualHost.removeQueue(queue);

                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeleteOkBody responseBody = methodRegistry.createQueueDeleteOkBody(purged);
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                    }
                    catch (AccessControlException e)
                    {
                        _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());

                    }
                }
            }
        }
    }

    @Override
    public void receiveQueuePurge(final AMQShortString queueName, final boolean nowait)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] QueuePurge[" +" queue: " + queueName + " nowait: " + nowait + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        AMQQueue queue = null;
        if (queueName == null && (queue = getDefaultQueue()) == null)
        {

            _connection.closeConnection(AMQConstant.NOT_ALLOWED, "No queue specified.", getChannelId());
        }
        else if ((queueName != null) && (queue = virtualHost.getQueue(queueName.toString())) == null)
        {
            closeChannel(AMQConstant.NOT_FOUND, "Queue '" + queueName + "' does not exist.");
        }
        else if (!queue.verifySessionAccess(this))
        {
            _connection.closeConnection(AMQConstant.NOT_ALLOWED,
                                        "Queue is exclusive, but not created on this Connection.", getChannelId());
        }
        else
        {
            try
            {
                long purged = queue.clearQueue();
                if (!nowait)
                {
                    sync();
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createQueuePurgeOkBody(purged);
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                }
            }
            catch (AccessControlException e)
            {
                _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());

            }

        }
    }

    @Override
    public void receiveQueueUnbind(final AMQShortString queueName,
                                   final AMQShortString exchange,
                                   final AMQShortString bindingKey,
                                   final FieldTable arguments)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] QueueUnbind[" +" queue: " + queueName +
                          " exchange: " + exchange +
                          " bindingKey: " + bindingKey +
                          " arguments: " + arguments + " ]");
        }

        VirtualHostImpl virtualHost = _connection.getVirtualHost();


        final boolean useDefaultQueue = queueName == null;
        final AMQQueue queue = useDefaultQueue
                ? getDefaultQueue()
                : virtualHost.getQueue(queueName.toString());


        if (queue == null)
        {
            String message = useDefaultQueue
                    ? "No default queue defined on channel and queue was null"
                    : "Queue '" + queueName + "' does not exist.";
            closeChannel(AMQConstant.NOT_FOUND, message);
        }
        else if (isDefaultExchange(exchange))
        {
            _connection.closeConnection(AMQConstant.NOT_ALLOWED, "Cannot unbind the queue '"
                                                         + queue.getName()
                                                         + "' from the default exchange", getChannelId());

        }
        else
        {

            final ExchangeImpl exch = virtualHost.getExchange(exchange.toString());

            if (exch == null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "Exchange '" + exchange + "' does not exist.");
            }
            else if (!exch.hasBinding(String.valueOf(bindingKey), queue))
            {
                closeChannel(AMQConstant.NOT_FOUND, "No such binding");
            }
            else
            {
                try
                {
                    exch.deleteBinding(String.valueOf(bindingKey), queue);

                    final AMQMethodBody responseBody = _connection.getMethodRegistry().createQueueUnbindOkBody();
                    sync();
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }
                catch (AccessControlException e)
                {
                    _connection.closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), getChannelId());

                }
            }

        }
    }

    @Override
    public void receiveTxSelect()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] TxSelect");
        }

        setLocalTransactional();

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        TxSelectOkBody responseBody = methodRegistry.createTxSelectOkBody();
        _connection.writeFrame(responseBody.generateFrame(_channelId));

    }

    @Override
    public void receiveTxCommit()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] TxCommit");
        }


        if (!isTransactional())
        {
            closeChannel(AMQConstant.COMMAND_INVALID,
                         "Fatal error: commit called on non-transactional channel");
        }
        commit(new Runnable()
        {

            @Override
            public void run()
            {
                MethodRegistry methodRegistry = _connection.getMethodRegistry();
                AMQMethodBody responseBody = methodRegistry.createTxCommitOkBody();
                _connection.writeFrame(responseBody.generateFrame(_channelId));
            }
        }, true);

    }

    @Override
    public void receiveTxRollback()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] TxRollback");
        }

        if (!isTransactional())
        {
            closeChannel(AMQConstant.COMMAND_INVALID,
                         "Fatal error: rollback called on non-transactional channel");
        }

        final MethodRegistry methodRegistry = _connection.getMethodRegistry();
        final AMQMethodBody responseBody = methodRegistry.createTxRollbackOkBody();

        Runnable task = new Runnable()
        {

            public void run()
            {
                _connection.writeFrame(responseBody.generateFrame(_channelId));
            }
        };

        rollback(task);

        //Now resend all the unacknowledged messages back to the original subscribers.
        //(Must be done after the TxnRollback-ok response).
        // Why, are we not allowed to send messages back to client before the ok method?
        resend();
    }

    @Override
    public void receiveConfirmSelect(final boolean nowait)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + _channelId + "] ConfirmSelect [ nowait: " + nowait + " ]");
        }
        _confirmOnPublish = true;

        if(!nowait)
        {
            _connection.writeFrame(new AMQFrame(_channelId, ConfirmSelectOkBody.INSTANCE));
        }
    }


    private void closeChannel(final AMQConstant cause, final String message)
    {
        _connection.closeChannelAndWriteFrame(this, cause, message);
    }


    private boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || AMQShortString.EMPTY_STRING.equals(exchangeName);
    }

    private void setDefaultQueue(AMQQueue<?> queue)
    {
        AMQQueue<?> currentDefaultQueue = _defaultQueue;
        if (queue != currentDefaultQueue)
        {
            if (currentDefaultQueue != null)
            {
                currentDefaultQueue.removeDeleteTask(_defaultQueueAssociationClearingTask);
            }
            if (queue != null)
            {
                queue.addDeleteTask(_defaultQueueAssociationClearingTask);
            }
        }
        _defaultQueue = queue;
    }

    private AMQQueue getDefaultQueue()
    {
        return _defaultQueue;
    }

    private class DefaultQueueAssociationClearingTask implements Action<AMQQueue>
    {
        @Override
        public void performAction(final AMQQueue queue)
        {
            if ( queue == _defaultQueue)
            {
                _defaultQueue = null;
            }
        }
    }
}
