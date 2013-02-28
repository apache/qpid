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

import java.text.MessageFormat;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SessionEventListener;
import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.transaction.Coordinator;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;
import org.apache.qpid.amqp_1_0.type.transport.*;

import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.*;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;

public class Session_1_0 implements SessionEventListener, AMQSessionModel, LogSubject
{
    private static final Symbol LIFETIME_POLICY = Symbol.valueOf("lifetime-policy");
    private VirtualHost _vhost;
    private AutoCommitTransaction _transaction;

    private final LinkedHashMap<Integer, ServerTransaction> _openTransactions =
            new LinkedHashMap<Integer, ServerTransaction>();
    private final Connection_1_0 _connection;
    private UUID _id = UUID.randomUUID();


    public Session_1_0(VirtualHost vhost, final Connection_1_0 connection)
    {
        _vhost = vhost;
        _transaction = new AutoCommitTransaction(vhost.getMessageStore());
        _connection = connection;

    }

    public void remoteLinkCreation(final LinkEndpoint endpoint)
    {


        Destination destination;
        Link_1_0 link = null;
        Error error = null;

        final LinkRegistry linkRegistry = _vhost.getLinkRegistry(endpoint.getSession().getConnection().getRemoteContainerId());


        if(endpoint.getRole() == Role.SENDER)
        {

            SendingLink_1_0 previousLink = linkRegistry.getDurableSendingLink(endpoint.getName());

            if(previousLink == null)
            {

                Target target = (Target) endpoint.getTarget();
                Source source = (Source) endpoint.getSource();


                if(source != null)
                {
                    if(Boolean.TRUE.equals(source.getDynamic()))
                    {
                        AMQQueue tempQueue = createTemporaryQueue(source.getDynamicNodeProperties());
                        source.setAddress(tempQueue.getName());
                    }
                    String addr = source.getAddress();
                    AMQQueue queue = _vhost.getQueueRegistry().getQueue(addr);
                    if(queue != null)
                    {

                        destination = new QueueDestination(queue);



                    }
                    else
                    {
                        Exchange exchg = _vhost.getExchangeRegistry().getExchange(addr);
                        if(exchg != null)
                        {
                            destination = new ExchangeDestination(exchg, source.getDurable(), source.getExpiryPolicy());
                        }
                        else
                        {

                            endpoint.setSource(null);
                            destination = null;
                        }
                    }

                }
                else
                {
                    destination = null;
                }

                if(destination != null)
                {
                    final SendingLinkEndpoint sendingLinkEndpoint = (SendingLinkEndpoint) endpoint;
                    try
                    {
                        final SendingLink_1_0 sendingLink = new SendingLink_1_0(new SendingLinkAttachment(this, sendingLinkEndpoint),
                                                                                _vhost,
                                                                                (SendingDestination) destination
                        );
                        sendingLinkEndpoint.setLinkEventListener(sendingLink);
                        link = sendingLink;
                        if(TerminusDurability.UNSETTLED_STATE.equals(source.getDurable()))
                        {
                            linkRegistry.registerSendingLink(endpoint.getName(), sendingLink);
                        }
                    }
                    catch(AmqpErrorException e)
                    {
                        e.printStackTrace();
                        destination = null;
                        sendingLinkEndpoint.setSource(null);
                        error = e.getError();
                    }
                }
            }
            else
            {
                Source newSource = (Source) endpoint.getSource();

                Source oldSource = (Source) previousLink.getEndpoint().getSource();
                final TerminusDurability newSourceDurable = newSource == null ? null : newSource.getDurable();
                if(newSourceDurable != null)
                {
                    oldSource.setDurable(newSourceDurable);
                    if(newSourceDurable.equals(TerminusDurability.NONE))
                    {
                        linkRegistry.unregisterSendingLink(endpoint.getName());
                    }
                }
                endpoint.setSource(oldSource);
                SendingLinkEndpoint sendingLinkEndpoint = (SendingLinkEndpoint) endpoint;
                previousLink.setLinkAttachment(new SendingLinkAttachment(this, sendingLinkEndpoint));
                sendingLinkEndpoint.setLinkEventListener(previousLink);
                link = previousLink;
                endpoint.setLocalUnsettled(previousLink.getUnsettledOutcomeMap());
            }
        }
        else
        {
            if(endpoint.getTarget() instanceof Coordinator)
            {
                Coordinator coordinator = (Coordinator) endpoint.getTarget();
                TxnCapability[] capabilities = coordinator.getCapabilities();
                boolean localTxn = false;
                boolean multiplePerSession = false;
                if(capabilities != null)
                {
                    for(TxnCapability capability : capabilities)
                    {
                        if(capability.equals(TxnCapability.LOCAL_TXN))
                        {
                            localTxn = true;
                        }
                        else if(capability.equals(TxnCapability.MULTI_TXNS_PER_SSN))
                        {
                            multiplePerSession = true;
                        }
                        else
                        {
                            error = new Error();
                            error.setCondition(AmqpError.NOT_IMPLEMENTED);
                            error.setDescription("Unsupported capability: " + capability);
                            break;
                        }
                    }
                }

       /*         if(!localTxn)
                {
                    capabilities.add(TxnCapabilities.LOCAL_TXN);
                }*/

                final ReceivingLinkEndpoint receivingLinkEndpoint = (ReceivingLinkEndpoint) endpoint;
                final TxnCoordinatorLink_1_0 coordinatorLink =
                        new TxnCoordinatorLink_1_0(_vhost, this, receivingLinkEndpoint, _openTransactions);
                receivingLinkEndpoint.setLinkEventListener(coordinatorLink);
                link = coordinatorLink;


            }
            else
            {

                ReceivingLink_1_0 previousLink = linkRegistry.getDurableReceivingLink(endpoint.getName());

                if(previousLink == null)
                {

                    Target target = (Target) endpoint.getTarget();

                    if(target != null)
                    {
                        if(Boolean.TRUE.equals(target.getDynamic()))
                        {

                            AMQQueue tempQueue = createTemporaryQueue(target.getDynamicNodeProperties());
                            target.setAddress(tempQueue.getName());
                        }

                        String addr = target.getAddress();
                        Exchange exchg = _vhost.getExchangeRegistry().getExchange(addr);
                        if(exchg != null)
                        {
                            destination = new ExchangeDestination(exchg, target.getDurable(),
                                                                  target.getExpiryPolicy());
                        }
                        else
                        {
                            AMQQueue queue = _vhost.getQueueRegistry().getQueue(addr);
                            if(queue != null)
                            {

                                destination = new QueueDestination(queue);
                            }
                            else
                            {
                                endpoint.setTarget(null);
                                destination = null;
                            }

                        }


                    }
                    else
                    {
                        destination = null;
                    }
                    if(destination != null)
                    {
                        final ReceivingLinkEndpoint receivingLinkEndpoint = (ReceivingLinkEndpoint) endpoint;
                        final ReceivingLink_1_0 receivingLink = new ReceivingLink_1_0(new ReceivingLinkAttachment(this, receivingLinkEndpoint), _vhost,
                                (ReceivingDestination) destination);
                        receivingLinkEndpoint.setLinkEventListener(receivingLink);
                        link = receivingLink;
                        if(TerminusDurability.UNSETTLED_STATE.equals(target.getDurable()))
                        {
                            linkRegistry.registerReceivingLink(endpoint.getName(), receivingLink);
                        }
                    }
                }
                else
                {
                    ReceivingLinkEndpoint receivingLinkEndpoint = (ReceivingLinkEndpoint) endpoint;
                    previousLink.setLinkAttachment(new ReceivingLinkAttachment(this, receivingLinkEndpoint));
                    receivingLinkEndpoint.setLinkEventListener(previousLink);
                    link = previousLink;
                    endpoint.setLocalUnsettled(previousLink.getUnsettledOutcomeMap());

                }
            }
        }

        endpoint.attach();

        if(link == null)
        {
            if(error == null)
            {
                error = new Error();
                error.setCondition(AmqpError.NOT_FOUND);
            }
            endpoint.detach(error);
        }
        else
        {
            link.start();
        }
    }


    private AMQQueue createTemporaryQueue(Map properties)
    {
        final String queueName = UUID.randomUUID().toString();
        AMQQueue queue = null;
        try
        {
            LifetimePolicy lifetimePolicy = properties == null
                                            ? null
                                            : (LifetimePolicy) properties.get(LIFETIME_POLICY);

            final AMQQueue tempQueue = queue = AMQQueueFactory.createAMQQueueImpl( UUIDGenerator.generateQueueUUID(queueName, _vhost.getName()),
                                                                                   queueName,
                                                                                   false, // durable
                                                                                   null, // owner
                                                                                   false, // autodelete
                                                                                   false, // exclusive
                                                                                   _vhost,
                                                                                   properties);



            if (lifetimePolicy == null || lifetimePolicy instanceof DeleteOnClose)
            {
                final Connection_1_0.Task deleteQueueTask =
                        new Connection_1_0.Task()
                        {
                            public void doTask(Connection_1_0 session)
                            {
                                if (_vhost.getQueueRegistry().getQueue(queueName) == tempQueue)
                                {
                                    try
                                    {
                                        tempQueue.delete();
                                    }
                                    catch (AMQException e)
                                    {
                                        e.printStackTrace();  //TODO.
                                    }
                                }
                            }
                        };

                _connection.addConnectionCloseTask(deleteQueueTask);

                queue.addQueueDeleteTask(new AMQQueue.Task()
                {
                    public void doTask(AMQQueue queue)
                    {
                        _connection.removeConnectionCloseTask(deleteQueueTask);
                    }


                });
            }
            else if(lifetimePolicy instanceof DeleteOnNoLinks)
            {

            }
            else if(lifetimePolicy instanceof DeleteOnNoMessages)
            {

            }
            else if(lifetimePolicy instanceof DeleteOnNoLinksOrMessages)
            {

            }
        }
        catch (AMQSecurityException e)
        {
            e.printStackTrace();  //TODO.
        } catch (AMQException e)
        {
            e.printStackTrace();  //TODO
        }

        return queue;
    }

    public ServerTransaction getTransaction(Binary transactionId)
    {
        // TODO should treat invalid id differently to null
        ServerTransaction transaction = _openTransactions.get(binaryToInteger(transactionId));
        return transaction == null ? _transaction : transaction;
    }

    public void remoteEnd(End end)
    {
        Iterator<Map.Entry<Integer, ServerTransaction>> iter = _openTransactions.entrySet().iterator();

        while(iter.hasNext())
        {
            Map.Entry<Integer, ServerTransaction> entry = iter.next();
            entry.getValue().rollback();
            iter.remove();
        }

        _connection.sessionEnded(this);

    }

    Integer binaryToInteger(final Binary txnId)
    {
        if(txnId == null)
        {
            return null;
        }

        if(txnId.getLength() > 4)
            throw new IllegalArgumentException();

        int id = 0;
        byte[] data = txnId.getArray();
        for(int i = 0; i < txnId.getLength(); i++)
        {
            id <<= 8;
            id += data[i+txnId.getArrayOffset()];
        }

        return id;

    }

    Binary integerToBinary(final int txnId)
    {
        byte[] data = new byte[4];
        data[3] = (byte) (txnId & 0xff);
        data[2] = (byte) ((txnId & 0xff00) >> 8);
        data[1] = (byte) ((txnId & 0xff0000) >> 16);
        data[0] = (byte) ((txnId & 0xff000000) >> 24);
        return new Binary(data);

    }

    public void forceEnd()
    {
    }


    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public AMQConnectionModel getConnectionModel()
    {
        return _connection.getModel();
    }

    @Override
    public String getClientID()
    {
        // TODO
        return "";
    }

    @Override
    public void close() throws AMQException
    {
        // TODO - required for AMQSessionModel / management initiated closing
    }

    @Override
    public LogSubject getLogSubject()
    {
        return this;
    }

    @Override
    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose) throws AMQException
    {
        // TODO - required for AMQSessionModel / long running transaction detection
    }

    @Override
    public void block(AMQQueue queue)
    {
        // TODO - required for AMQSessionModel / producer side flow control
    }

    @Override
    public void unblock(AMQQueue queue)
    {
        // TODO - required for AMQSessionModel / producer side flow control
    }

    @Override
    public void block()
    {
        // TODO - required for AMQSessionModel / producer side flow control
    }

    @Override
    public void unblock()
    {
        // TODO - required for AMQSessionModel / producer side flow control
    }

    @Override
    public boolean getBlocking()
    {
        // TODO
        return false;
    }

    @Override
    public boolean onSameConnection(InboundMessage inbound)
    {
        // TODO
        return false;
    }

    @Override
    public int getUnacknowledgedMessageCount()
    {
        // TODO
        return 0;
    }

    @Override
    public Long getTxnCount()
    {
        // TODO
        return 0l;
    }

    @Override
    public Long getTxnStart()
    {
        // TODO
        return 0l;
    }

    @Override
    public Long getTxnCommits()
    {
        // TODO
        return 0l;
    }

    @Override
    public Long getTxnRejects()
    {
        // TODO
        return 0l;
    }

    @Override
    public int getChannelId()
    {
        // TODO
        return 0;
    }

    @Override
    public int getConsumerCount()
    {
        // TODO
        return 0;
    }


    public String toLogString()
    {
        long connectionId = getConnectionModel().getConnectionId();

        String remoteAddress = getConnectionModel().getRemoteAddressString();

        return "[" +
               MessageFormat.format(CHANNEL_FORMAT,
                                    connectionId,
                                    getClientID(),
                                    remoteAddress,
                                    _vhost.getName(), // TODO - virtual host
                                    0) // TODO - channel)
            + "] ";
    }

    @Override
    public int compareTo(AMQSessionModel o)
    {
        return getId().compareTo(o.getId());
    }
}
