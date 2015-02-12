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

package org.apache.qpid.amqp_1_0.transport;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Source;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.Target;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.transport.Attach;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Flow;
import org.apache.qpid.amqp_1_0.type.transport.ReceiverSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Role;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;

public abstract class LinkEndpoint<T extends LinkEventListener>
{

    private T _linkEventListener;
    private DeliveryStateHandler _deliveryStateHandler;
    private Object _flowTransactionId;
    private SenderSettleMode _sendingSettlementMode;
    private ReceiverSettleMode _receivingSettlementMode;
    private Map _initialUnsettledMap;
    private Map _localUnsettled;
    private UnsignedInteger _lastSentCreditLimit;


    private enum State
    {
        DETACHED,
        ATTACH_SENT,
        ATTACH_RECVD,
        ATTACHED,
        DETACH_SENT,
        DETACH_RECVD
    };

    private final String _name;

    private SessionEndpoint _session;


    private volatile State _state = State.DETACHED;

    private Source _source;
    private Target _target;
    private UnsignedInteger _deliveryCount;
    private UnsignedInteger _linkCredit;
    private UnsignedInteger _available;
    private Boolean _drain;
    private UnsignedInteger _localHandle;
    private UnsignedLong _maxMessageSize;

    private Map<Binary,Delivery> _unsettledTransfers = new HashMap<Binary,Delivery>();

    LinkEndpoint(final SessionEndpoint sessionEndpoint, String name, Map<Binary, Outcome> unsettled)
    {
        this(sessionEndpoint, name, unsettled, null);
    }

    LinkEndpoint(final SessionEndpoint sessionEndpoint, String name, Map<Binary, Outcome> unsettled, DeliveryStateHandler deliveryStateHandler)
    {
        _name = name;
        _session = sessionEndpoint;
        _linkCredit = UnsignedInteger.valueOf(0);
        _drain = Boolean.FALSE;
        _localUnsettled = unsettled;
        _deliveryStateHandler = deliveryStateHandler;
    }

    LinkEndpoint(final SessionEndpoint sessionEndpoint,final Attach attach)
    {
        _session = sessionEndpoint;

        _name = attach.getName();
        _initialUnsettledMap = attach.getUnsettled();

        _state = State.ATTACH_RECVD;
    }

    public String getName()
    {
        return _name;
    }

    public abstract Role getRole();

    public Source getSource()
    {
        return _source;
    }

    public void setSource(final Source source)
    {
        _source = source;
    }

    public Target getTarget()
    {
        return _target;
    }

    public void setTarget(final Target target)
    {
        _target = target;
    }

    public void setDeliveryCount(final UnsignedInteger deliveryCount)
    {
        _deliveryCount = deliveryCount;
    }

    public void setLinkCredit(final UnsignedInteger linkCredit)
    {
        _linkCredit = linkCredit;
    }

    public void setAvailable(final UnsignedInteger available)
    {
        _available = available;
    }

    public void setDrain(final Boolean drain)
    {
        _drain = drain;
    }

    public UnsignedInteger getDeliveryCount()
    {
        return _deliveryCount;
    }

    public UnsignedInteger getAvailable()
    {
        return _available;
    }

    public Boolean getDrain()
    {
        return _drain;
    }

    public UnsignedInteger getLinkCredit()
    {
        return _linkCredit;
    }

    public void remoteDetached(Detach detach)
    {
        synchronized (getLock())
        {
            switch(_state)
            {
                case DETACH_SENT:
                    _state = State.DETACHED;
                    break;
                case ATTACHED:
                    _state = State.DETACH_RECVD;
                    _linkEventListener.remoteDetached(this, detach);
                    break;
            }
            getLock().notifyAll();
        }
    }

    public void receiveTransfer(final Transfer transfer, final Delivery delivery)
    {
        // TODO
    }

    public void settledByPeer(final Binary deliveryTag)
    {
        // TODO
    }

    public void receiveFlow(final Flow flow)
    {
    }

    public void addUnsettled(final Delivery unsettled)
    {
        synchronized(getLock())
        {
            _unsettledTransfers.put(unsettled.getDeliveryTag(), unsettled);
            getLock().notifyAll();
        }
    }

    public void receiveDeliveryState(final Delivery unsettled,
                                     final DeliveryState state,
                                     final Boolean settled)
    {
        // TODO
        synchronized(getLock())
        {
            if(_deliveryStateHandler != null)
            {
               _deliveryStateHandler.handle(unsettled.getDeliveryTag(), state, settled);
            }

            if(settled)
            {
                settle(unsettled.getDeliveryTag());
            }

            getLock().notifyAll();
        }

    }

    public void settle(final Binary deliveryTag)
    {
        Delivery delivery = _unsettledTransfers.remove(deliveryTag);
        if(delivery != null)
        {
            getSession().settle(getRole(),delivery.getDeliveryId());
        }

    }

    public int getUnsettledCount()
    {
        synchronized(getLock())
        {
            return _unsettledTransfers.size();
        }
    }

    public void setLocalHandle(final UnsignedInteger localHandle)
    {
        _localHandle = localHandle;
    }

    public void receiveAttach(final Attach attach)
    {
        synchronized(getLock())
        {
            switch(_state)
            {
                case ATTACH_SENT:
                {

                    _state = State.ATTACHED;
                    getLock().notifyAll();

                    _initialUnsettledMap = attach.getUnsettled();
                    /*  TODO - don't yet handle:

                        attach.getUnsettled();
                        attach.getProperties();
                        attach.getDurable();
                        attach.getExpiryPolicy();
                        attach.getTimeout();
                     */

                    break;
                }

                case DETACHED:
                {
                    _state = State.ATTACHED;
                    getLock().notifyAll();
                }


            }

            if(attach.getRole() == Role.SENDER)
            {
                _source = attach.getSource();
            }
            else
            {
                _target = attach.getTarget();
            }

            if(getRole() == Role.SENDER)
            {
                _maxMessageSize = attach.getMaxMessageSize();
            }

        }
    }

    public boolean isAttached()
    {
        synchronized (getLock())
        {
            return _state == State.ATTACHED;
        }
    }

    public boolean isDetached()
    {
        synchronized (getLock())
        {
            return _state == State.DETACHED || _session.isEnded();
        }
    }

    public SessionEndpoint getSession()
    {
        return _session;
    }

    public UnsignedInteger getLocalHandle()
    {
        return _localHandle;
    }

    public Object getLock()
    {
        return _session.getLock();
    }


    public long getSyncTimeout()
    {
        return _session.getSyncTimeout();
    }

    public void waitUntil(Predicate predicate) throws TimeoutException, InterruptedException
    {
        _session.waitUntil(predicate);
    }

    public void waitUntil(Predicate predicate, long timeout) throws TimeoutException, InterruptedException
    {
        _session.waitUntil(predicate, timeout);
    }


    public void attach()
    {
        synchronized(getLock())
        {
            Attach attachToSend = new Attach();
            attachToSend.setName(getName());
            attachToSend.setRole(getRole());
            attachToSend.setHandle(getLocalHandle());
            attachToSend.setSource(getSource());
            attachToSend.setTarget(getTarget());
            attachToSend.setSndSettleMode(getSendingSettlementMode());
            attachToSend.setRcvSettleMode(getReceivingSettlementMode());
            attachToSend.setUnsettled(_localUnsettled);

            if(getRole() == Role.SENDER)
            {
                attachToSend.setInitialDeliveryCount(_deliveryCount);
            }

            switch(_state)
            {
                case DETACHED:
                    _state = State.ATTACH_SENT;
                    break;
                case ATTACH_RECVD:
                    _state = State.ATTACHED;
                    break;
                default:
                    // TODO ERROR
            }

            getSession().sendAttach(attachToSend);

            getLock().notifyAll();

        }

    }


    public void detach()
    {
        detach(null, false);
    }

    public void close()
    {
        detach(null, true);
    }

    public void close(Error error)
    {
        detach(error, true);
    }

    public void detach(Error error)
    {
        detach(error, false);
    }

    private void detach(Error error, boolean close)
    {
        synchronized(getLock())
        {
            //TODO
            switch(_state)
            {
                case ATTACHED:
                    _state = State.DETACH_SENT;
                    break;
                case DETACH_RECVD:
                    _state = State.DETACHED;
                    break;
                default:
                    return;
            }
            
            if (getSession().getState() != SessionState.END_RECVD)
            {
                Detach detach = new Detach();
                detach.setHandle(getLocalHandle());
                if(close)
                    detach.setClosed(close);
                detach.setError(error);

                getSession().sendDetach(detach);
            }

            getLock().notifyAll();
        }

    }




    public void setTransactionId(final Object txnId)
    {
        _flowTransactionId = txnId;
    }

    public void sendFlowConditional()
    {
        if(_lastSentCreditLimit != null)
        {
            UnsignedInteger clientsCredit = _lastSentCreditLimit.subtract(_deliveryCount);
            int i = _linkCredit.subtract(clientsCredit).compareTo(clientsCredit);
            if(i >=0)
            {
                sendFlow(_flowTransactionId != null);
            }
            else
            {
                getSession().sendFlowConditional();
            }
        }
        else
        {
            sendFlow(_flowTransactionId != null);
        }
    }


    public void sendFlow()
    {
        sendFlow(_flowTransactionId != null);
    }

    public void sendFlowWithEcho()
    {
        sendFlow(_flowTransactionId != null, true);
    }


    public void sendFlow(boolean setTransactionId)
    {
        sendFlow(setTransactionId, false);
    }

    public void sendFlow(boolean setTransactionId, boolean echo)
    {
        if(_state == State.ATTACHED || _state == State.ATTACH_SENT)
        {
            Flow flow = new Flow();
            flow.setLinkCredit(_linkCredit);
            flow.setDeliveryCount(_deliveryCount);
            flow.setEcho(echo);
            _lastSentCreditLimit = _linkCredit.add(_deliveryCount);
            flow.setAvailable(_available);
            flow.setDrain(_drain);
            if(setTransactionId)
            {
                flow.setProperties(Collections.singletonMap(Symbol.valueOf("txn-id"), _flowTransactionId));
            }
            flow.setHandle(getLocalHandle());
            getSession().sendFlow(flow);
        }
    }

    public T getLinkEventListener()
    {
        return _linkEventListener;
    }

    public void setLinkEventListener(final T linkEventListener)
    {
        synchronized(getLock())
        {
            _linkEventListener = linkEventListener;
        }
    }

    public DeliveryStateHandler getDeliveryStateHandler()
    {
        return _deliveryStateHandler;
    }

    public void setDeliveryStateHandler(final DeliveryStateHandler deliveryStateHandler)
    {
        synchronized(getLock())
        {
            _deliveryStateHandler = deliveryStateHandler;
        }
    }

    public void setSendingSettlementMode(SenderSettleMode sendingSettlementMode)
    {
        _sendingSettlementMode = sendingSettlementMode;
    }

    public SenderSettleMode getSendingSettlementMode()
    {
        return _sendingSettlementMode;
    }

    public ReceiverSettleMode getReceivingSettlementMode()
    {
        return _receivingSettlementMode;
    }

    public void setReceivingSettlementMode(ReceiverSettleMode receivingSettlementMode)
    {
        _receivingSettlementMode = receivingSettlementMode;
    }

    public Map getInitialUnsettledMap()
    {
        return _initialUnsettledMap;
    }


    public abstract void flowStateChanged();

    public void setLocalUnsettled(Map unsettled)
    {
        _localUnsettled = unsettled;
    }

    @Override public String toString()
    {
        return "LinkEndpoint{" +
               "_name='" + _name + '\'' +
               ", _session=" + _session +
               ", _state=" + _state +
               ", _role=" + getRole() +
               ", _source=" + _source +
               ", _target=" + _target +
               ", _transferCount=" + _deliveryCount +
               ", _linkCredit=" + _linkCredit +
               ", _available=" + _available +
               ", _drain=" + _drain +
               ", _localHandle=" + _localHandle +
               ", _maxMessageSize=" + _maxMessageSize +
               '}';
    }
}
