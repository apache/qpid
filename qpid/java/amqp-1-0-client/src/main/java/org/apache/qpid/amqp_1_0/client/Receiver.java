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
package org.apache.qpid.amqp_1_0.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import org.apache.qpid.amqp_1_0.messaging.SectionDecoder;
import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.Predicate;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkListener;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.Modified;
import org.apache.qpid.amqp_1_0.type.messaging.Released;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.ReceiverSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;

public class Receiver implements DeliveryStateHandler
{
    private ReceivingLinkEndpoint _endpoint;
    private int _id;
    private static final UnsignedInteger DEFAULT_INITIAL_CREDIT = UnsignedInteger.valueOf(100);
    private Session _session;

    private Queue<Transfer> _prefetchQueue = new ConcurrentLinkedQueue<Transfer>();
    private Map<Binary, SettledAction> _unsettledMap = new HashMap<Binary, SettledAction>();
    private MessageArrivalListener _messageArrivalListener;
    private org.apache.qpid.amqp_1_0.type.transport.Error _error;
    private Runnable _remoteErrorTask;

    public Receiver(final Session session,
                    final String linkName,
                    final Target target,
                    final Source source,
                    final AcknowledgeMode ackMode) throws ConnectionErrorException
    {
        this(session, linkName, target, source, ackMode, false);
    }

    public Receiver(final Session session,
                    final String linkName,
                    final Target target,
                    final Source source,
                    final AcknowledgeMode ackMode,
                    boolean isDurable) throws ConnectionErrorException
    {
        this(session,linkName,target,source,ackMode,isDurable,null);
    }

    public Receiver(final Session session,
                    final String linkName,
                    final Target target,
                    final Source source,
                    final AcknowledgeMode ackMode,
                    final boolean isDurable,
                    final Map<Binary,Outcome> unsettled) throws ConnectionErrorException
    {

        session.getConnection().checkNotClosed();
        _session = session;
        if(isDurable)
        {
            source.setDurable(TerminusDurability.UNSETTLED_STATE);
            source.setExpiryPolicy(TerminusExpiryPolicy.NEVER);
        }
        else if(source != null)
        {
            source.setDurable(TerminusDurability.NONE);
            source.setExpiryPolicy(TerminusExpiryPolicy.LINK_DETACH);
        }
        _endpoint = session.getEndpoint().createReceivingLinkEndpoint(linkName, target, source,
                                                                      UnsignedInteger.ZERO);

        _endpoint.setDeliveryStateHandler(this);

        switch(ackMode)
        {
            case ALO:
                _endpoint.setSendingSettlementMode(SenderSettleMode.UNSETTLED);
                _endpoint.setReceivingSettlementMode(ReceiverSettleMode.FIRST);
                break;
            case AMO:
                _endpoint.setSendingSettlementMode(SenderSettleMode.SETTLED);
                _endpoint.setReceivingSettlementMode(ReceiverSettleMode.FIRST);
                break;
            case EO:
                _endpoint.setSendingSettlementMode(SenderSettleMode.UNSETTLED);
                _endpoint.setReceivingSettlementMode(ReceiverSettleMode.SECOND);
                break;

        }

        _endpoint.setLinkEventListener(new ReceivingLinkListener.DefaultLinkEventListener()
        {
            @Override public void messageTransfer(final Transfer xfr)
            {
                _prefetchQueue.add(xfr);
                postPrefetchAction();
            }

            @Override
            public void remoteDetached(final LinkEndpoint endpoint, final Detach detach)
            {
                _error = detach.getError();
                if(detach.getError()!=null)
                {
                    remoteError();
                }
                super.remoteDetached(endpoint, detach);
            }
        });

        _endpoint.setLocalUnsettled(unsettled);
        _endpoint.attach();

        try
        {
            _endpoint.waitUntil(new Predicate()
            {

                @Override
                public boolean isSatisfied()
                {
                    return _endpoint.isAttached() || _endpoint.isDetached();
                }
            });
        }
        catch (TimeoutException e)
        {
            throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR,"Timeout waiting for attach");
        }
        catch (InterruptedException e)
        {
            throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR,"Interrupted while waiting for attach");
        }

        if(_endpoint.getSource() == null)
        {
            try
            {
                _endpoint.waitUntil(new Predicate()
                {
                    @Override
                    public boolean isSatisfied()
                    {
                        return _endpoint.isDetached();
                    }
                });
            }
            catch (TimeoutException e)
            {
                throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR,"Timeout waiting for detach following failed attach");
            }
            catch (InterruptedException e)
            {
                throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR,"Interrupted while waiting for detach following failed attach");
            }
            throw new ConnectionErrorException(getError().getCondition(),
                                               getError().getDescription() == null
                                                       ? "AMQP error: '" + getError().getCondition().toString()
                                                         + "' when attempting to create a receiver"
                                                         + (source != null ? " from: '" + source.getAddress() +"'" : "")
                                                       : getError().getDescription());
        }
        else
        {

        }
    }

    private void remoteError()
    {
        if(_remoteErrorTask != null)
        {
            Thread thread = new Thread(_remoteErrorTask);
            thread.start();
        }
    }

    private void postPrefetchAction()
    {
        if(_messageArrivalListener != null)
        {
            _messageArrivalListener.messageArrived(this);
        }
    }

    public void setCredit(UnsignedInteger credit, boolean window)
    {
        _endpoint.setLinkCredit(credit);
        _endpoint.setCreditWindow(window);

    }


    public String getAddress()
    {
        return ((Source)_endpoint.getSource()).getAddress();
    }

    public Map getFilter()
    {
        return ((Source)_endpoint.getSource()).getFilter();
    }

    public Message receive()
    {
        return receive(-1L);
    }

    public Message receive(boolean wait)
    {
        return receive(wait ? -1L : 0L);
    }

    // 0 means no wait, -1 wait forever
    public Message receive(long wait)
    {
        Message m = null;
        Transfer xfr;
        long endTime = wait > 0L ? System.currentTimeMillis() + wait : 0L;

        while((xfr = receiveFromPrefetch(wait)) != null )
        {

            if(!Boolean.TRUE.equals(xfr.getAborted()))
            {
                Binary deliveryTag = xfr.getDeliveryTag();
                Boolean resume = xfr.getResume();

                List<Section> sections = new ArrayList<Section>();
                List<ByteBuffer> payloads = new ArrayList<ByteBuffer>();
                int totalSize = 0;

                boolean hasMore;
                do
                {
                    hasMore = Boolean.TRUE.equals(xfr.getMore());

                    ByteBuffer buf = xfr.getPayload();

                    if(buf != null)
                    {

                        totalSize += buf.remaining();

                        payloads.add(buf);
                    }
                    if(hasMore)
                    {
                        xfr = receiveFromPrefetch(-1l);
                        if(xfr== null)
                        {
                            // TODO - this is wrong!!!!
                            System.out.println("eeek");
                        }
                    }
                }
                while(hasMore && !Boolean.TRUE.equals(xfr.getAborted()));

                if(!Boolean.TRUE.equals(xfr.getAborted()))
                {
                    ByteBuffer allPayload = ByteBuffer.allocate(totalSize);
                    for(ByteBuffer payload : payloads)
                    {
                        allPayload.put(payload);
                    }
                    allPayload.flip();
                    SectionDecoder decoder = _session.getSectionDecoder();

                    try
                    {
                        sections = decoder.parseAll(allPayload);
                    }
                    catch (AmqpErrorException e)
                    {
                        // todo - throw a sensible error
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    m = new Message(sections, false);
                    m.setDeliveryTag(deliveryTag);
                    m.setResume(resume);
                    m.setReceiver(this);
                    break;
                }
            }

            if(wait > 0L)
            {
                wait = endTime - System.currentTimeMillis();
                if(wait <=0L)
                {
                    break;
                }
            }
        }


        return m;

    }

    private Transfer receiveFromPrefetch(long wait)
    {
        long endTime = ((wait >0L) ? (System.currentTimeMillis() + wait) : 0L);
        final Object lock = _endpoint.getLock();
        synchronized(lock)
        {
            Transfer xfr;
            while(((xfr = _prefetchQueue.peek()) == null) && !_endpoint.isDrained() && !_endpoint.isDetached()
                  && wait != 0)
            {
                try
                {
                    if(wait>0L)
                    {
                        lock.wait(wait);
                    }
                    else if(wait<0L)
                    {
                        lock.wait();
                    }
                }
                catch (InterruptedException e)
                {
                    return null;
                }
                if(wait > 0L)
                {
                    wait = endTime - System.currentTimeMillis();
                    if(wait <= 0L)
                    {
                        break;
                    }
                }

            }
            if(xfr != null)
            {
                _prefetchQueue.poll();

            }

            return xfr;
        }

    }


    public void release(final Message m)
    {
        release(m.getDeliveryTag());
    }

    public void release(Binary deliveryTag)
    {
        update(new Released(), deliveryTag, null, null);
    }


    public void modified(Binary tag)
    {
        final Modified outcome = new Modified();
        outcome.setDeliveryFailed(true);

        update(outcome, tag, null, null);
    }

    public void acknowledge(final Message m)
    {
        acknowledge(m.getDeliveryTag());
    }

    public void acknowledge(final Message m, SettledAction a)
    {
        acknowledge(m.getDeliveryTag(), a);
    }


    public void acknowledge(final Message m, Transaction txn)
    {
        acknowledge(m.getDeliveryTag(), txn);
    }


    public void acknowledge(final Binary deliveryTag)
    {
        acknowledge(deliveryTag, null, null);
    }


    public void acknowledge(final Binary deliveryTag, SettledAction a)
    {
        acknowledge(deliveryTag, null, a);
    }

    public void acknowledge(final Binary deliveryTag, final Transaction txn)
    {
        acknowledge(deliveryTag, txn, null);
    }

    public void acknowledge(final Binary deliveryTag, final Transaction txn, SettledAction action)
    {
        update(new Accepted(), deliveryTag, txn, action);
    }

    public void update(Outcome outcome, final Binary deliveryTag, final Transaction txn, SettledAction action)
    {

        DeliveryState state;
        if(txn != null)
        {
            TransactionalState txnState = new TransactionalState();
            txnState.setOutcome(outcome);
            txnState.setTxnId(txn.getTxnId());
            state = txnState;
        }
        else
        {
            state = (DeliveryState) outcome;
        }
        boolean settled = txn == null && !ReceiverSettleMode.SECOND.equals(_endpoint.getReceivingSettlementMode());

        if(!(settled || action == null))
        {
            _unsettledMap.put(deliveryTag, action);
        }

        _endpoint.updateDisposition(deliveryTag,state, settled);
    }

    public Error getError()
    {
        return _error;
    }

    public void acknowledgeAll(Message m)
    {
        acknowledgeAll(m.getDeliveryTag());
    }

    public void acknowledgeAll(Binary deliveryTag)
    {
        acknowledgeAll(deliveryTag, null, null);
    }

    public void acknowledgeAll(Binary deliveryTag, final Transaction txn, SettledAction action)
    {
        updateAll(new Accepted(), deliveryTag, txn, action);
    }

    public void updateAll(Outcome outcome, Binary deliveryTag)
    {
        updateAll(outcome, deliveryTag, null, null);
    }

    public void updateAll(Outcome outcome, Binary deliveryTag, final Transaction txn, SettledAction action)
    {
        DeliveryState state;

        if(txn != null)
        {
            TransactionalState txnState = new TransactionalState();
            txnState.setOutcome(outcome);
            txnState.setTxnId(txn.getTxnId());
            state = txnState;
        }
        else
        {
            state = (DeliveryState) outcome;
        }
        boolean settled = txn == null && !ReceiverSettleMode.SECOND.equals(_endpoint.getReceivingSettlementMode());

        if(!(settled || action == null))
        {
            _unsettledMap.put(deliveryTag, action);
        }
        _endpoint.updateAllDisposition(deliveryTag, state, settled);
    }



    public void close()
    {
        _endpoint.setTarget(null);
        _endpoint.close();
        Message msg;
        while((msg = receive(0l)) != null)
        {
            release(msg);
        }
        _session.removeReceiver(this);

    }


    public void detach()
    {
        _endpoint.setTarget(null);
        _endpoint.detach();
        Message msg;
        while((msg = receive(0l)) != null)
        {
            release(msg);
        }

    }

    public void drain()
    {
        _endpoint.drain();
    }

    /**
     * Waits for the receiver to drain or a message to be available to be received.
     * @return true if the receiver has been drained.
     */
    public boolean drainWait()
    {
        final Object lock = _endpoint.getLock();
        synchronized(lock)
        {
            try
            {
                while( _prefetchQueue.peek()==null && !_endpoint.isDrained() && !_endpoint.isDetached() )
                {
                    lock.wait();
                }
            }
            catch (InterruptedException e)
            {
            }
        }
        return _prefetchQueue.peek()==null && _endpoint.isDrained();
    }

    /**
     * Clears the receiver drain so that message delivery can resume.
     */
    public void clearDrain()
    {
        _endpoint.clearDrain();
    }

    public void setCreditWithTransaction(final UnsignedInteger credit, final Transaction txn)
    {
        _endpoint.setLinkCredit(credit);
        _endpoint.setTransactionId(txn == null ? null : txn.getTxnId());
        _endpoint.setCreditWindow(false);

    }

    public void handle(final Binary deliveryTag, final DeliveryState state, final Boolean settled)
    {
        if(Boolean.TRUE.equals(settled))
        {
            SettledAction action = _unsettledMap.remove(deliveryTag);
            if(action != null)
            {
                action.onSettled(deliveryTag);
            }
        }
    }

    public Map<Binary, Outcome> getRemoteUnsettled()
    {
        return _endpoint.getInitialUnsettledMap();
    }


    public void setMessageArrivalListener(final MessageArrivalListener messageArrivalListener)
    {
        synchronized(_endpoint.getLock())
        {
            _messageArrivalListener = messageArrivalListener;
            int prefetchSize = _prefetchQueue.size();
            for(int i = 0; i < prefetchSize; i++)
            {
                postPrefetchAction();
            }
        }
    }

    public Session getSession()
    {
        return _session;
    }

    public org.apache.qpid.amqp_1_0.type.Source getSource()
    {
        return _endpoint.getSource();
    }

    public static interface SettledAction
    {
        public void onSettled(Binary deliveryTag);
    }


    public interface MessageArrivalListener
    {
        void messageArrived(Receiver receiver);
    }

    public void setRemoteErrorListener(Runnable listener)
    {
        _remoteErrorTask = listener;
    }
}
