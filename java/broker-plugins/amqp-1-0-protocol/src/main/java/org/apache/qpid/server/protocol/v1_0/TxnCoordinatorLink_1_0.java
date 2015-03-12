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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.amqp_1_0.messaging.SectionDecoder;
import org.apache.qpid.amqp_1_0.messaging.SectionDecoderImpl;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkListener;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.transaction.Declare;
import org.apache.qpid.amqp_1_0.type.transaction.Declared;
import org.apache.qpid.amqp_1_0.type.transaction.Discharge;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class TxnCoordinatorLink_1_0 implements ReceivingLinkListener, Link_1_0
{
    private static final Logger _logger = LoggerFactory.getLogger(TxnCoordinatorLink_1_0.class);
    private VirtualHostImpl _vhost;
    private ReceivingLinkEndpoint _endpoint;

    private ArrayList<Transfer> _incompleteMessage;
    private SectionDecoder _sectionDecoder;
    private LinkedHashMap<Integer, ServerTransaction> _openTransactions;
    private Session_1_0 _session;


    public TxnCoordinatorLink_1_0(VirtualHostImpl vhost,
                                  Session_1_0 session_1_0, ReceivingLinkEndpoint endpoint,
                                  LinkedHashMap<Integer, ServerTransaction> openTransactions)
    {
        _vhost = vhost;
        _session = session_1_0;
        _endpoint  = endpoint;
        _sectionDecoder = new SectionDecoderImpl(endpoint.getSession().getConnection().getDescribedTypeRegistry());
        _openTransactions = openTransactions;
    }

    public void messageTransfer(Transfer xfr)
    {
        // TODO - cope with fragmented messages

        ByteBuffer payload = null;


        if(Boolean.TRUE.equals(xfr.getMore()) && _incompleteMessage == null)
        {
            _incompleteMessage = new ArrayList<Transfer>();
            _incompleteMessage.add(xfr);
            return;
        }
        else if(_incompleteMessage != null)
        {
            _incompleteMessage.add(xfr);
            if(Boolean.TRUE.equals(xfr.getMore()))
            {
                return;
            }

            int size = 0;
            for(Transfer t : _incompleteMessage)
            {
                size += t.getPayload().limit();
            }
            payload = ByteBuffer.allocate(size);
            for(Transfer t : _incompleteMessage)
            {
                payload.put(t.getPayload().duplicate());
            }
            payload.flip();
            _incompleteMessage=null;

        }
        else
        {
            payload = xfr.getPayload();
        }


        // Only interested int he amqp-value section that holds the message to the coordinator
        try
        {
            List<Section> sections = _sectionDecoder.parseAll(payload);

            for(Section section : sections)
            {
                if(section instanceof AmqpValue)
                {
                    Object command = ((AmqpValue) section).getValue();

                    if(command instanceof Declare)
                    {
                        Integer txnId = Integer.valueOf(0);
                        Iterator<Integer> existingTxn  = _openTransactions.keySet().iterator();
                        while(existingTxn.hasNext())
                        {
                            txnId = existingTxn.next();
                        }
                        txnId = Integer.valueOf(txnId.intValue() + 1);

                        _openTransactions.put(txnId, new LocalTransaction(_vhost.getMessageStore()));

                        Declared state = new Declared();



                        state.setTxnId(_session.integerToBinary(txnId));
                        _endpoint.updateDisposition(xfr.getDeliveryTag(), state, true);

                    }
                    else if(command instanceof Discharge)
                    {
                        Discharge discharge = (Discharge) command;

                        DeliveryState state = xfr.getState();
                        discharge(_session.binaryToInteger(discharge.getTxnId()), discharge.getFail());
                        _endpoint.updateDisposition(xfr.getDeliveryTag(), new Accepted(), true);

                    }
                }
            }

        }
        catch (AmqpErrorException e)
        {
            //TODO
            _logger.error("AMQP error", e);
        }

    }

    public void remoteDetached(LinkEndpoint endpoint, Detach detach)
    {
        //TODO
        endpoint.detach();
    }

    private Error discharge(Integer transactionId, boolean fail)
    {
        Error error = null;
        ServerTransaction txn = _openTransactions.get(transactionId);
        if(txn != null)
        {
            if(fail)
            {
                txn.rollback();
            }
            else
            {
                txn.commit();
            }
            _openTransactions.remove(transactionId);
        }
        else
        {
            error = new Error();
            error.setCondition(AmqpError.NOT_FOUND);
            error.setDescription("Unknown transactionId" + transactionId);
        }
        return error;
    }



    public void start()
    {
        _endpoint.setLinkCredit(UnsignedInteger.ONE);
        _endpoint.setCreditWindow();
    }
}
