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
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.transport.ServerSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.AMQException;
import org.apache.qpid.transport.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

import sun.awt.X11.XSystemTrayPeer;

public class Subscription_0_10 implements Subscription, FlowCreditManager.FlowCreditManagerListener
{
    private final QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final Lock _stateChangeLock = new ReentrantLock();

    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private final AtomicReference<QueueEntry> _queueContext = new AtomicReference<QueueEntry>(null);
    private final AtomicBoolean _deleted = new AtomicBoolean(false);


    private FlowCreditManager _creditManager;


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
    private final ServerSession _session;


    public Subscription_0_10(ServerSession session, String destination, MessageAcceptMode acceptMode,
                             MessageAcquireMode acquireMode, FlowCreditManager creditManager, FilterManager filters)
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
        return false;  //To change body of implemented methods use File | Settings | File Templates.
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


    public void send(QueueEntry entry) throws AMQException
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


        _session.sendMessage(xfr);


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
        _creditManager.addCredit(1, queueEntry.getSize());
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


    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
    }

    public void setCreditManager(FlowCreditManager creditManager)
    {
        _creditManager.removeListener(this);

        _creditManager = creditManager;

        creditManager.addStateListener(this);

    }


}
