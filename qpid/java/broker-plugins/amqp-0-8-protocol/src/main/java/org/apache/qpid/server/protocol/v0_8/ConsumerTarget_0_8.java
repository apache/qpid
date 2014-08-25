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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.consumer.AbstractConsumerTarget;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.v0_8.output.ProtocolOutputConverter;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.StateChangeListener;

/**
 * Encapsulation of a subscription to a queue.
 * <p>
 * Ties together the protocol session of a subscriber, the consumer tag
 * that was given out by the broker and the channel id.
 */
public abstract class ConsumerTarget_0_8 extends AbstractConsumerTarget implements FlowCreditManager.FlowCreditManagerListener
{

    private final StateChangeListener<MessageInstance, MessageInstance.State> _entryReleaseListener =
            new StateChangeListener<MessageInstance, MessageInstance.State>()
            {
                @Override
                public void stateChanged(final MessageInstance entry,
                                         final MessageInstance.State oldSate,
                                         final MessageInstance.State newState)
                {
                    if (oldSate == QueueEntry.State.ACQUIRED && newState != QueueEntry.State.ACQUIRED)
                    {
                        restoreCredit(entry.getMessage());
                    }
                    entry.removeStateChangeListener(this);
                }
            };

    private final ClientDeliveryMethod _deliveryMethod;
    private final RecordDeliveryMethod _recordMethod;

    private final AtomicLong _unacknowledgedCount = new AtomicLong(0);
    private final AtomicLong _unacknowledgedBytes = new AtomicLong(0);
    private ConsumerImpl _consumer;


    public static ConsumerTarget_0_8 createBrowserTarget(AMQChannel channel,
                                                         AMQShortString consumerTag, FieldTable filters,
                                                         FlowCreditManager creditManager) throws AMQException
    {
        return new BrowserConsumer(channel, consumerTag, filters, creditManager, channel.getClientDeliveryMethod(), channel.getRecordDeliveryMethod());
    }

    public static ConsumerTarget_0_8 createGetNoAckTarget(final AMQChannel channel,
                                                          final AMQShortString consumerTag,
                                                          final FieldTable filters,
                                                          final FlowCreditManager creditManager,
                                                          final ClientDeliveryMethod deliveryMethod,
                                                          final RecordDeliveryMethod recordMethod) throws AMQException
    {
        return new GetNoAckConsumer(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);
    }

    static final class BrowserConsumer extends ConsumerTarget_0_8
    {
        public BrowserConsumer(AMQChannel channel,
                               AMQShortString consumerTag, FieldTable filters,
                               FlowCreditManager creditManager,
                               ClientDeliveryMethod deliveryMethod,
                               RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, consumerTag,
                  filters, creditManager, deliveryMethod, recordMethod);
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         *
         * @param entry
         * @param batch
         * @throws org.apache.qpid.AMQException
         */
        @Override
        public long send(MessageInstance entry, boolean batch)
        {
            // We don't decrement the reference here as we don't want to consume the message
            // but we do want to send it to the client.

            synchronized (getChannel())
            {
                long deliveryTag = getChannel().getNextDeliveryTag();
                return sendToClient(entry.getMessage(), entry.getInstanceProperties(), deliveryTag);
            }

        }

        @Override
        public boolean allocateCredit(ServerMessage msg)
        {
            return true;
        }

    }

    public static ConsumerTarget_0_8 createNoAckTarget(AMQChannel channel,
                                                           AMQShortString consumerTag, FieldTable filters,
                                                           FlowCreditManager creditManager) throws AMQException
    {
        return new NoAckConsumer(channel, consumerTag, filters, creditManager, channel.getClientDeliveryMethod(), channel.getRecordDeliveryMethod());
    }

    public static ConsumerTarget_0_8 createNoAckTarget(AMQChannel channel,
                                                       AMQShortString consumerTag, FieldTable filters,
                                                       FlowCreditManager creditManager,
                                                       ClientDeliveryMethod deliveryMethod,
                                                       RecordDeliveryMethod recordMethod) throws AMQException
    {
        return new NoAckConsumer(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);
    }

    public static class NoAckConsumer extends ConsumerTarget_0_8
    {
        private final AutoCommitTransaction _txn;

        public NoAckConsumer(AMQChannel channel,
                             AMQShortString consumerTag, FieldTable filters,
                             FlowCreditManager creditManager,
                             ClientDeliveryMethod deliveryMethod,
                             RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);

            _txn = new AutoCommitTransaction(channel.getVirtualHost().getMessageStore());
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         * @param entry   The message to send
         * @param batch
         */
        @Override
        public long send(MessageInstance entry, boolean batch)
        {
            // if we do not need to wait for client acknowledgements
            // we can decrement the reference count immediately.

            // By doing this _before_ the send we ensure that it
            // doesn't get sent if it can't be dequeued, preventing
            // duplicate delivery on recovery.

            // The send may of course still fail, in which case, as
            // the message is unacked, it will be lost.
            _txn.dequeue(entry.getOwningResource(), entry.getMessage(), NOOP);

            ServerMessage message = entry.getMessage();
            MessageReference ref = message.newReference();
            InstanceProperties props = entry.getInstanceProperties();
            entry.delete();
            long size;
            synchronized (getChannel())
            {
                getChannel().getProtocolSession().setDeferFlush(batch);
                long deliveryTag = getChannel().getNextDeliveryTag();

                size = sendToClient(message, props, deliveryTag);

            }
            ref.release();
            return size;

        }

        @Override
        public boolean allocateCredit(ServerMessage msg)
        {
            return true;
        }

        private static final ServerTransaction.Action NOOP =
                new ServerTransaction.Action()
                {
                    @Override
                    public void postCommit()
                    {
                    }

                    @Override
                    public void onRollback()
                    {
                    }
                };
    }

    /**
     * NoAck Subscription for use with BasicGet method.
     */
    public static final class GetNoAckConsumer extends NoAckConsumer
    {
        public GetNoAckConsumer(AMQChannel channel,
                                AMQShortString consumerTag, FieldTable filters,
                                FlowCreditManager creditManager,
                                ClientDeliveryMethod deliveryMethod,
                                RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);
        }

        public boolean allocateCredit(ServerMessage msg)
        {
            return getCreditManager().useCreditForMessage(msg.getSize());
        }

    }


    public static ConsumerTarget_0_8 createAckTarget(AMQChannel channel,
                                                         AMQShortString consumerTag, FieldTable filters,
                                                         FlowCreditManager creditManager)
            throws AMQException
    {
        return new AckConsumer(channel,consumerTag,filters,creditManager, channel.getClientDeliveryMethod(), channel.getRecordDeliveryMethod());
    }


    public static ConsumerTarget_0_8 createAckTarget(AMQChannel channel,
                                                         AMQShortString consumerTag, FieldTable filters,
                                                         FlowCreditManager creditManager,
                                                         ClientDeliveryMethod deliveryMethod,
                                                         RecordDeliveryMethod recordMethod)
            throws AMQException
    {
        return new AckConsumer(channel,consumerTag,filters,creditManager, deliveryMethod, recordMethod);
    }

    static final class AckConsumer extends ConsumerTarget_0_8
    {
        public AckConsumer(AMQChannel channel,
                           AMQShortString consumerTag, FieldTable filters,
                           FlowCreditManager creditManager,
                           ClientDeliveryMethod deliveryMethod,
                           RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, consumerTag, filters, creditManager, deliveryMethod, recordMethod);
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         * @param entry   The message to send
         * @param batch
         */
        @Override
        public long send(MessageInstance entry, boolean batch)
        {


            synchronized (getChannel())
            {
                getChannel().getProtocolSession().setDeferFlush(batch);
                long deliveryTag = getChannel().getNextDeliveryTag();

                addUnacknowledgedMessage(entry);
                recordMessageDelivery(entry, deliveryTag);
                entry.addStateChangeListener(getReleasedStateChangeListener());
                long size = sendToClient(entry.getMessage(), entry.getInstanceProperties(), deliveryTag);
                entry.incrementDeliveryCount();
                return size;
            }
        }



    }


    private static final Logger _logger = Logger.getLogger(ConsumerTarget_0_8.class);

    private final AMQChannel _channel;

    private final AMQShortString _consumerTag;

    private final FlowCreditManager _creditManager;

    private final Boolean _autoClose;

    private final AtomicBoolean _deleted = new AtomicBoolean(false);




    public ConsumerTarget_0_8(AMQChannel channel,
                              AMQShortString consumerTag,
                              FieldTable arguments,
                              FlowCreditManager creditManager,
                              ClientDeliveryMethod deliveryMethod,
                              RecordDeliveryMethod recordMethod)
            throws AMQException
    {
        super(State.ACTIVE);

        _channel = channel;
        _consumerTag = consumerTag;

        _creditManager = creditManager;
        creditManager.addStateListener(this);

        _deliveryMethod = deliveryMethod;
        _recordMethod = recordMethod;

        if (arguments != null)
        {
            Object autoClose = arguments.get(AMQPFilterTypes.AUTO_CLOSE.getValue());
            if (autoClose != null)
            {
                _autoClose = (Boolean) autoClose;
            }
            else
            {
                _autoClose = false;
            }
        }
        else
        {
            _autoClose = false;
        }
    }

    public ConsumerImpl getConsumer()
    {
        return _consumer;
    }

    @Override
    public void consumerRemoved(final ConsumerImpl sub)
    {
    }

    @Override
    public void consumerAdded(final ConsumerImpl sub)
    {
        _consumer = sub;
    }

    public AMQSessionModel getSessionModel()
    {
        return _channel;
    }

    public String toString()
    {
        String subscriber = "[channel=" + _channel +
                            ", consumerTag=" + _consumerTag +
                            ", session=" + getProtocolSession().getKey()  ;

        return subscriber + "]";
    }

    public boolean isSuspended()
    {
        return getState()!=State.ACTIVE || _channel.isSuspended() || _deleted.get() || _channel.getConnectionModel().isStopped();
    }

    /**
     * Callback indicating that a queue has been deleted.
     *
     */
    public void queueDeleted()
    {
        _deleted.set(true);
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
    }


    public boolean close()
    {
        boolean closed = false;
        State state = getState();

        final ConsumerImpl consumer = getConsumer();

        if(consumer != null)
        {
            consumer.getSendLock();
        }
        try
        {
            while(!closed && state != State.CLOSED)
            {
                closed = updateState(state, State.CLOSED);
                if(!closed)
                {
                    state = getState();
                }
            }
            _creditManager.removeListener(this);
            return closed;
        }
        finally
        {
            if(consumer != null)
            {
                consumer.releaseSendLock();
            }
        }
    }


    public boolean allocateCredit(ServerMessage msg)
    {
        return _creditManager.useCreditForMessage(msg.getSize());
    }

    public AMQChannel getChannel()
    {
        return _channel;
    }

    public AMQShortString getConsumerTag()
    {
        return _consumerTag;
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _channel.getProtocolSession();
    }

    public void restoreCredit(final ServerMessage message)
    {
        _creditManager.restoreCredit(1, message.getSize());
    }

    protected final StateChangeListener<MessageInstance, MessageInstance.State> getReleasedStateChangeListener()
    {
        return _entryReleaseListener;
    }

    public void creditStateChanged(boolean hasCredit)
    {

        if(hasCredit)
        {
            if(!updateState(State.SUSPENDED, State.ACTIVE))
            {
                // this is a hack to get round the issue of increasing bytes credit
                getStateListener().stateChanged(this, State.ACTIVE, State.ACTIVE);
            }
        }
        else
        {
            updateState(State.ACTIVE, State.SUSPENDED);
        }
    }

    protected long sendToClient(final ServerMessage message, final InstanceProperties props, final long deliveryTag)
    {
        return _deliveryMethod.deliverToClient(getConsumer(), message, props, deliveryTag);

    }


    protected void recordMessageDelivery(final MessageInstance entry, final long deliveryTag)
    {
        _recordMethod.recordMessageDelivery(getConsumer(),entry,deliveryTag);
    }


    public void confirmAutoClose()
    {
        ProtocolOutputConverter converter = getChannel().getProtocolSession().getProtocolOutputConverter();
        converter.confirmConsumerAutoClose(getChannel().getChannelId(), getConsumerTag());
    }

    public void queueEmpty()
    {
        if (isAutoClose())
        {
            close();
            confirmAutoClose();
        }
    }

    public void flushBatched()
    {
        _channel.getProtocolSession().setDeferFlush(false);

        _channel.getProtocolSession().flushBatched();
    }

    protected void addUnacknowledgedMessage(MessageInstance entry)
    {
        final long size = entry.getMessage().getSize();
        _unacknowledgedBytes.addAndGet(size);
        _unacknowledgedCount.incrementAndGet();
        entry.addStateChangeListener(new StateChangeListener<MessageInstance, MessageInstance.State>()
        {
            public void stateChanged(MessageInstance entry, MessageInstance.State oldState, MessageInstance.State newState)
            {
                if(oldState.equals(MessageInstance.State.ACQUIRED) && !newState.equals(MessageInstance.State.ACQUIRED))
                {
                    _unacknowledgedBytes.addAndGet(-size);
                    _unacknowledgedCount.decrementAndGet();
                    entry.removeStateChangeListener(this);
                }
            }
        });
    }

    @Override
    public void acquisitionRemoved(final MessageInstance node)
    {
    }

    public long getUnacknowledgedBytes()
    {
        return _unacknowledgedBytes.longValue();
    }

    public long getUnacknowledgedMessages()
    {
        return _unacknowledgedCount.longValue();
    }
}
