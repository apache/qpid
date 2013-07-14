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
package org.apache.qpid.server.protocol.v1_0;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.qpid.AMQException;
import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoderImpl;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Modified;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.qpid.amqp_1_0.type.messaging.Released;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.typedmessage.TypedBytesContentReader;
import org.apache.qpid.typedmessage.TypedBytesFormatException;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.message.MessageMetaData_1_0;
import org.apache.qpid.server.protocol.v0_10.MessageTransferMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;

class Subscription_1_0 implements Subscription
{
    private SendingLink_1_0 _link;

    private AMQQueue _queue;

    private final AtomicReference<State> _state = new AtomicReference<State>(State.SUSPENDED);

    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final long _id;
    private final boolean _acquires;
    private volatile AMQQueue.Context _queueContext;
    private Map<String, Object> _properties = new ConcurrentHashMap<String, Object>();
    private ReentrantLock _stateChangeLock = new ReentrantLock();

    private boolean _noLocal;
    private FilterManager _filters;

    private long _deliveryTag = 0L;
    private StateListener _stateListener;

    private Binary _transactionId;
    private final AMQPDescribedTypeRegistry _typeRegistry = AMQPDescribedTypeRegistry.newInstance()
                                                                                     .registerTransportLayer()
                                                                                     .registerMessagingLayer()
                                                                                     .registerTransactionLayer()
                                                                                     .registerSecurityLayer();
    private SectionEncoder _sectionEncoder = new SectionEncoderImpl(_typeRegistry);

    public Subscription_1_0(final SendingLink_1_0 link, final QueueDestination destination)
    {
        this(link, destination, ((Source)link.getEndpoint().getSource()).getDistributionMode() != StdDistMode.COPY);
    }

    public Subscription_1_0(final SendingLink_1_0 link, final QueueDestination destination, boolean acquires)
    {
        _link = link;
        _queue = destination.getQueue();
        _id = getEndpoint().getLocalHandle().longValue();
        _acquires = acquires;
    }

    private SendingLinkEndpoint getEndpoint()
    {
        return _link.getEndpoint();
    }

    public LogActor getLogActor()
    {
        return null;  //TODO
    }

    public boolean isTransient()
    {
        return true;  //TODO
    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

    public void setQueue(final AMQQueue queue, final boolean exclusive)
    {
        //TODO
    }

    public void setNoLocal(final boolean noLocal)
    {
        _noLocal = noLocal;
    }

    public long getSubscriptionID()
    {
        return _id;
    }

    public boolean isSuspended()
    {
        return _link.getSession().getConnectionModel().isStopped() || !isActive();// || !getEndpoint().hasCreditToSend();

    }

    public boolean hasInterest(final QueueEntry entry)
    {
        return !(_noLocal && (entry.getMessage() instanceof Message_1_0)
                          && ((Message_1_0)entry.getMessage()).getSession() == getSession())
               && checkFilters(entry);

    }

    private boolean checkFilters(final QueueEntry entry)
    {
        return (_filters == null) || _filters.allAllow(entry);
    }

    public boolean isClosed()
    {
        return !getEndpoint().isAttached();
    }

    public boolean acquires()
    {
        return _acquires;
    }

    public boolean seesRequeues()
    {
        // TODO
        return acquires();
    }

    public void close()
    {
        getEndpoint().detach();
    }

    public void send(QueueEntry entry, boolean batch) throws AMQException
    {
        // TODO
        send(entry);
    }

    public void flushBatched()
    {
        // TODO
    }

    public void send(final QueueEntry queueEntry) throws AMQException
    {
        ServerMessage serverMessage = queueEntry.getMessage();
        Message_1_0 message;
        if(serverMessage instanceof Message_1_0)
        {
            message = (Message_1_0) serverMessage;
        }
        else
        {
            if(serverMessage instanceof AMQMessage)
            {
                message = new Message_1_0(convert08Message((AMQMessage)serverMessage));
            }
            else if(serverMessage instanceof MessageTransferMessage)
            {
                message = new Message_1_0(convert010Message((MessageTransferMessage)serverMessage));
            }
            else
            {
                return;
            }
        }

        Transfer transfer = new Transfer();
        //TODO


        List<ByteBuffer> fragments = message.getFragments();
        ByteBuffer payload;
        if(fragments.size() == 1)
        {
            payload = fragments.get(0);
        }
        else
        {
            int size = 0;
            for(ByteBuffer fragment : fragments)
            {
                size += fragment.remaining();
            }

            payload = ByteBuffer.allocate(size);

            for(ByteBuffer fragment : fragments)
            {
                payload.put(fragment.duplicate());
            }

            payload.flip();
        }

        if(queueEntry.getDeliveryCount() != 0)
        {
            payload = payload.duplicate();
            ValueHandler valueHandler = new ValueHandler(_typeRegistry);

            Header oldHeader = null;
            try
            {
                ByteBuffer encodedBuf = payload.duplicate();
                Object value = valueHandler.parse(payload);
                if(value instanceof Header)
                {
                    oldHeader = (Header) value;
                }
                else
                {
                    payload.position(0);
                }
            }
            catch (AmqpErrorException e)
            {
                //TODO
                throw new RuntimeException(e);
            }

            Header header = new Header();
            if(oldHeader != null)
            {
                header.setDurable(oldHeader.getDurable());
                header.setPriority(oldHeader.getPriority());
                header.setTtl(oldHeader.getTtl());
            }
            header.setDeliveryCount(UnsignedInteger.valueOf(queueEntry.getDeliveryCount()));
            _sectionEncoder.reset();
            _sectionEncoder.encodeObject(header);
            Binary encodedHeader = _sectionEncoder.getEncoding();

            ByteBuffer oldPayload = payload;
            payload = ByteBuffer.allocate(oldPayload.remaining() + encodedHeader.getLength());
            payload.put(encodedHeader.getArray(),encodedHeader.getArrayOffset(),encodedHeader.getLength());
            payload.put(oldPayload);
            payload.flip();
        }

        transfer.setPayload(payload);
        byte[] data = new byte[8];
        ByteBuffer.wrap(data).putLong(_deliveryTag++);
        final Binary tag = new Binary(data);

        transfer.setDeliveryTag(tag);

        synchronized(_link.getLock())
        {
            if(_link.isAttached())
            {
                if(SenderSettleMode.SETTLED.equals(getEndpoint().getSendingSettlementMode()))
                {
                    transfer.setSettled(true);
                }
                else
                {
                    UnsettledAction action = _acquires
                                             ? new DispositionAction(tag, queueEntry)
                                             : new DoNothingAction(tag, queueEntry);

                    _link.addUnsettled(tag, action, queueEntry);
                }

                if(_transactionId != null)
                {
                    TransactionalState state = new TransactionalState();
                    state.setTxnId(_transactionId);
                    transfer.setState(state);
                }
                // TODO - need to deal with failure here
                if(_acquires && _transactionId != null)
                {
                    ServerTransaction txn = _link.getTransaction(_transactionId);
                    if(txn != null)
                    {
                        txn.addPostTransactionAction(new ServerTransaction.Action(){

                            public void postCommit()
                            {
                                //To change body of implemented methods use File | Settings | File Templates.
                            }

                            public void onRollback()
                            {
                                if(queueEntry.isAcquiredBy(Subscription_1_0.this))
                                {
                                    queueEntry.release();
                                    _link.getEndpoint().updateDisposition(tag, (DeliveryState)null, true);


                                }
                            }
                        });
                    }

                }
                getSession().getConnectionModel().registerMessageDelivered(message.getSize());
                getEndpoint().transfer(transfer);
            }
            else
            {
                queueEntry.release();
            }
        }

    }

    private StoredMessage<MessageMetaData_1_0> convert010Message(final MessageTransferMessage serverMessage)
    {
        final MessageMetaData_1_0 metaData = convertMetaData(serverMessage);

        return convertServerMessage(metaData, serverMessage);

    }

    private MessageMetaData_1_0 convertMetaData(final MessageTransferMessage serverMessage)
    {
        List<Section> sections = new ArrayList<Section>(3);
        final MessageProperties msgProps = serverMessage.getHeader().getMessageProperties();
        final DeliveryProperties deliveryProps = serverMessage.getHeader().getDeliveryProperties();

        Header header = new Header();
        if(deliveryProps != null)
        {
            header.setDurable(deliveryProps.hasDeliveryMode() && deliveryProps.getDeliveryMode() == MessageDeliveryMode.PERSISTENT);
            if(deliveryProps.hasPriority())
            {
                header.setPriority(UnsignedByte.valueOf((byte)deliveryProps.getPriority().getValue()));
            }
            if(deliveryProps.hasTtl())
            {
                header.setTtl(UnsignedInteger.valueOf(deliveryProps.getTtl()));
            }
            sections.add(header);
        }

        Properties props = new Properties();
        if(msgProps != null)
        {
        //        props.setAbsoluteExpiryTime();
            if(msgProps.hasContentEncoding())
            {
                props.setContentEncoding(Symbol.valueOf(msgProps.getContentEncoding()));
            }

            if(msgProps.hasCorrelationId())
            {
                props.setCorrelationId(msgProps.getCorrelationId());
            }
        //        props.setCreationTime();
        //        props.setGroupId();
        //        props.setGroupSequence();
            if(msgProps.hasMessageId())
            {
                props.setMessageId(msgProps.getMessageId());
            }
            if(msgProps.hasReplyTo())
            {
                props.setReplyTo(msgProps.getReplyTo().getExchange()+"/"+msgProps.getReplyTo().getRoutingKey());
            }
            if(msgProps.hasContentType())
            {
                props.setContentType(Symbol.valueOf(msgProps.getContentType()));

                // Modify the content type when we are dealing with java object messages produced by the Qpid 0.x client
                if(props.getContentType() == Symbol.valueOf("application/java-object-stream"))
                {
                    props.setContentType(Symbol.valueOf("application/x-java-serialized-object"));
                }
            }
        //        props.setReplyToGroupId();
            props.setSubject(serverMessage.getRoutingKey());
        //        props.setTo();
            if(msgProps.hasUserId())
            {
                props.setUserId(new Binary(msgProps.getUserId()));
            }

            sections.add(props);

            if(msgProps.getApplicationHeaders() != null)
            {
                sections.add(new ApplicationProperties(msgProps.getApplicationHeaders()));
            }
        }
        return new MessageMetaData_1_0(sections, _sectionEncoder);
    }

    private StoredMessage<MessageMetaData_1_0> convert08Message(final AMQMessage serverMessage)
    {
        final MessageMetaData_1_0 metaData = convertMetaData(serverMessage);

        return convertServerMessage(metaData, serverMessage);


    }

    private StoredMessage<MessageMetaData_1_0> convertServerMessage(final MessageMetaData_1_0 metaData,
                                                                    final ServerMessage serverMessage)
    {
        final String mimeType = serverMessage.getMessageHeader().getMimeType();
        byte[] data = new byte[(int) serverMessage.getSize()];
        serverMessage.getContent(ByteBuffer.wrap(data), 0);

        Section bodySection = convertMessageBody(mimeType, data);

        final ByteBuffer allData = encodeConvertedMessage(metaData, bodySection);

        return new StoredMessage<MessageMetaData_1_0>()
        {
            @Override
            public MessageMetaData_1_0 getMetaData()
            {
                return metaData;
            }

            @Override
            public long getMessageNumber()
            {
                return serverMessage.getMessageNumber();
            }

            @Override
            public void addContent(int offsetInMessage, ByteBuffer src)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getContent(int offsetInMessage, ByteBuffer dst)
            {
                ByteBuffer buf = allData.duplicate();
                buf.position(offsetInMessage);
                buf = buf.slice();
                int size;
                if(dst.remaining()<buf.remaining())
                {
                    buf.limit(dst.remaining());
                    size = dst.remaining();
                }
                else
                {
                    size = buf.remaining();
                }
                dst.put(buf);
                return size;
            }

            @Override
            public ByteBuffer getContent(int offsetInMessage, int size)
            {
                ByteBuffer buf = allData.duplicate();
                buf.position(offsetInMessage);
                buf = buf.slice();
                if(size < buf.remaining())
                {
                    buf.limit(size);
                }
                return buf;
            }

            @Override
            public StoreFuture flushToStore()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void remove()
            {
                serverMessage.getStoredMessage().remove();
            }
        };
    }

    private ByteBuffer encodeConvertedMessage(MessageMetaData_1_0 metaData, Section bodySection)
    {
        int headerSize = (int) metaData.getStorableSize();

        _sectionEncoder.reset();
        _sectionEncoder.encodeObject(bodySection);
        Binary dataEncoding = _sectionEncoder.getEncoding();

        final ByteBuffer allData = ByteBuffer.allocate(headerSize + dataEncoding.getLength());
        metaData.writeToBuffer(0,allData);
        allData.put(dataEncoding.getArray(),dataEncoding.getArrayOffset(),dataEncoding.getLength());
        return allData;
    }

    private static Section convertMessageBody(String mimeType, byte[] data)
    {
        if("text/plain".equals(mimeType) || "text/xml".equals(mimeType))
        {
            String text = new String(data);
            return new AmqpValue(text);
        }
        else if("jms/map-message".equals(mimeType))
        {
            TypedBytesContentReader reader = new TypedBytesContentReader(ByteBuffer.wrap(data));

            LinkedHashMap map = new LinkedHashMap();
            final int entries = reader.readIntImpl();
            for (int i = 0; i < entries; i++)
            {
                try
                {
                    String propName = reader.readStringImpl();
                    Object value = reader.readObject();
                    map.put(propName, value);
                }
                catch (EOFException e)
                {
                    throw new IllegalArgumentException(e);
                }
                catch (TypedBytesFormatException e)
                {
                    throw new IllegalArgumentException(e);
                }

            }

            return new AmqpValue(map);

        }
        else if("amqp/map".equals(mimeType))
        {
            BBDecoder decoder = new BBDecoder();
            decoder.init(ByteBuffer.wrap(data));
            return new AmqpValue(decoder.readMap());

        }
        else if("amqp/list".equals(mimeType))
        {
            BBDecoder decoder = new BBDecoder();
            decoder.init(ByteBuffer.wrap(data));
            return new AmqpValue(decoder.readList());
        }
        else if("jms/stream-message".equals(mimeType))
        {
            TypedBytesContentReader reader = new TypedBytesContentReader(ByteBuffer.wrap(data));

            List list = new ArrayList();
            while (reader.remaining() != 0)
            {
                try
                {
                    list.add(reader.readObject());
                }
                catch (TypedBytesFormatException e)
                {
                    throw new RuntimeException(e);  // TODO - Implement
                }
                catch (EOFException e)
                {
                    throw new RuntimeException(e);  // TODO - Implement
                }
            }
            return new AmqpValue(list);
        }
        else
        {
            return new Data(new Binary(data));

        }
    }

    private MessageMetaData_1_0 convertMetaData(final AMQMessage serverMessage)
    {

        List<Section> sections = new ArrayList<Section>(3);

        Header header = new Header();

        header.setDurable(serverMessage.isPersistent());

        BasicContentHeaderProperties contentHeader =
                (BasicContentHeaderProperties) serverMessage.getContentHeaderBody().getProperties();

        header.setPriority(UnsignedByte.valueOf(contentHeader.getPriority()));
        final long expiration = serverMessage.getExpiration();
        final long arrivalTime = serverMessage.getArrivalTime();

        if(expiration > arrivalTime)
        {
            header.setTtl(UnsignedInteger.valueOf(expiration - arrivalTime));
        }
        sections.add(header);


        Properties props = new Properties();

        props.setContentEncoding(Symbol.valueOf(contentHeader.getEncodingAsString()));

        props.setContentType(Symbol.valueOf(contentHeader.getContentTypeAsString()));

        // Modify the content type when we are dealing with java object messages produced by the Qpid 0.x client
        if(props.getContentType() == Symbol.valueOf("application/java-object-stream"))
        {
            props.setContentType(Symbol.valueOf("application/x-java-serialized-object"));
        }

        final AMQShortString correlationId = contentHeader.getCorrelationId();
        if(correlationId != null)
        {
            props.setCorrelationId(new Binary(correlationId.getBytes()));
        }
        //        props.setCreationTime();
        //        props.setGroupId();
        //        props.setGroupSequence();
        final AMQShortString messageId = contentHeader.getMessageId();
        if(messageId != null)
        {
            props.setMessageId(new Binary(messageId.getBytes()));
        }
        props.setReplyTo(String.valueOf(contentHeader.getReplyTo()));

        //        props.setReplyToGroupId();
        props.setSubject(serverMessage.getRoutingKey());
        //        props.setTo();
        if(contentHeader.getUserId() != null)
        {
            props.setUserId(new Binary(contentHeader.getUserId().getBytes()));
        }
        sections.add(props);

        sections.add(new ApplicationProperties(FieldTable.convertToMap(contentHeader.getHeaders())));

        return new MessageMetaData_1_0(sections, _sectionEncoder);
    }

    public void queueDeleted(final AMQQueue queue)
    {
        //TODO
        getEndpoint().setSource(null);
        getEndpoint().detach();
    }

    public boolean wouldSuspend(final QueueEntry msg)
    {
        synchronized (_link.getLock())
        {
            final boolean hasCredit = _link.isAttached() && getEndpoint().hasCreditToSend();
            if(!hasCredit && getState() == State.ACTIVE)
            {
                suspend();
            }

            return !hasCredit;
        }
    }

    public boolean trySendLock()
    {
        return _stateChangeLock.tryLock();
    }

    public void suspend()
    {
        synchronized(_link.getLock())
        {
            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
        }
    }

    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void releaseQueueEntry(QueueEntry queueEntryImpl)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public void onDequeue(final QueueEntry queueEntry)
    {
        //TODO
    }

    public void restoreCredit(final QueueEntry queueEntry)
    {
        //TODO
    }

    public void setStateListener(final StateListener listener)
    {
        _stateListener = listener;
    }

    public State getState()
    {
        return _state.get();
    }

    public AMQQueue.Context getQueueContext()
    {
        return _queueContext;
    }

    public void setQueueContext(AMQQueue.Context queueContext)
    {
        _queueContext = queueContext;
    }


    public boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public void set(String key, Object value)
    {
        _properties.put(key, value);
    }

    public Object get(String key)
    {
        return _properties.get(key);
    }

    public boolean isSessionTransactional()
    {
        return false;  //TODO
    }

    public void queueEmpty()
    {
        synchronized(_link.getLock())
        {
            if(_link.drained())
            {
                if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
                {
                    _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
                }
            }
        }
    }

    public void flowStateChanged()
    {
        synchronized(_link.getLock())
        {
            if(isSuspended() && getEndpoint() != null)
            {
                if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
                {
                    _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
                }
                _transactionId = _link.getTransactionId();
            }
        }
    }

    public Session_1_0 getSession()
    {
        return _link.getSession();
    }

    private class DispositionAction implements UnsettledAction
    {

        private final QueueEntry _queueEntry;
        private final Binary _deliveryTag;

        public DispositionAction(Binary tag, QueueEntry queueEntry)
        {
            _deliveryTag = tag;
            _queueEntry = queueEntry;
        }

        public boolean process(DeliveryState state, final Boolean settled)
        {

            Binary transactionId = null;
            final Outcome outcome;
            // If disposition is settled this overrides the txn?
            if(state instanceof TransactionalState)
            {
                transactionId = ((TransactionalState)state).getTxnId();
                outcome = ((TransactionalState)state).getOutcome();
            }
            else if (state instanceof Outcome)
            {
                outcome = (Outcome) state;
            }
            else
            {
                outcome = null;
            }


            ServerTransaction txn = _link.getTransaction(transactionId);

            if(outcome instanceof Accepted)
            {
                txn.dequeue(_queueEntry.getQueue(), _queueEntry.getMessage(),
                        new ServerTransaction.Action()
                        {

                            public void postCommit()
                            {
                                if(_queueEntry.isAcquiredBy(Subscription_1_0.this))
                                {
                                    _queueEntry.discard();
                                }
                            }

                            public void onRollback()
                            {

                            }
                        });
                txn.addPostTransactionAction(new ServerTransaction.Action()
                    {
                        public void postCommit()
                        {
                            //_link.getEndpoint().settle(_deliveryTag);
                            _link.getEndpoint().updateDisposition(_deliveryTag, (DeliveryState)outcome, true);
                            _link.getEndpoint().sendFlowConditional();
                        }

                        public void onRollback()
                        {
                            if(Boolean.TRUE.equals(settled))
                            {
                                final Modified modified = new Modified();
                                modified.setDeliveryFailed(true);
                                _link.getEndpoint().updateDisposition(_deliveryTag, modified, true);
                                _link.getEndpoint().sendFlowConditional();
                            }
                        }
                    });
            }
            else if(outcome instanceof Released)
            {
                txn.addPostTransactionAction(new ServerTransaction.Action()
                {
                    public void postCommit()
                    {

                        _queueEntry.release();
                        _link.getEndpoint().settle(_deliveryTag);
                    }

                    public void onRollback()
                    {
                        _link.getEndpoint().settle(_deliveryTag);
                    }
                });
            }

            else if(outcome instanceof Modified)
            {
                txn.addPostTransactionAction(new ServerTransaction.Action()
                {
                    public void postCommit()
                    {

                        _queueEntry.release();
                        if(Boolean.TRUE.equals(((Modified)outcome).getDeliveryFailed()))
                        {
                            _queueEntry.incrementDeliveryCount();
                        }
                        _link.getEndpoint().settle(_deliveryTag);
                    }

                    public void onRollback()
                    {
                        if(Boolean.TRUE.equals(settled))
                        {
                            final Modified modified = new Modified();
                            modified.setDeliveryFailed(true);
                            _link.getEndpoint().updateDisposition(_deliveryTag, modified, true);
                            _link.getEndpoint().sendFlowConditional();
                        }
                    }
                });
            }

            return (transactionId == null && outcome != null);
        }
    }

    private class DoNothingAction implements UnsettledAction
    {
        public DoNothingAction(final Binary tag,
                               final QueueEntry queueEntry)
        {
        }

        public boolean process(final DeliveryState state, final Boolean settled)
        {
            Binary transactionId = null;
            Outcome outcome = null;
            // If disposition is settled this overrides the txn?
            if(state instanceof TransactionalState)
            {
                transactionId = ((TransactionalState)state).getTxnId();
                outcome = ((TransactionalState)state).getOutcome();
            }
            else if (state instanceof Outcome)
            {
                outcome = (Outcome) state;
            }
            return true;
        }
    }

    public FilterManager getFilters()
    {
        return _filters;
    }

    public void setFilters(final FilterManager filters)
    {
        _filters = filters;
    }

    @Override
    public AMQSessionModel getSessionModel()
    {
        // TODO
        return getSession();
    }

    @Override
    public long getBytesOut()
    {
        // TODO
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        // TODO
        return 0;
    }

    @Override
    public long getUnacknowledgedBytes()
    {
        // TODO
        return 0;
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        // TODO
        return 0;
    }

    @Override
    public String getConsumerName()
    {
        //TODO
        return "TODO";
    }
}
