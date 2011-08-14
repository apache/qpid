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

import org.apache.qpid.amqp_1_0.messaging.SectionDecoder;
import org.apache.qpid.amqp_1_0.messaging.SectionDecoderImpl;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoderImpl;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DistributionMode;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;
import org.apache.qpid.amqp_1_0.type.transport.ReceiverSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Session
{
    private SessionEndpoint _endpoint;
    private List<Receiver> _receivers = new ArrayList<Receiver>();
    private List<Sender> _senders = new ArrayList<Sender>();
    private SectionEncoder _sectionEncoder;
    private SectionDecoder _sectionDecoder;
    private TransactionController _sessionLocalTC;

    public Session(final Connection connection, String name)
    {
        _endpoint = connection.getEndpoint().createSession(name);
        _sectionEncoder = new SectionEncoderImpl(connection.getEndpoint().getDescribedTypeRegistry());
        _sectionDecoder = new SectionDecoderImpl(connection.getEndpoint().getDescribedTypeRegistry());
    }


    public synchronized Sender createSender(final String targetName) throws Sender.SenderCreationException
    {
        return createSender(targetName, false);
    }

    public synchronized Sender createSender(final String targetName, boolean synchronous) throws Sender.SenderCreationException
    {

        final String sourceName = UUID.randomUUID().toString();
        return new Sender(this, targetName+"<-"+sourceName, targetName, sourceName, synchronous);

    }

    public synchronized Sender createSender(final String targetName, int window) throws Sender.SenderCreationException
    {
         final String sourceName = UUID.randomUUID().toString();
         return new Sender(this, targetName+"<-"+sourceName, targetName, sourceName, window);

    }

    public Sender createSender(String targetName, int window, AcknowledgeMode mode) throws Sender.SenderCreationException
    {

        return createSender(targetName, window, mode, null);
    }

    public Sender createSender(String targetName, int window, AcknowledgeMode mode, String linkName) throws Sender.SenderCreationException
    {
        return createSender(targetName, window, mode, linkName, null);
    }
    public Sender createSender(String targetName, int window, AcknowledgeMode mode, String linkName, Map<Binary, Outcome> unsettled) throws Sender.SenderCreationException
    {
        return createSender(targetName, window, mode, linkName, false, unsettled);
    }

    public Sender createSender(String targetName, int window, AcknowledgeMode mode, String linkName,
                               boolean isDurable, Map<Binary, Outcome> unsettled) throws Sender.SenderCreationException
    {
        return new Sender(this, linkName == null ? "->" + targetName + '(' + UUID.randomUUID().toString()+')': linkName,
                          targetName, null, window, mode, isDurable, unsettled);

    }


    public Receiver createReceiver(final String sourceAddr)
    {
        return createReceiver(sourceAddr, null, AcknowledgeMode.ALO);
    }


    public Receiver createReceiver(final String queue, final AcknowledgeMode mode)
    {
        return createReceiver(queue, null, mode);
    }

    public Receiver createReceiver(final String queue, final AcknowledgeMode mode, String linkName)
    {
        return createReceiver(queue, null, mode, linkName);
    }

    public Receiver createReceiver(final String queue, final AcknowledgeMode mode, String linkName, boolean isDurable)
    {
        return createReceiver(queue, null, mode, linkName, isDurable);
    }

    public Receiver createReceiver(final String queue, final AcknowledgeMode mode, String linkName,
                                   boolean isDurable, Map<Binary, Outcome> unsettled)
    {
        return createReceiver(queue, null, mode, linkName, isDurable, unsettled);
    }


    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode)
    {
        return createReceiver(sourceAddr, mode, AcknowledgeMode.ALO);
    }

    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode, String linkName)
    {
        return createReceiver(sourceAddr, mode, AcknowledgeMode.ALO, linkName);
    }


    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode,
                                            final AcknowledgeMode ackMode)
    {
        return createReceiver(sourceAddr, mode, ackMode, null);
    }

    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode,
                                            final AcknowledgeMode ackMode, String linkName)
    {
        return createReceiver(sourceAddr,mode, ackMode, linkName, false);
    }

    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode,
                                            final AcknowledgeMode ackMode, String linkName, boolean isDurable)
    {
        return createReceiver(sourceAddr, mode, ackMode, linkName, isDurable, null);
    }

    private synchronized Receiver createReceiver(final String sourceAddr, DistributionMode mode,
                                            final AcknowledgeMode ackMode, String linkName, boolean isDurable,
                                            Map<Binary, Outcome> unsettled)
    {

        final Receiver receiver =
                new Receiver(this, linkName == null
                                    ? sourceAddr + "-> (" + UUID.randomUUID().toString() + ")"
                                    : linkName,
                             null, sourceAddr, mode, ackMode, isDurable, unsettled);
        _receivers.add(receiver);
        return receiver;

    }

    public synchronized Receiver createCopyingReceiver(final String sourceAddr)
    {
        return createReceiver(sourceAddr, StdDistMode.COPY);
    }

    public synchronized Receiver createMovingReceiver(final String sourceAddr)
    {
        return createReceiver(sourceAddr, StdDistMode.MOVE);
    }

    public Receiver createTemporaryQueueReceiver()
    {
        Source source = new Source();
        source.setDynamic(true);

        final Receiver receiver = new Receiver(this, "tempSender"+UUID.randomUUID().toString(), new Target(),
                                               source, AcknowledgeMode.ALO);
        _receivers.add(receiver);
        return receiver;
    }

    public SessionEndpoint getEndpoint()
    {
        return _endpoint;
    }

    public synchronized void close()
    {
        try
        {
            for(Sender sender : new ArrayList<Sender>(_senders))
            {
                sender.close();
            }
            for(Receiver receiver : new ArrayList<Receiver>(_receivers))
            {
                receiver.close();
            }
            if(_sessionLocalTC != null)
            {
                _sessionLocalTC.close();
            }
            _endpoint.end();
        }
        catch (Sender.SenderClosingException e)
        {
// TODO
            e.printStackTrace();
        }

        //TODO

    }

    void removeSender(Sender sender)
    {
        _senders.remove(sender);
    }

    void removeReceiver(Receiver receiver)
    {
        _receivers.remove(receiver);
    }

    public SectionEncoder getSectionEncoder()
    {
        return _sectionEncoder;
    }

    public SectionDecoder getSectionDecoder()
    {
        return _sectionDecoder;
    }


    public Transaction createSessionLocalTransaction()
    {
        TransactionController localController = getSessionLocalTransactionController();
        return localController.beginTransaction();
    }

    private TransactionController getSessionLocalTransactionController()
    {
        if(_sessionLocalTC == null)
        {
            _sessionLocalTC = createSessionLocalTransactionController();
        }
        return _sessionLocalTC;
    }

    private TransactionController createSessionLocalTransactionController()
    {
        String name = "txnControllerLink";
        SendingLinkEndpoint tcLinkEndpoint = _endpoint.createTransactionController(name, TxnCapability.LOCAL_TXN,
                                                                                   TxnCapability.MULTI_TXNS_PER_SSN);
        tcLinkEndpoint.setReceivingSettlementMode(ReceiverSettleMode.FIRST);
        tcLinkEndpoint.setSendingSettlementMode(SenderSettleMode.UNSETTLED);
        tcLinkEndpoint.attach();
        return new TransactionController(this, tcLinkEndpoint);
    }


}
