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
package org.apache.qpid.server.subscription;

import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.FailedDequeueException;
import org.apache.qpid.server.queue.MessageCleanupException;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.flow.CreditCreditManager;
import org.apache.qpid.server.flow.WindowCreditManager;
import org.apache.qpid.server.flow.FlowCreditManager_0_10;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.transport.ServerSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.AMQException;
import org.apache.qpid.transport.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class Subscription_0_10 implements Subscription, FlowCreditManager.FlowCreditManagerListener
{
    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final Lock _stateChangeLock = new ReentrantLock();

    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private final AtomicReference<QueueEntry> _queueContext = new AtomicReference<QueueEntry>(null);
    private final AtomicBoolean _deleted = new AtomicBoolean(false);


    private FlowCreditManager_0_10 _creditManager;


    private StateListener _stateListener = new StateListener()
                                            {

                                                public void stateChange(Subscription sub, State oldState, State newState)
                                                {

                                                }
                                            };
    private AMQQueue _queue;
    private final String _destination;
    private boolean _noLocal;
    private final FilterManager _filters;
    private final MessageAcceptMode _acceptMode;
    private final MessageAcquireMode _acquireMode;
    private MessageFlowMode _flowMode;
    private final ServerSession _session;
    private AtomicBoolean _stopped = new AtomicBoolean(true);
    private ConcurrentHashMap<Integer, QueueEntry> _sentMap = new ConcurrentHashMap<Integer, QueueEntry>();


    public Subscription_0_10(ServerSession session, String destination, MessageAcceptMode acceptMode,
                             MessageAcquireMode acquireMode, FlowCreditManager_0_10 creditManager, FilterManager filters)
    {
        _session = session;
        _destination = destination;
        _acceptMode = acceptMode;
        _acquireMode = acquireMode;
        _creditManager = creditManager;
        _filters = filters;
        _creditManager.addStateListener(this);

    }

    public AMQQueue getQueue()
    {
        return _queue;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

    public void setQueue(AMQQueue queue)
    {
        if(getQueue() != null)
        {
            throw new IllegalStateException("Attempt to set queue for subscription " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;
    }

    public AMQShortString getConsumerTag()
    {
        return new AMQShortString(_destination);
    }

    public boolean isSuspended()
    {
        return !isActive() || _deleted.get(); // TODO check for Session suspension
    }

    public boolean hasInterest(QueueEntry entry)
    {

        //TODO 0-8/9 to 0-10 conversion
        if(!(entry.getMessage() instanceof MessageTransferMessage))
        {
            return false;
        }

        //check that the message hasn't been rejected
        if (entry.isRejectedBy(this))
        {

            return false;
        }



        if (_noLocal)
        {


        }


        return checkFilters(entry);


    }

    private boolean checkFilters(QueueEntry entry)
    {
        return (_filters == null) || _filters.allAllow(entry.getMessage());
    }

    public boolean isAutoClose()
    {
        // no such thing in 0-10
        return false;
    }

    public boolean isClosed()
    {
        return getState() == State.CLOSED;
    }

    public boolean isBrowser()
    {
        return _acquireMode == MessageAcquireMode.NOT_ACQUIRED;
    }

    public boolean seesRequeues()
    {
        return _acquireMode != MessageAcquireMode.NOT_ACQUIRED || _acceptMode == MessageAcceptMode.EXPLICIT;
    }

    public void close()
    {
        boolean closed = false;
        State state = getState();

        _stateChangeLock.lock();
        try
        {
            while(!closed && state != State.CLOSED)
            {
                closed = _state.compareAndSet(state, State.CLOSED);
                if(!closed)
                {
                    state = getState();
                }
                else
                {
                    _stateListener.stateChange(this,state, State.CLOSED);
                }
            }
            _creditManager.removeListener(this);
        }
        finally
        {
            _stateChangeLock.unlock();
        }



    }

    public void creditStateChanged(boolean hasCredit)
    {

        if(hasCredit)
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
            else
            {
                // this is a hack to get round the issue of increasing bytes credit
                _stateListener.stateChange(this, State.ACTIVE, State.ACTIVE);
            }
        }
        else
        {
            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
        }
    }


    public void send(final QueueEntry entry) throws AMQException
    {
        ServerMessage serverMsg = entry.getMessage();


        MessageTransferMessage msg = (MessageTransferMessage) serverMsg;



        MessageTransfer xfr = new MessageTransfer();
        xfr.setDestination(_destination);
        xfr.setBody(msg.getBody());
        xfr.setAcceptMode(_acceptMode);
        xfr.setAcquireMode(_acquireMode);

        Struct[] headers = msg.getHeader().getStructs();

        ArrayList<Struct> newHeaders = new ArrayList<Struct>(headers.length);
        DeliveryProperties origDeliveryProps = null;
        for(Struct header : headers)
        {
            if(header instanceof DeliveryProperties)
            {
                origDeliveryProps = (DeliveryProperties) header;
            }
            else
            {
                newHeaders.add(header);
            }
        }

        DeliveryProperties deliveryProps = new DeliveryProperties();
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

        }

        deliveryProps.setRedelivered(entry.isRedelivered());

        newHeaders.add(deliveryProps);
        xfr.setHeader(new Header(newHeaders));                

        if(_acceptMode == MessageAcceptMode.NONE)
        {
            xfr.setCompletionListener(new MessageAcceptCompletionListener(this, _session, entry));
        }



        _session.sendMessage(xfr);

        if(_acceptMode == MessageAcceptMode.EXPLICIT)
        {
            // potential race condition if incomming commands on this session can be processed on a different thread
            // to this one (i.e. the message is only put in the map *after* it has been sent, theoretically we could get
            // acknowledgement back before reaching the next line)
            _session.onMessageDispositionChange(xfr, new ServerSession.MessageDispositionChangeListener()
                                        {
                                            public void onAccept()
                                            {
                                                _session.acknowledge(Subscription_0_10.this,entry);
                                            }

                                            public void onRelease()
                                            {
                                                release(entry);
                                            }

                                            public void onReject()
                                            {
                                                reject(entry);
                                            }
            });
        }
        else
        {
            _session.onMessageDispositionChange(xfr, new ServerSession.MessageDispositionChangeListener()
                                        {
                                            public void onAccept()
                                            {
                                                // TODO : should log error of explicit accept on non-explicit sub
                                            }

                                            public void onRelease()
                                            {
                                                release(entry);
                                            }

                                            public void onReject()
                                            {
                                                reject(entry);
                                            }

            });
        }


    }

    private void reject(QueueEntry entry)
    {
        entry.setRedelivered(true);
        entry.reject(this);

    }

    private void release(QueueEntry entry)
    {
        entry.setRedelivered(true);
        entry.release();
    }

    public void queueDeleted(AMQQueue queue)
    {
        _deleted.set(true);
    }

    public boolean wouldSuspend(QueueEntry msg)
    {
        return !_creditManager.useCreditForMessage(msg.getMessage());
    }

    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void restoreCredit(QueueEntry queueEntry)
    {
        _creditManager.restoreCredit(1, queueEntry.getSize());
    }

    public void setStateListener(StateListener listener)
    {
        _stateListener = listener;
    }

    public State getState()
    {
        return _state.get();
    }

    public QueueEntry getLastSeenEntry()
    {
        return _queueContext.get();
    }

    public boolean setLastSeenEntry(QueueEntry expected, QueueEntry newValue)
    {
        return _queueContext.compareAndSet(expected, newValue);
    }

    public boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public void confirmAutoClose()
    {
        //No such thing in 0-10
    }


    public FlowCreditManager_0_10 getCreditManager()
    {
        return _creditManager;
    }


    public void stop()
    {
        if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
        {
            _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
        }
        _stopped.set(true);
        FlowCreditManager_0_10 creditManager = getCreditManager();
        creditManager.clearCredit();
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
                creditManager.addCredit(0L, value);
                break;
        }

        _stopped.set(false);

        if(creditManager.hasCredit())
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
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
                throw new RuntimeException("Unknown message flow mode: " + flowMode);
        }
        _creditManager.addStateListener(this);
    }

    public boolean isStopped()
    {
        return _stopped.get();
    }

    public boolean acquires()
    {
        return _acquireMode == MessageAcquireMode.PRE_ACQUIRED;
    }

    public void acknowledge(QueueEntry entry)
    {
        // TODO Fix Store Context / cleanup

        try
        {
            entry.discard(new StoreContext());
        }
        catch (FailedDequeueException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (MessageCleanupException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void flush() throws AMQException
    {
        _queue.flushSubscription(this);
        stop();
    }
}
