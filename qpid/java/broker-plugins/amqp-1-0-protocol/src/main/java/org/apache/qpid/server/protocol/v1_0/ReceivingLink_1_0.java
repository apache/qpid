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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.qpid.amqp_1_0.messaging.SectionDecoderImpl;
import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkListener;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.ReceiverSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.store.MessageHandle;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class ReceivingLink_1_0 implements ReceivingLinkListener, Link_1_0, DeliveryStateHandler
{
    private VirtualHostImpl _vhost;

    private ReceivingDestination _destination;
    private SectionDecoderImpl _sectionDecoder;
    private volatile ReceivingLinkAttachment _attachment;


    private ArrayList<Transfer> _incompleteMessage;
    private TerminusDurability _durability;

    private Map<Binary, Outcome> _unsettledMap = Collections.synchronizedMap(new HashMap<Binary, Outcome>());
    private boolean _resumedMessage;
    private Binary _messageDeliveryTag;
    private ReceiverSettleMode _receivingSettlementMode;


    public ReceivingLink_1_0(ReceivingLinkAttachment receivingLinkAttachment, VirtualHostImpl vhost,
                             ReceivingDestination destination)
    {
        _vhost = vhost;
        _destination = destination;
        _attachment = receivingLinkAttachment;
        _receivingSettlementMode = receivingLinkAttachment.getEndpoint().getReceivingSettlementMode();

        receivingLinkAttachment.setDeliveryStateHandler(this);

        _durability = ((Target)receivingLinkAttachment.getTarget()).getDurable();

        _sectionDecoder = new SectionDecoderImpl(receivingLinkAttachment.getEndpoint().getSession().getConnection().getDescribedTypeRegistry());


    }

    public void messageTransfer(Transfer xfr)
    {
        // TODO - cope with fragmented messages

        List<ByteBuffer> fragments = null;



        if(Boolean.TRUE.equals(xfr.getMore()) && _incompleteMessage == null)
        {
            _incompleteMessage = new ArrayList<Transfer>();
            _incompleteMessage.add(xfr);
            _resumedMessage = Boolean.TRUE.equals(xfr.getResume());
            _messageDeliveryTag = xfr.getDeliveryTag();
            return;
        }
        else if(_incompleteMessage != null)
        {
            _incompleteMessage.add(xfr);

            if(Boolean.TRUE.equals(xfr.getMore()))
            {
                return;
            }

            fragments = new ArrayList<ByteBuffer>(_incompleteMessage.size());
            for(Transfer t : _incompleteMessage)
            {
                fragments.add(t.getPayload());
            }
            _incompleteMessage=null;

        }
        else
        {
            _resumedMessage = Boolean.TRUE.equals(xfr.getResume());
            _messageDeliveryTag = xfr.getDeliveryTag();
            fragments = Collections.singletonList(xfr.getPayload());
        }

        if(_resumedMessage)
        {
            if(_unsettledMap.containsKey(_messageDeliveryTag))
            {
                Outcome outcome = _unsettledMap.get(_messageDeliveryTag);
                boolean settled = ReceiverSettleMode.FIRST.equals(getReceivingSettlementMode());
                getEndpoint().updateDisposition(_messageDeliveryTag, (DeliveryState) outcome, settled);
                if(settled)
                {
                    _unsettledMap.remove(_messageDeliveryTag);
                }
            }
            else
            {
                System.err.println("UNEXPECTED!!");
                System.err.println("Delivery Tag: " + _messageDeliveryTag);
                System.err.println("_unsettledMap: " + _unsettledMap);

            }
        }
        else
        {
            MessageMetaData_1_0 mmd = null;
            List<ByteBuffer> immutableSections = new ArrayList<ByteBuffer>(3);
            mmd = new MessageMetaData_1_0(fragments.toArray(new ByteBuffer[fragments.size()]),
                    _sectionDecoder,
                    immutableSections);

            MessageHandle<MessageMetaData_1_0> handle = _vhost.getMessageStore().addMessage(mmd);

            boolean skipping = true;
            int offset = 0;

            for(ByteBuffer bareMessageBuf : immutableSections)
            {
                handle.addContent(bareMessageBuf.duplicate());
                offset += bareMessageBuf.remaining();
            }
            final StoredMessage<MessageMetaData_1_0> storedMessage = handle.allContentAdded();
            Message_1_0 message = new Message_1_0(storedMessage, fragments, getSession().getConnection().getReference());
            MessageReference<Message_1_0> reference = message.newReference();

            Binary transactionId = null;
            org.apache.qpid.amqp_1_0.type.DeliveryState xfrState = xfr.getState();
            if(xfrState != null)
            {
                if(xfrState instanceof TransactionalState)
                {
                    transactionId = ((TransactionalState)xfrState).getTxnId();
                }
            }

            ServerTransaction transaction = null;
            if(transactionId != null)
            {
                transaction = getSession().getTransaction(transactionId);
            }
            else
            {
                Session_1_0 session = getSession();
                transaction = session != null ? session.getTransaction(null) : new AutoCommitTransaction(_vhost.getMessageStore());
            }

            Outcome outcome = _destination.send(message, transaction);

            DeliveryState resultantState;

            if(transactionId == null)
            {
                resultantState = (DeliveryState) outcome;
            }
            else
            {
                TransactionalState transactionalState = new TransactionalState();
                transactionalState.setOutcome(outcome);
                transactionalState.setTxnId(transactionId);
                resultantState = transactionalState;

            }


            boolean settled = transaction instanceof AutoCommitTransaction && ReceiverSettleMode.FIRST.equals(getReceivingSettlementMode());

            final Binary deliveryTag = xfr.getDeliveryTag();

            if(!settled)
            {
                _unsettledMap.put(deliveryTag, outcome);
            }

            getEndpoint().updateDisposition(deliveryTag, resultantState, settled);

            getSession().getConnectionModel().registerMessageReceived(message.getSize(), message.getArrivalTime());

            if(!(transaction instanceof AutoCommitTransaction))
            {
                ServerTransaction.Action a;
                transaction.addPostTransactionAction(new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        getEndpoint().updateDisposition(deliveryTag, null, true);
                    }

                    public void onRollback()
                    {
                        getEndpoint().updateDisposition(deliveryTag, null, true);
                    }
                });
            }

            reference.release();
        }
    }

    private ReceiverSettleMode getReceivingSettlementMode()
    {
        return _receivingSettlementMode;
    }

    public void remoteDetached(LinkEndpoint endpoint, Detach detach)
    {
        //TODO
        // if not durable or close
        if(!TerminusDurability.UNSETTLED_STATE.equals(_durability) ||
           (detach != null && Boolean.TRUE.equals(detach.getClosed())))
        {
            endpoint.close();
        }
        else if(detach == null || detach.getError() != null)
        {
            _attachment = null;
        }
    }

    public void start()
    {
        getEndpoint().setLinkCredit(UnsignedInteger.valueOf(_destination.getCredit()));
        getEndpoint().setCreditWindow();
    }

    public ReceivingLinkEndpoint getEndpoint()
    {
        return _attachment.getEndpoint();
    }


    public Session_1_0 getSession()
    {
        ReceivingLinkAttachment attachment = _attachment;
        return attachment == null ? null : attachment.getSession();
    }

    public void handle(Binary deliveryTag, DeliveryState state, Boolean settled)
    {
        if(Boolean.TRUE.equals(settled))
        {
            _unsettledMap.remove(deliveryTag);
        }
    }

    public void setLinkAttachment(ReceivingLinkAttachment linkAttachment)
    {
        _attachment = linkAttachment;
        _receivingSettlementMode = linkAttachment.getEndpoint().getReceivingSettlementMode();
        ReceivingLinkEndpoint endpoint = linkAttachment.getEndpoint();
        Map initialUnsettledMap = endpoint.getInitialUnsettledMap();

        Map<Binary, Outcome> unsettledCopy = new HashMap<Binary, Outcome>(_unsettledMap);
        for(Map.Entry<Binary, Outcome> entry : unsettledCopy.entrySet())
        {
            Binary deliveryTag = entry.getKey();
            if(!initialUnsettledMap.containsKey(deliveryTag))
            {
                _unsettledMap.remove(deliveryTag);
            }
        }

    }

    public Map getUnsettledOutcomeMap()
    {
        return _unsettledMap;
    }
}
