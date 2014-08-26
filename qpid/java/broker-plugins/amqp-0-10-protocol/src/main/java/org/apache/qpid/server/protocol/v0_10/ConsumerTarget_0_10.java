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
package org.apache.qpid.server.protocol.v0_10;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.consumer.AbstractConsumerTarget;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ChannelMessages;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.plugin.MessageConverter;
import org.apache.qpid.server.protocol.MessageConverterRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueConsumer;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.Option;
import org.apache.qpid.util.GZIPUtils;

public class ConsumerTarget_0_10 extends AbstractConsumerTarget implements FlowCreditManager.FlowCreditManagerListener
{

    private static final Option[] BATCHED = new Option[] { Option.BATCH };

    private final AtomicBoolean _deleted = new AtomicBoolean(false);
    private final String _name;


    private FlowCreditManager_0_10 _creditManager;

    private final MessageAcceptMode _acceptMode;
    private final MessageAcquireMode _acquireMode;
    private MessageFlowMode _flowMode;
    private final ServerSession _session;
    private final AtomicBoolean _stopped = new AtomicBoolean(true);

    private final AtomicLong _unacknowledgedCount = new AtomicLong(0);
    private final AtomicLong _unacknowledgedBytes = new AtomicLong(0);

    private final Map<String, Object> _arguments;
    private int _deferredMessageCredit;
    private long _deferredSizeCredit;
    private final List<ConsumerImpl> _consumers = new CopyOnWriteArrayList<>();


    public ConsumerTarget_0_10(ServerSession session,
                               String name,
                               MessageAcceptMode acceptMode,
                               MessageAcquireMode acquireMode,
                               MessageFlowMode flowMode,
                               FlowCreditManager_0_10 creditManager,
                               Map<String, Object> arguments)
    {
        super(State.SUSPENDED);
        _session = session;
        _postIdSettingAction = new AddMessageDispositionListenerAction(session);
        _acceptMode = acceptMode;
        _acquireMode = acquireMode;
        _creditManager = creditManager;
        _flowMode = flowMode;
        _creditManager.addStateListener(this);
        _arguments = arguments == null ? Collections.<String, Object> emptyMap() :
                                         Collections.<String, Object> unmodifiableMap(arguments);
        _name = name;
    }

    public boolean isSuspended()
    {
        return getState()!=State.ACTIVE || _deleted.get() || _session.isClosing() || _session.getConnectionModel().isStopped(); // TODO check for Session suspension
    }

    public boolean close()
    {
        boolean closed = false;
        State state = getState();

        getSendLock();
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
            }
        finally
        {
            releaseSendLock();
        }

        return closed;

    }

    public void creditStateChanged(boolean hasCredit)
    {

        if(hasCredit)
        {
            if(!updateState(State.SUSPENDED, State.ACTIVE))
            {
                // this is a hack to get round the issue of increasing bytes credit
                notifyCurrentState();
            }
        }
        else
        {
            updateState(State.ACTIVE, State.SUSPENDED);
        }
    }

    public String getName()
    {
        return _name;
    }


    public static class AddMessageDispositionListenerAction implements Runnable
    {
        private MessageTransfer _xfr;
        private ServerSession.MessageDispositionChangeListener _action;
        private ServerSession _session;

        public AddMessageDispositionListenerAction(ServerSession session)
        {
            _session = session;
        }

        public void setXfr(MessageTransfer xfr)
        {
            _xfr = xfr;
        }

        public void setAction(ServerSession.MessageDispositionChangeListener action)
        {
            _action = action;
        }

        public void run()
        {
            if(_action != null)
            {
                _session.onMessageDispositionChange(_xfr, _action);
            }
        }
    }

    private final AddMessageDispositionListenerAction _postIdSettingAction;

    public long send(final ConsumerImpl consumer, final MessageInstance entry, boolean batch)
    {
        ServerMessage serverMsg = entry.getMessage();


        MessageTransfer xfr;

        DeliveryProperties deliveryProps;
        MessageProperties messageProps = null;

        MessageTransferMessage msg;

        if(serverMsg instanceof MessageTransferMessage)
        {

            msg = (MessageTransferMessage) serverMsg;

        }
        else
        {
            MessageConverter converter =
                    MessageConverterRegistry.getConverter(serverMsg.getClass(), MessageTransferMessage.class);


            msg = (MessageTransferMessage) converter.convert(serverMsg, _session.getVirtualHost());
        }
        DeliveryProperties origDeliveryProps = msg.getHeader() == null ? null : msg.getHeader().getDeliveryProperties();
        messageProps = msg.getHeader() == null ? null : msg.getHeader().getMessageProperties();

        deliveryProps = new DeliveryProperties();
        if(origDeliveryProps != null)
        {
            if(origDeliveryProps.hasDeliveryMode())
            {
                deliveryProps.setDeliveryMode(origDeliveryProps.getDeliveryMode());
            }
            if(origDeliveryProps.hasExchange())
            {
                deliveryProps.setExchange(origDeliveryProps.getExchange());
            }
            if(origDeliveryProps.hasExpiration())
            {
                deliveryProps.setExpiration(origDeliveryProps.getExpiration());
            }
            if(origDeliveryProps.hasPriority())
            {
                deliveryProps.setPriority(origDeliveryProps.getPriority());
            }
            if(origDeliveryProps.hasRoutingKey())
            {
                deliveryProps.setRoutingKey(origDeliveryProps.getRoutingKey());
            }
            if(origDeliveryProps.hasTimestamp())
            {
                deliveryProps.setTimestamp(origDeliveryProps.getTimestamp());
            }
            if(origDeliveryProps.hasTtl())
            {
                deliveryProps.setTtl(origDeliveryProps.getTtl());
            }


        }

        deliveryProps.setRedelivered(entry.isRedelivered());

        boolean msgCompressed = messageProps != null && GZIPUtils.GZIP_CONTENT_ENCODING.equals(messageProps.getContentEncoding());


        ByteBuffer body = msg.getBody();

        boolean compressionSupported = _session.getConnection().getConnectionDelegate().isCompressionSupported();

        if(msgCompressed && !compressionSupported)
        {
            byte[] uncompressed = GZIPUtils.uncompressBufferToArray(body);
            if(uncompressed != null)
            {
                messageProps.setContentEncoding(null);
                body = ByteBuffer.wrap(uncompressed);
            }
        }
        else if(!msgCompressed
                && compressionSupported
                && (messageProps == null || messageProps.getContentEncoding()==null)
                && body != null
                && body.remaining() > _session.getConnection().getMessageCompressionThreshold())
        {
            byte[] compressed = GZIPUtils.compressBufferToArray(body);
            if(compressed != null)
            {
                if(messageProps == null)
                {
                    messageProps = new MessageProperties();
                }
                messageProps.setContentEncoding(GZIPUtils.GZIP_CONTENT_ENCODING);
                body = ByteBuffer.wrap(compressed);
            }
        }
        long size = body == null ? 0 : body.remaining();

        Header header = new Header(deliveryProps, messageProps, msg.getHeader() == null ? null : msg.getHeader().getNonStandardProperties());

        xfr = batch ? new MessageTransfer(_name,_acceptMode,_acquireMode,header, body, BATCHED)
                    : new MessageTransfer(_name,_acceptMode,_acquireMode,header, body);

        if(_acceptMode == MessageAcceptMode.NONE && _acquireMode != MessageAcquireMode.PRE_ACQUIRED)
        {
            xfr.setCompletionListener(new MessageAcceptCompletionListener(this, consumer, _session, entry, _flowMode == MessageFlowMode.WINDOW));
        }
        else if(_flowMode == MessageFlowMode.WINDOW)
        {
            xfr.setCompletionListener(new Method.CompletionListener()
                                        {
                                            public void onComplete(Method method)
                                            {
                                                deferredAddCredit(1, entry.getMessage().getSize());
                                            }
                                        });
        }


        _postIdSettingAction.setXfr(xfr);
        if(_acceptMode == MessageAcceptMode.EXPLICIT)
        {
            _postIdSettingAction.setAction(new ExplicitAcceptDispositionChangeListener(entry, this, consumer));
        }
        else if(_acquireMode != MessageAcquireMode.PRE_ACQUIRED)
        {
            _postIdSettingAction.setAction(new ImplicitAcceptDispositionChangeListener(entry, this, consumer));
        }
        else
        {
            _postIdSettingAction.setAction(null);
        }


        _session.sendMessage(xfr, _postIdSettingAction);
        entry.incrementDeliveryCount();
        if(_acceptMode == MessageAcceptMode.NONE && _acquireMode == MessageAcquireMode.PRE_ACQUIRED)
        {
            forceDequeue(entry, false);
        }
        else if(_acquireMode == MessageAcquireMode.PRE_ACQUIRED)
        {
            recordUnacknowledged(entry);
        }
        return size;
    }

    void recordUnacknowledged(MessageInstance entry)
    {
        _unacknowledgedCount.incrementAndGet();
        _unacknowledgedBytes.addAndGet(entry.getMessage().getSize());
    }

    private void deferredAddCredit(final int deferredMessageCredit, final long deferredSizeCredit)
    {
        _deferredMessageCredit += deferredMessageCredit;
        _deferredSizeCredit += deferredSizeCredit;

    }

    public void flushCreditState(boolean strict)
    {
        if(strict || !isSuspended() || _deferredMessageCredit >= 200
          || !(_creditManager instanceof WindowCreditManager)
          || ((WindowCreditManager)_creditManager).getMessageCreditLimit() < 400 )
        {
            _creditManager.restoreCredit(_deferredMessageCredit, _deferredSizeCredit);
            _deferredMessageCredit = 0;
            _deferredSizeCredit = 0l;
        }
    }

    private void forceDequeue(final MessageInstance entry, final boolean restoreCredit)
    {
        AutoCommitTransaction dequeueTxn = new AutoCommitTransaction(_session.getVirtualHost().getMessageStore());
        dequeueTxn.dequeue(entry.getOwningResource(), entry.getMessage(),
                           new ServerTransaction.Action()
                           {
                               public void postCommit()
                               {
                                   if (restoreCredit)
                                   {
                                       restoreCredit(entry.getMessage());
                                   }
                                   entry.delete();
                               }

                               public void onRollback()
                               {

                               }
                           });
   }

    void reject(final MessageInstance entry)
    {
        entry.setRedelivered();
        entry.routeToAlternate(null, null);
        if(isAcquiredByConsumer(entry))
        {
            entry.delete();
        }
    }

    private boolean isAcquiredByConsumer(final MessageInstance entry)
    {
        ConsumerImpl acquiringConsumer = entry.getAcquiringConsumer();
        if(acquiringConsumer instanceof QueueConsumer)
        {
            return ((QueueConsumer)acquiringConsumer).getTarget() == this;
        }

        return false;
    }

    void release(final MessageInstance entry, final boolean setRedelivered)
    {
        if (setRedelivered)
        {
            entry.setRedelivered();
        }

        if (getSessionModel().isClosing() || !setRedelivered)
        {
            entry.decrementDeliveryCount();
        }

        if (isMaxDeliveryLimitReached(entry))
        {
            sendToDLQOrDiscard(entry);
        }
        else
        {
            entry.release();
        }
    }

    protected void sendToDLQOrDiscard(MessageInstance entry)
    {
        final ServerMessage msg = entry.getMessage();

        int requeues = entry.routeToAlternate(new Action<MessageInstance>()
                    {
                        @Override
                        public void performAction(final MessageInstance requeueEntry)
                        {
                            getEventLogger().message(ChannelMessages.DEADLETTERMSG(msg.getMessageNumber(),
                                                                                   requeueEntry.getOwningResource()
                                                                                           .getName()));
                        }
                    }, null);

        if (requeues == 0)
        {
            TransactionLogResource owningResource = entry.getOwningResource();
            if(owningResource instanceof AMQQueue)
            {
                final AMQQueue queue = (AMQQueue)owningResource;
                final Exchange alternateExchange = queue.getAlternateExchange();

                if(alternateExchange != null)
                {
                    getEventLogger().message(ChannelMessages.DISCARDMSG_NOROUTE(msg.getMessageNumber(),
                                                                           alternateExchange.getName()));
                }
                else
                {
                    getEventLogger().message(ChannelMessages.DISCARDMSG_NOALTEXCH(msg.getMessageNumber(),
                                                                             queue.getName(),
                                                                             msg.getInitialRoutingAddress()));
                }
            }
        }
    }

    protected EventLogger getEventLogger()
    {
        return getSessionModel().getVirtualHost().getEventLogger();
    }

    private boolean isMaxDeliveryLimitReached(MessageInstance entry)
    {
        final int maxDeliveryLimit = entry.getMaximumDeliveryCount();
        return (maxDeliveryLimit > 0 && entry.getDeliveryCount() >= maxDeliveryLimit);
    }

    public void queueDeleted()
    {
        _deleted.set(true);
    }

    public boolean allocateCredit(ServerMessage message)
    {
        return _creditManager.useCreditForMessage(message.getSize());
    }

    public void restoreCredit(ServerMessage message)
    {
        _creditManager.restoreCredit(1, message.getSize());
    }

    public FlowCreditManager_0_10 getCreditManager()
    {
        return _creditManager;
    }


    public void stop()
    {
        try
        {
            getSendLock();

            updateState(State.ACTIVE, State.SUSPENDED);
            _stopped.set(true);
            FlowCreditManager_0_10 creditManager = getCreditManager();
            creditManager.clearCredit();
        }
        finally
        {
            releaseSendLock();
        }
    }

    public void addCredit(MessageCreditUnit unit, long value)
    {
        FlowCreditManager_0_10 creditManager = getCreditManager();

        switch (unit)
        {
            case MESSAGE:

                creditManager.addCredit(value, 0L);
                break;
            case BYTE:
                creditManager.addCredit(0l, value);
                break;
        }

        _stopped.set(false);

        if(creditManager.hasCredit())
        {
            updateState(State.SUSPENDED, State.ACTIVE);
        }

    }

    public void setFlowMode(MessageFlowMode flowMode)
    {


        _creditManager.removeListener(this);

        switch(flowMode)
        {
            case CREDIT:
                _creditManager = new CreditCreditManager(0l,0l);
                break;
            case WINDOW:
                _creditManager = new WindowCreditManager(0l,0l);
                break;
            default:
                // this should never happen, as 0-10 is finalised and so the enum should never change
                throw new ConnectionScopedRuntimeException("Unknown message flow mode: " + flowMode);
        }
        _flowMode = flowMode;
        updateState(State.ACTIVE, State.SUSPENDED);

        _creditManager.addStateListener(this);

    }

    public boolean isStopped()
    {
        return _stopped.get();
    }

    public boolean deleteAcquired(MessageInstance entry)
    {
        if(isAcquiredByConsumer(entry))
        {
            acquisitionRemoved(entry);
            entry.delete();
            return true;
        }
        else
        {
            return false;
        }
    }

    @Override
    public void acquisitionRemoved(final MessageInstance entry)
    {
        _unacknowledgedBytes.addAndGet(-entry.getMessage().getSize());
        _unacknowledgedCount.decrementAndGet();
    }

    public void flush()
    {
        flushCreditState(true);
        for(ConsumerImpl consumer : _consumers)
        {
            consumer.flush();
        }
        stop();
    }

    public ServerSession getSessionModel()
    {
        return _session;
    }

    public boolean isDurable()
    {
        return false;
    }

    public Map<String, Object> getArguments()
    {
        return _arguments;
    }

    public void queueEmpty()
    {
    }

    public void flushBatched()
    {
        _session.getConnection().flush();
    }


    @Override
    public void consumerAdded(final ConsumerImpl sub)
    {
        _consumers.add(sub);
    }

    @Override
    public void consumerRemoved(final ConsumerImpl sub)
    {
        _consumers.remove(sub);
        if(_consumers.isEmpty())
        {
            close();
        }
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
