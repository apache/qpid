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

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;

import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.ReceivingLinkListener;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkListener;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;
import org.apache.qpid.amqp_1_0.transport.SessionEventListener;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.LifetimePolicy;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.DeleteOnClose;
import org.apache.qpid.amqp_1_0.type.messaging.DeleteOnNoLinks;
import org.apache.qpid.amqp_1_0.type.messaging.DeleteOnNoLinksOrMessages;
import org.apache.qpid.amqp_1_0.type.messaging.DeleteOnNoMessages;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.transaction.Coordinator;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.End;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Role;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.ConsumerListener;
import org.apache.qpid.server.protocol.LinkRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class Session_1_0 implements SessionEventListener, AMQSessionModel<Session_1_0, Connection_1_0>, LogSubject
{
    private static final Logger _logger = Logger.getLogger(Session_1_0.class);
    private static final Symbol LIFETIME_POLICY = Symbol.valueOf("lifetime-policy");
    private final SessionEndpoint _endpoint;
    private AutoCommitTransaction _transaction;

    private final LinkedHashMap<Integer, ServerTransaction> _openTransactions =
            new LinkedHashMap<Integer, ServerTransaction>();

    private final CopyOnWriteArrayList<Action<? super Session_1_0>> _taskList =
            new CopyOnWriteArrayList<Action<? super Session_1_0>>();

    private final Connection_1_0 _connection;
    private UUID _id = UUID.randomUUID();
    private AtomicBoolean _closed = new AtomicBoolean();
    private final Subject _subject = new Subject();

    private final CopyOnWriteArrayList<Consumer<?>> _consumers = new CopyOnWriteArrayList<Consumer<?>>();
    private final CopyOnWriteArrayList<SendingLink_1_0> _sendingLinks = new CopyOnWriteArrayList<>();
    private final ConfigurationChangeListener _consumerClosedListener = new ConsumerClosedListener();
    private final CopyOnWriteArrayList<ConsumerListener> _consumerListeners = new CopyOnWriteArrayList<ConsumerListener>();
    private Session<?> _modelObject;


    public Session_1_0(final Connection_1_0 connection, final SessionEndpoint endpoint)
    {
        _endpoint = endpoint;
        _connection = connection;
        _subject.getPrincipals().addAll(connection.getSubject().getPrincipals());
        _subject.getPrincipals().add(new SessionPrincipal(this));
    }

    public void remoteLinkCreation(final LinkEndpoint endpoint)
    {

        Destination destination;
        Link_1_0 link = null;
        Error error = null;

        final
        LinkRegistry
                linkRegistry = getVirtualHost().getLinkRegistry(endpoint.getSession().getConnection().getRemoteContainerId());


        if(endpoint.getRole() == Role.SENDER)
        {

            final SendingLink_1_0 previousLink = (SendingLink_1_0) linkRegistry.getDurableSendingLink(endpoint.getName());

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
                    if(!addr.startsWith("/") && addr.contains("/"))
                    {
                        String[] parts = addr.split("/",2);
                        ExchangeImpl exchg = getVirtualHost().getExchange(parts[0]);
                        if(exchg != null)
                        {
                            ExchangeDestination exchangeDestination =
                                    new ExchangeDestination(exchg, source.getDurable(), source.getExpiryPolicy());
                            exchangeDestination.setInitialRoutingAddress(parts[1]);
                            destination = exchangeDestination;

                        }
                        else
                        {
                            endpoint.setSource(null);
                            destination = null;
                        }
                    }
                    else
                    {
                        MessageSource queue = getVirtualHost().getMessageSource(addr);
                        if(queue != null)
                        {
                            destination = new MessageSourceDestination(queue);
                        }
                        else
                        {
                            ExchangeImpl exchg = getVirtualHost().getExchange(addr);
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
                                                                                getVirtualHost(),
                                                                                (SendingDestination) destination
                        );

                        sendingLinkEndpoint.setLinkEventListener(new SubjectSpecificSendingLinkListener(sendingLink));
                        registerConsumer(sendingLink);

                        link = sendingLink;
                        if(TerminusDurability.UNSETTLED_STATE.equals(source.getDurable()) || TerminusDurability.CONFIGURATION.equals(source.getDurable()))
                        {
                            linkRegistry.registerSendingLink(endpoint.getName(), sendingLink);
                        }
                    }
                    catch(AmqpErrorException e)
                    {
                        _logger.error("Error creating sending link", e);
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
                sendingLinkEndpoint.setLinkEventListener(new SubjectSpecificSendingLinkListener(previousLink));
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
                        new TxnCoordinatorLink_1_0(getVirtualHost(), this, receivingLinkEndpoint, _openTransactions);
                receivingLinkEndpoint.setLinkEventListener(new SubjectSpecificReceivingLinkListener(coordinatorLink));
                link = coordinatorLink;


            }
            else
            {

                ReceivingLink_1_0 previousLink =
                        (ReceivingLink_1_0) linkRegistry.getDurableReceivingLink(endpoint.getName());

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
                        if(!addr.startsWith("/") && addr.contains("/"))
                        {
                            String[] parts = addr.split("/",2);
                            ExchangeImpl exchange = getVirtualHost().getExchange(parts[0]);
                            if(exchange != null)
                            {
                                ExchangeDestination exchangeDestination =
                                        new ExchangeDestination(exchange,
                                                                target.getDurable(),
                                                                target.getExpiryPolicy());

                                exchangeDestination.setInitialRoutingAddress(parts[1]);

                                destination = exchangeDestination;

                            }
                            else
                            {
                                endpoint.setTarget(null);
                                destination = null;
                            }
                        }
                        else
                        {
                            MessageDestination messageDestination = getVirtualHost().getMessageDestination(addr);
                            if(messageDestination != null)
                            {
                                destination = new NodeReceivingDestination(messageDestination, target.getDurable(),
                                                                           target.getExpiryPolicy());
                            }
                            else
                            {
                                AMQQueue queue = getVirtualHost().getQueue(addr);
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

                    }
                    else
                    {
                        destination = null;
                    }
                    if(destination != null)
                    {
                        final ReceivingLinkEndpoint receivingLinkEndpoint = (ReceivingLinkEndpoint) endpoint;
                        final ReceivingLink_1_0 receivingLink = new ReceivingLink_1_0(new ReceivingLinkAttachment(this, receivingLinkEndpoint),
                                                                                      getVirtualHost(),
                                (ReceivingDestination) destination);

                        receivingLinkEndpoint.setLinkEventListener(new SubjectSpecificReceivingLinkListener(receivingLink));

                        link = receivingLink;
                        if(TerminusDurability.UNSETTLED_STATE.equals(target.getDurable())
                           || TerminusDurability.CONFIGURATION.equals(target.getDurable()))
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
            endpoint.close(error);
        }
        else
        {
            link.start();
        }
    }

    private void registerConsumer(final SendingLink_1_0 link)
    {
        ConsumerImpl consumer = link.getConsumer();
        if(consumer instanceof Consumer<?>)
        {
            Consumer<?> modelConsumer = (Consumer<?>) consumer;
            _consumers.add(modelConsumer);
            _sendingLinks.add(link);
            modelConsumer.addChangeListener(_consumerClosedListener);
            consumerAdded(modelConsumer);
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
            Map<String,Object> attributes = new HashMap<String,Object>();
            attributes.put(org.apache.qpid.server.model.Queue.ID, UUID.randomUUID());
            attributes.put(org.apache.qpid.server.model.Queue.NAME, queueName);
            attributes.put(org.apache.qpid.server.model.Queue.DURABLE, false);

            if(lifetimePolicy instanceof DeleteOnNoLinks)
            {
                attributes.put(org.apache.qpid.server.model.Queue.LIFETIME_POLICY,
                               org.apache.qpid.server.model.LifetimePolicy.DELETE_ON_NO_LINKS);
            }
            else if(lifetimePolicy instanceof DeleteOnNoLinksOrMessages)
            {
                attributes.put(org.apache.qpid.server.model.Queue.LIFETIME_POLICY,
                               org.apache.qpid.server.model.LifetimePolicy.IN_USE);
            }
            else if(lifetimePolicy instanceof DeleteOnClose)
            {
                attributes.put(org.apache.qpid.server.model.Queue.LIFETIME_POLICY,
                               org.apache.qpid.server.model.LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
            }
            else if(lifetimePolicy instanceof DeleteOnNoMessages)
            {
                attributes.put(org.apache.qpid.server.model.Queue.LIFETIME_POLICY,
                               org.apache.qpid.server.model.LifetimePolicy.IN_USE);
            }
            else
            {
                attributes.put(org.apache.qpid.server.model.Queue.LIFETIME_POLICY,
                               org.apache.qpid.server.model.LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
            }


            // TODO convert AMQP 1-0 node properties to queue attributes

            queue = getVirtualHost().createQueue(attributes);
        }
        catch (AccessControlException e)
        {
            //TODO
            _logger.info("Security error", e);
            throw new ConnectionScopedRuntimeException(e);
        }
        catch (QueueExistsException e)
        {
            _logger.error("A temporary queue was created with a name which collided with an existing queue name");
            throw new ConnectionScopedRuntimeException(e);
        }

        return queue;
    }

    public ServerTransaction getTransaction(Binary transactionId)
    {
        // TODO should treat invalid id differently to null
        ServerTransaction transaction = _openTransactions.get(binaryToInteger(transactionId));
        if(transaction == null)
        {
            if(_transaction == null)
            {
                _transaction = new AutoCommitTransaction(_connection.getVirtualHost().getMessageStore());
            }
            transaction = _transaction;
        }
        return transaction;
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

        for(LinkEndpoint linkEndpoint : _endpoint.getLocalLinkEndpoints())
        {
            linkEndpoint.remoteDetached(new Detach());
        }

        _connection.sessionEnded(this);
        performCloseTasks();
        if(_modelObject != null)
        {
            _modelObject.delete();
        }

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
            id += ((int)data[i+txnId.getArrayOffset()] & 0xff);
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

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public Connection_1_0 getConnectionModel()
    {
        return _connection;
    }

    @Override
    public String getClientID()
    {
        // TODO
        return "";
    }

    @Override
    public void close()
    {
        performCloseTasks();
        _endpoint.end();
        if(_modelObject != null)
        {
            _modelObject.delete();
        }
    }

    protected void performCloseTasks()
    {

        if(_closed.compareAndSet(false, true))
        {
            List<Action<? super Session_1_0>> taskList = new ArrayList<Action<? super Session_1_0>>(_taskList);
            _taskList.clear();
            for(Action<? super Session_1_0> task : taskList)
            {
                task.performAction(this);
            }
        }
    }


    @Override
    public void close(AMQConstant cause, String message)
    {
        performCloseTasks();
        final End end = new End();
        final Error theError = new Error();
        theError.setDescription(message);
        theError.setCondition(ConnectionError.CONNECTION_FORCED);
        end.setError(theError);
        _endpoint.end(end);
    }

    @Override
    public void transportStateChanged()
    {
        for(SendingLink_1_0 link : _sendingLinks)
        {
            ConsumerTarget_1_0 target = link.getConsumerTarget();
            target.flowStateChanged();


        }


    }

    @Override
    public LogSubject getLogSubject()
    {
        return this;
    }

    @Override
    public void checkTransactionStatus(long openWarn, long openClose, long idleWarn, long idleClose)
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
    public Object getConnectionReference()
    {
        return getConnection().getReference();
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
        return _endpoint.getSendingChannel();
    }

    @Override
    public int getConsumerCount()
    {
        return getConsumers().size();
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
                                    getVirtualHost().getName(),
                                    _endpoint.getSendingChannel())  + "] ";
    }

    @Override
    public int compareTo(AMQSessionModel o)
    {
        return getId().compareTo(o.getId());
    }

    public Connection_1_0 getConnection()
    {
        return _connection;
    }

    @Override
    public void addDeleteTask(final Action<? super Session_1_0> task)
    {
        if(!_closed.get())
        {
            _taskList.add(task);
        }
    }

    @Override
    public void removeDeleteTask(final Action<? super Session_1_0> task)
    {
        _taskList.remove(task);
    }

    public Subject getSubject()
    {
        return _subject;
    }

    VirtualHostImpl getVirtualHost()
    {
        return _connection.getVirtualHost();
    }


    private class SubjectSpecificReceivingLinkListener implements ReceivingLinkListener
    {
        private final ReceivingLinkListener _linkListener;

        public SubjectSpecificReceivingLinkListener(final ReceivingLinkListener linkListener)
        {
            _linkListener = linkListener;
        }

        @Override
        public void messageTransfer(final Transfer xfr)
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    _linkListener.messageTransfer(xfr);
                    return null;
                }
            });
        }

        @Override
        public void remoteDetached(final LinkEndpoint endpoint, final Detach detach)
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    _linkListener.remoteDetached(endpoint, detach);
                    return null;
                }
            });
        }
    }

    private class SubjectSpecificSendingLinkListener implements SendingLinkListener
    {
        private final SendingLink_1_0 _previousLink;

        public SubjectSpecificSendingLinkListener(final SendingLink_1_0 previousLink)
        {
            _previousLink = previousLink;
        }

        @Override
        public void flowStateChanged()
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    _previousLink.flowStateChanged();
                    return null;
                }
            });
        }

        @Override
        public void remoteDetached(final LinkEndpoint endpoint, final Detach detach)
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    _previousLink.remoteDetached(endpoint, detach);
                    return null;
                }
            });
        }
    }


    @Override
    public Collection<Consumer<?>> getConsumers()
    {
        return Collections.unmodifiableCollection(_consumers);
    }

    @Override
    public void addConsumerListener(final ConsumerListener listener)
    {
        _consumerListeners.add(listener);
    }

    @Override
    public void removeConsumerListener(final ConsumerListener listener)
    {
        _consumerListeners.remove(listener);
    }

    @Override
    public void setModelObject(final Session<?> session)
    {
        _modelObject = session;
    }

    @Override
    public Session<?> getModelObject()
    {
        return _modelObject;
    }

    @Override
    public long getTransactionStartTime()
    {
        return 0L;
    }

    @Override
    public long getTransactionUpdateTime()
    {
        return 0L;
    }

    @Override
    public void processPending()
    {
        for(Consumer<?> consumer : getConsumers())
        {

            ((ConsumerImpl)consumer).getTarget().processPending();
        }
    }

    private void consumerAdded(Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerAdded(consumer);
        }
    }

    private void consumerRemoved(Consumer<?> consumer)
    {
        for(ConsumerListener l : _consumerListeners)
        {
            l.consumerRemoved(consumer);
        }
    }

    private class ConsumerClosedListener implements ConfigurationChangeListener
    {
        @Override
        public void stateChanged(final ConfiguredObject object, final org.apache.qpid.server.model.State oldState, final org.apache.qpid.server.model.State newState)
        {
            if(newState == org.apache.qpid.server.model.State.DELETED)
            {
                consumerRemoved((Consumer<?>)object);
            }
        }

        @Override
        public void childAdded(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void childRemoved(final ConfiguredObject object, final ConfiguredObject child)
        {

        }

        @Override
        public void attributeSet(final ConfiguredObject object,
                                 final String attributeName,
                                 final Object oldAttributeValue,
                                 final Object newAttributeValue)
        {

        }
    }
}
