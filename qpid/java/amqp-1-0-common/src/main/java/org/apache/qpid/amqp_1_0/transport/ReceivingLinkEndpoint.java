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

import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.*;


import java.util.*;

public class ReceivingLinkEndpoint extends LinkEndpoint<ReceivingLinkListener>
{


    private static class TransientState
    {

        UnsignedInteger _deliveryId;
        int _credit = 1;
        boolean _settled;

        private TransientState(final UnsignedInteger transferId)
        {
            _deliveryId = transferId;
        }

        void incrementCredit()
        {
            _credit++;
        }

        public int getCredit()
        {
            return _credit;
        }

        public UnsignedInteger getDeliveryId()
        {
            return _deliveryId;
        }

        public boolean isSettled()
        {
            return _settled;
        }

        public void setSettled(boolean settled)
        {
            _settled = settled;
        }
    }

    private Map<Binary, Object> _unsettledMap = new LinkedHashMap<Binary, Object>();
    private Map<Binary, TransientState> _unsettledIds = new LinkedHashMap<Binary, TransientState>();
    private boolean _creditWindow;
    private boolean _remoteDrain;
    private UnsignedInteger _remoteTransferCount;
    private UnsignedInteger _drainLimit;


    public ReceivingLinkEndpoint(final SessionEndpoint session, String name)
    {
        this(session,name,null);
    }

    public ReceivingLinkEndpoint(final SessionEndpoint session, String name, Map<Binary, Outcome> unsettledMap)
    {
        super(session, name, unsettledMap);
        setDeliveryCount(UnsignedInteger.valueOf(0));
        setLinkEventListener(ReceivingLinkListener.DEFAULT);
    }

    public ReceivingLinkEndpoint(final SessionEndpoint session, final Attach attach)
    {
        super(session, attach);
        setDeliveryCount(attach.getInitialDeliveryCount());
        setLinkEventListener(ReceivingLinkListener.DEFAULT);
        setSendingSettlementMode(attach.getSndSettleMode());
        setReceivingSettlementMode(attach.getRcvSettleMode());
    }


    @Override public Role getRole()
    {
        return Role.RECEIVER;
    }

    @Override
    public void receiveTransfer(final Transfer transfer, final Delivery delivery)
    {
        synchronized (getLock())
        {
            TransientState transientState;
            boolean existingState = _unsettledMap.containsKey(transfer.getDeliveryTag());
            _unsettledMap.put(transfer.getDeliveryTag(), transfer.getState());
            if(!existingState)
            {
                transientState = new TransientState(transfer.getDeliveryId());
                if(Boolean.TRUE.equals(transfer.getSettled()))
                {
                    transientState.setSettled(true);
                }
                _unsettledIds.put(transfer.getDeliveryTag(), transientState);
                setLinkCredit(getLinkCredit().subtract(UnsignedInteger.ONE));
                setDeliveryCount(getDeliveryCount().add(UnsignedInteger.ONE));

            }
            else
            {
                transientState = _unsettledIds.get(transfer.getDeliveryTag());
                transientState.incrementCredit();
                if(Boolean.TRUE.equals(transfer.getSettled()))
                {
                    transientState.setSettled(true);
                }
            }

            if(transientState.isSettled())
            {
                _unsettledMap.remove(transfer.getDeliveryTag());
            }
            getLinkEventListener().messageTransfer(transfer);


            getLock().notifyAll();
        }
    }

    @Override public void receiveFlow(final Flow flow)
    {
        synchronized (getLock())
        {
            super.receiveFlow(flow);
            _remoteDrain = Boolean.TRUE.equals((Boolean)flow.getDrain());
            setAvailable(flow.getAvailable());
            _remoteTransferCount = flow.getDeliveryCount();
            getLock().notifyAll();
        }
    }


    public boolean isDrained()
    {
        return getDrain() && getDeliveryCount().equals(getDrainLimit());
    }

    @Override
    public void settledByPeer(final Binary deliveryTag)
    {
        synchronized (getLock())
        {
            // TODO XXX : need to do anything about the window here?
            if(settled(deliveryTag) && _creditWindow)
            {
                sendFlowConditional();
            }
        }
    }

    public boolean settled(final Binary deliveryTag)
    {
        synchronized(getLock())
        {
            boolean deleted;
            if(deleted = (_unsettledIds.remove(deliveryTag) != null))
            {
                _unsettledMap.remove(deliveryTag);

                getLock().notifyAll();
            }

            return deleted;
        }
    }

    public void updateDisposition(final Binary deliveryTag, DeliveryState state, boolean settled)
    {
        synchronized(getLock())
        {
            if(_unsettledMap.containsKey(deliveryTag))
            {
                boolean outcomeUpdate = false;
                Outcome outcome=null;
                if(state instanceof Outcome)
                {
                    outcome = (Outcome)state;
                }
                else if(state instanceof TransactionalState)
                {
                    // TODO? Is this correct
                    outcome = ((TransactionalState)state).getOutcome();
                }

                if(outcome != null)
                {
                    Object oldOutcome = _unsettledMap.put(deliveryTag, outcome);
                    outcomeUpdate = !outcome.equals(oldOutcome);
                }




                TransientState transientState = _unsettledIds.get(deliveryTag);
                if(outcomeUpdate || settled)
                {

                    final UnsignedInteger transferId = transientState.getDeliveryId();

                    getSession().updateDisposition(getRole(), transferId, transferId, state, settled);
                }


                if(settled)
                {

                    if(settled(deliveryTag))
                    {
                        if(_creditWindow)
                        {
                            setLinkCredit(getLinkCredit().add(UnsignedInteger.ONE));
                            sendFlowConditional();
                        }
                        else
                        {
                            getSession().sendFlowConditional();
                        }
                    }
                }
                getLock().notifyAll();
            }
            else
            {
                TransientState transientState = _unsettledIds.get(deliveryTag);
                if(_creditWindow)
                {
                    setLinkCredit(getLinkCredit().add(UnsignedInteger.ONE));
                    sendFlowConditional();
                }

            }
        }

    }


    public void setCreditWindow()
    {
        setCreditWindow(true);
    }
    public void setCreditWindow(boolean window)
    {

        _creditWindow = window;
        sendFlowConditional();

    }

    public void drain()
    {
        synchronized (getLock())
        {
            setDrain(true);
            _creditWindow = false;
            _drainLimit = getDeliveryCount().add(getLinkCredit());
            sendFlow();
            getLock().notifyAll();
        }
    }

    @Override
    public void receiveDeliveryState(final Delivery unsettled, final DeliveryState state, final Boolean settled)
    {
        super.receiveDeliveryState(unsettled, state, settled);
        if(_creditWindow)
        {
            if(Boolean.TRUE.equals(settled))
            {
                setLinkCredit(getLinkCredit().add(UnsignedInteger.ONE));
                sendFlowConditional();
            }
        }
    }

    public void requestTransactionalSend(Object txnId)
    {
        synchronized (getLock())
        {
            setDrain(true);
            _creditWindow = false;
            setTransactionId(txnId);
            sendFlow();
            getLock().notifyAll();
        }
    }

    private void sendFlow(final Object transactionId)
    {
        sendFlow();
    }


    public void clearDrain()
    {
        synchronized (getLock())
        {
            setDrain(false);
            sendFlow();
            getLock().notifyAll();
        }
    }

    public void updateAllDisposition(Binary deliveryTag, Outcome outcome, boolean settled)
    {
        synchronized(getLock())
        {
            if(!_unsettledIds.isEmpty())
            {
                Binary firstTag = _unsettledIds.keySet().iterator().next();
                Binary lastTag = deliveryTag;
                updateDispositions(firstTag, lastTag, (DeliveryState) outcome, settled);
            }
        }
    }

    private void updateDispositions(Binary firstTag, Binary lastTag, DeliveryState state, boolean settled)
    {
        SortedMap<UnsignedInteger, UnsignedInteger> ranges = new TreeMap<UnsignedInteger,UnsignedInteger>();

        synchronized(getLock())
        {

            Iterator<Binary> iter = _unsettledIds.keySet().iterator();
            List<Binary> tagsToUpdate = new ArrayList<Binary>();
            Binary tag = null;

            while(iter.hasNext() && !(tag = iter.next()).equals(firstTag));

            if(firstTag.equals(tag))
            {
                tagsToUpdate.add(tag);

                UnsignedInteger deliveryId = _unsettledIds.get(firstTag).getDeliveryId();

                UnsignedInteger first = deliveryId;
                UnsignedInteger last = first;

                while(iter.hasNext())
                {
                    tag = iter.next();
                    tagsToUpdate.add(tag);

                    deliveryId = _unsettledIds.get(firstTag).getDeliveryId();

                    if(deliveryId.equals(last.add(UnsignedInteger.ONE)))
                    {
                        last = deliveryId;
                    }
                    else
                    {
                        ranges.put(first,last);
                        first = last = deliveryId;
                    }

                    if(tag.equals(lastTag))
                    {
                        break;
                    }
                }

                ranges.put(first,last);
            }

            if(settled)
            {

                for(Binary deliveryTag : tagsToUpdate)
                {
                    if(settled(deliveryTag) && _creditWindow)
                    {
                        setLinkCredit(getLinkCredit().add(UnsignedInteger.valueOf(1)));
                    }
                }
                sendFlowConditional();
            }



            for(Map.Entry<UnsignedInteger,UnsignedInteger> range : ranges.entrySet())
            {
                getSession().updateDisposition(getRole(), range.getKey(), range.getValue(), state, settled);
            }


            getLock().notifyAll();
        }

    }

    @Override
    public void settle(Binary deliveryTag)
    {
        super.settle(deliveryTag);
        if(_creditWindow)
        {
             sendFlowConditional();
        }

    }

    public UnsignedInteger getDrainLimit()
    {
        return _drainLimit;
    }
}
