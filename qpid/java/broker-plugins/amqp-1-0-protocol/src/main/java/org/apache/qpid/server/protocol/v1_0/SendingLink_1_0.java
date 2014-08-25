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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;

import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkListener;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.ExactSubjectFilter;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.MatchingSubjectFilter;
import org.apache.qpid.amqp_1_0.type.messaging.Modified;
import org.apache.qpid.amqp_1_0.type.messaging.NoLocalFilter;
import org.apache.qpid.amqp_1_0.type.messaging.Released;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.filter.SimpleFilterManager;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class SendingLink_1_0 implements SendingLinkListener, Link_1_0, DeliveryStateHandler
{
    private static final Logger _logger = Logger.getLogger(SendingLink_1_0.class);

    private VirtualHostImpl _vhost;
    private SendingDestination _destination;

    private ConsumerImpl _consumer;
    private ConsumerTarget_1_0 _target;

    private boolean _draining;
    private final Map<Binary, MessageInstance> _unsettledMap =
            new HashMap<Binary, MessageInstance>();

    private final ConcurrentMap<Binary, UnsettledAction> _unsettledActionMap =
            new ConcurrentHashMap<Binary, UnsettledAction>();
    private volatile SendingLinkAttachment _linkAttachment;
    private TerminusDurability _durability;
    private List<MessageInstance> _resumeFullTransfers = new ArrayList<MessageInstance>();
    private List<Binary> _resumeAcceptedTransfers = new ArrayList<Binary>();
    private Runnable _closeAction;
    private final MessageSource _queue;


    public SendingLink_1_0(final SendingLinkAttachment linkAttachment,
                           final VirtualHostImpl vhost,
                           final SendingDestination destination)
            throws AmqpErrorException
    {
        _vhost = vhost;
        _destination = destination;
        _linkAttachment = linkAttachment;
        final Source source = (Source) linkAttachment.getSource();
        _durability = source.getDurable();
        linkAttachment.setDeliveryStateHandler(this);
        QueueDestination qd = null;

        EnumSet<ConsumerImpl.Option> options = EnumSet.noneOf(ConsumerImpl.Option.class);


        boolean noLocal = false;
        JMSSelectorFilter messageFilter = null;

        if(destination instanceof MessageSourceDestination)
        {
            _queue = ((MessageSourceDestination) _destination).getQueue();

            if(_queue instanceof AMQQueue && ((AMQQueue)_queue).getAvailableAttributes().contains("topic"))
            {
                source.setDistributionMode(StdDistMode.COPY);
            }

            Map<Symbol,Filter> filters = source.getFilter();

            Map<Symbol,Filter> actualFilters = new HashMap<Symbol,Filter>();

            if(filters != null)
            {
                for(Map.Entry<Symbol,Filter> entry : filters.entrySet())
                {
                    if(entry.getValue() instanceof NoLocalFilter)
                    {
                        actualFilters.put(entry.getKey(), entry.getValue());
                        noLocal = true;
                    }
                    else if(messageFilter == null && entry.getValue() instanceof org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter)
                    {

                        org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter selectorFilter = (org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter) entry.getValue();
                        try
                        {
                            messageFilter = new JMSSelectorFilter(selectorFilter.getValue());

                            actualFilters.put(entry.getKey(), entry.getValue());
                        }
                        catch (ParseException e)
                        {
                            Error error = new Error();
                            error.setCondition(AmqpError.INVALID_FIELD);
                            error.setDescription("Invalid JMS Selector: " + selectorFilter.getValue());
                            error.setInfo(Collections.singletonMap(Symbol.valueOf("field"), Symbol.valueOf("filter")));
                            throw new AmqpErrorException(error);
                        }
                        catch (SelectorParsingException e)
                        {
                            Error error = new Error();
                            error.setCondition(AmqpError.INVALID_FIELD);
                            error.setDescription("Invalid JMS Selector: " + selectorFilter.getValue());
                            error.setInfo(Collections.singletonMap(Symbol.valueOf("field"), Symbol.valueOf("filter")));
                            throw new AmqpErrorException(error);
                        }


                    }
                }
            }
            source.setFilter(actualFilters.isEmpty() ? null : actualFilters);

            _target = new ConsumerTarget_1_0(this, source.getDistributionMode() != StdDistMode.COPY);
            if(source.getDistributionMode() != StdDistMode.COPY)
            {
                options.add(ConsumerImpl.Option.ACQUIRES);
                options.add(ConsumerImpl.Option.SEES_REQUEUES);
            }

        }
        else if(destination instanceof ExchangeDestination)
        {
            try
            {

                ExchangeDestination exchangeDestination = (ExchangeDestination) destination;

                boolean isDurable = exchangeDestination.getDurability() == TerminusDurability.CONFIGURATION
                                    || exchangeDestination.getDurability() == TerminusDurability.UNSETTLED_STATE;
                String name;
                if(isDurable)
                {
                    String remoteContainerId = getEndpoint().getSession().getConnection().getRemoteContainerId();
                    remoteContainerId = remoteContainerId.replace("_","__").replace(".", "_:");

                    String endpointName = linkAttachment.getEndpoint().getName();
                    endpointName = endpointName
                                    .replace("_", "__")
                                    .replace(".", "_:")
                                    .replace("(", "_O")
                                    .replace(")", "_C")
                                    .replace("<", "_L")
                                    .replace(">", "_R");
                    name = "qpid_/" + remoteContainerId + "_/" + endpointName;
                }
                else
                {
                    name = UUID.randomUUID().toString();
                }

                AMQQueue queue = _vhost.getQueue(name);
                ExchangeImpl exchange = exchangeDestination.getExchange();

                if(queue == null)
                {
                    Map<String,Object> attributes = new HashMap<String,Object>();
                    attributes.put(Queue.ID, UUID.randomUUID());
                    attributes.put(Queue.NAME, name);
                    attributes.put(Queue.DURABLE, isDurable);
                    attributes.put(Queue.LIFETIME_POLICY, LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS);
                    attributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.LINK);

                    queue = _vhost.createQueue(attributes);
                }
                else
                {
                    Collection<BindingImpl> bindings = queue.getBindings();
                    List<BindingImpl> bindingsToRemove = new ArrayList<BindingImpl>();
                    for(BindingImpl existingBinding : bindings)
                    {
                        if(existingBinding.getExchange() != exchange)
                        {
                            bindingsToRemove.add(existingBinding);
                        }
                    }
                    for(BindingImpl existingBinding : bindingsToRemove)
                    {
                        existingBinding.delete();
                    }
                }


                String binding = null;

                Map<Symbol,Filter> filters = source.getFilter();
                Map<Symbol,Filter> actualFilters = new HashMap<Symbol,Filter>();
                boolean hasBindingFilter = false;
                if(filters != null && !filters.isEmpty())
                {

                    for(Map.Entry<Symbol,Filter> entry : filters.entrySet())
                    {
                        if(!hasBindingFilter
                           && entry.getValue() instanceof ExactSubjectFilter
                           && exchange.getType().equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
                        {
                            ExactSubjectFilter filter = (ExactSubjectFilter) filters.values().iterator().next();
                            source.setFilter(filters);
                            binding = filter.getValue();
                            actualFilters.put(entry.getKey(), entry.getValue());
                            hasBindingFilter = true;
                        }
                        else if(!hasBindingFilter
                                && entry.getValue() instanceof MatchingSubjectFilter
                                && exchange.getType().equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
                        {
                            MatchingSubjectFilter filter = (MatchingSubjectFilter) filters.values().iterator().next();
                            source.setFilter(filters);
                            binding = filter.getValue();
                            actualFilters.put(entry.getKey(), entry.getValue());
                            hasBindingFilter = true;
                        }
                        else if(entry.getValue() instanceof NoLocalFilter)
                        {
                            actualFilters.put(entry.getKey(), entry.getValue());
                            noLocal = true;
                        }
                        else if(messageFilter == null && entry.getValue() instanceof org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter)
                        {

                            org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter selectorFilter = (org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter) entry.getValue();
                            try
                            {
                                messageFilter = new JMSSelectorFilter(selectorFilter.getValue());

                                actualFilters.put(entry.getKey(), entry.getValue());
                            }
                            catch (ParseException e)
                            {
                                Error error = new Error();
                                error.setCondition(AmqpError.INVALID_FIELD);
                                error.setDescription("Invalid JMS Selector: " + selectorFilter.getValue());
                                error.setInfo(Collections.singletonMap(Symbol.valueOf("field"), Symbol.valueOf("filter")));
                                throw new AmqpErrorException(error);
                            }
                            catch (SelectorParsingException e)
                            {
                                Error error = new Error();
                                error.setCondition(AmqpError.INVALID_FIELD);
                                error.setDescription("Invalid JMS Selector: " + selectorFilter.getValue());
                                error.setInfo(Collections.singletonMap(Symbol.valueOf("field"), Symbol.valueOf("filter")));
                                throw new AmqpErrorException(error);
                            }


                        }
                    }
                }
                _queue = queue;
                source.setFilter(actualFilters.isEmpty() ? null : actualFilters);
                if(binding != null)
                {
                    exchange.addBinding(binding, queue,null);
                }
                if(exchangeDestination.getInitialRoutingAddress() != null)
                {
                    exchange.addBinding(exchangeDestination.getInitialRoutingAddress(),queue,null);
                }
                source.setDistributionMode(StdDistMode.COPY);

                qd = new QueueDestination(queue);
            }
            catch (QueueExistsException e)
            {
                _logger.error("A randomly generated temporary queue name collided with an existing queue",e);
                throw new ConnectionScopedRuntimeException(e);
            }


            _target = new ConsumerTarget_1_0(this, true);
            options.add(ConsumerImpl.Option.ACQUIRES);
            options.add(ConsumerImpl.Option.SEES_REQUEUES);

        }
        else
        {
            throw new ConnectionScopedRuntimeException("Unknown destination type");
        }

        if(_target != null)
        {
            if(noLocal)
            {
                options.add(ConsumerImpl.Option.NO_LOCAL);
            }

            try
            {
                final String name;
                if(getEndpoint().getTarget() instanceof Target)
                {
                    Target target = (Target) getEndpoint().getTarget();
                    name = target.getAddress() == null ? getEndpoint().getName() : target.getAddress();
                }
                else
                {
                    name = getEndpoint().getName();
                }
                _consumer = _queue.addConsumer(_target,
                                               messageFilter == null ? null : new SimpleFilterManager(messageFilter),
                                               Message_1_0.class, name, options);
            }
            catch (MessageSource.ExistingExclusiveConsumer e)
            {
                _logger.info("Cannot add a consumer to the destination as there is already an exclusive consumer");
                throw new ConnectionScopedRuntimeException(e);
            }
            catch (MessageSource.ExistingConsumerPreventsExclusive e)
            {
                _logger.info("Cannot add an exclusive consumer to the destination as there is already a consumer");
                throw new ConnectionScopedRuntimeException(e);
            }
            catch (MessageSource.ConsumerAccessRefused e)
            {
                _logger.info("Cannot add an exclusive consumer to the destination as there is an incompatible exclusivity policy");
                throw new ConnectionScopedRuntimeException(e);
            }
        }

    }

    public void resume(SendingLinkAttachment linkAttachment)
    {
        _linkAttachment = linkAttachment;
    }

    public void remoteDetached(final LinkEndpoint endpoint, final Detach detach)
    {
        //TODO
        // if not durable or close
        if(!TerminusDurability.UNSETTLED_STATE.equals(_durability))
        {
            while(!_consumer.trySendLock())
            {
                synchronized (endpoint.getLock())
                {
                    try
                    {
                        endpoint.getLock().wait(100);
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            }
            try
            {
                _consumer.close();

                Modified state = new Modified();
                state.setDeliveryFailed(true);

                for(UnsettledAction action : _unsettledActionMap.values())
                {

                    action.process(state,Boolean.TRUE);
                }
                _unsettledActionMap.clear();

                endpoint.close();

                if(_destination instanceof ExchangeDestination
                   && (_durability == TerminusDurability.CONFIGURATION
                        || _durability == TerminusDurability.UNSETTLED_STATE))
                {
                    try
                    {
                        _vhost.removeQueue((AMQQueue)_queue);
                    }
                    catch (AccessControlException e)
                    {
                        //TODO
                        _logger.error("Error registering subscription", e);
                    }
                }

                if(_closeAction != null)
                {
                    _closeAction.run();
                }
            }
            finally
            {
                _consumer.releaseSendLock();
            }
        }
        else if(detach == null || detach.getError() != null)
        {
            _linkAttachment = null;
            _target.flowStateChanged();
        }
        else
        {
            endpoint.detach();
        }
    }

    public void start()
    {
        //TODO
    }

    public SendingLinkEndpoint getEndpoint()
    {
        return _linkAttachment == null ? null : _linkAttachment.getEndpoint() ;
    }

    public Session_1_0 getSession()
    {
        return _linkAttachment == null ? null : _linkAttachment.getSession();
    }

    public void flowStateChanged()
    {
        if(Boolean.TRUE.equals(getEndpoint().getDrain())
                && hasCredit())
        {
            _draining = true;
        }

        while(!_resumeAcceptedTransfers.isEmpty() && getEndpoint().hasCreditToSend())
        {
            Accepted accepted = new Accepted();
            synchronized(getLock())
            {

                Transfer xfr = new Transfer();
                Binary dt = _resumeAcceptedTransfers.remove(0);
                xfr.setDeliveryTag(dt);
                xfr.setState(accepted);
                xfr.setResume(Boolean.TRUE);
                getEndpoint().transfer(xfr);
            }

        }
        if(_resumeAcceptedTransfers.isEmpty())
        {
            _target.flowStateChanged();
        }

    }

    boolean hasCredit()
    {
        return getEndpoint().getLinkCredit().compareTo(UnsignedInteger.ZERO) > 0;
    }

    public boolean isDraining()
    {
        return false;  //TODO
    }

    public boolean drained()
    {
        if(getEndpoint() != null)
        {
            synchronized(getEndpoint().getLock())
            {
                if(_draining)
                {
                    //TODO
                    getEndpoint().drained();
                    _draining = false;
                    return true;
                }
                else
                {
                    return false;
                }
            }
        }
        else
        {
            return false;
        }
    }

    public void addUnsettled(Binary tag, UnsettledAction unsettledAction, MessageInstance queueEntry)
    {
        _unsettledActionMap.put(tag,unsettledAction);
        if(getTransactionId() == null)
        {
            _unsettledMap.put(tag, queueEntry);
        }
    }

    public void removeUnsettled(Binary tag)
    {
        _unsettledActionMap.remove(tag);
    }

    public void handle(Binary deliveryTag, DeliveryState state, Boolean settled)
    {
        UnsettledAction action = _unsettledActionMap.get(deliveryTag);
        boolean localSettle = false;
        if(action != null)
        {
            localSettle = action.process(state, settled);
            if(localSettle && !Boolean.TRUE.equals(settled))
            {
                _linkAttachment.updateDisposition(deliveryTag, state, true);
            }
        }
        if(Boolean.TRUE.equals(settled) || localSettle)
        {
            _unsettledActionMap.remove(deliveryTag);
            _unsettledMap.remove(deliveryTag);
        }
    }

    ServerTransaction getTransaction(Binary transactionId)
    {
        return _linkAttachment.getSession().getTransaction(transactionId);
    }

    public Binary getTransactionId()
    {
        SendingLinkEndpoint endpoint = getEndpoint();
        return endpoint == null ? null : endpoint.getTransactionId();
    }

    public synchronized Object getLock()
    {
        return _linkAttachment == null ? this : getEndpoint().getLock();
    }

    public boolean isDetached()
    {
        return _linkAttachment == null || getEndpoint().isDetached();
    }

    public boolean isAttached()
    {
        return _linkAttachment != null && getEndpoint().isAttached();
    }

    public synchronized void setLinkAttachment(SendingLinkAttachment linkAttachment)
    {

        if(_consumer.isActive())
        {
            _target.suspend();
        }

        _linkAttachment = linkAttachment;

        SendingLinkEndpoint endpoint = linkAttachment.getEndpoint();
        endpoint.setDeliveryStateHandler(this);
        Map initialUnsettledMap = endpoint.getInitialUnsettledMap();
        Map<Binary, MessageInstance> unsettledCopy = new HashMap<Binary, MessageInstance>(_unsettledMap);
        _resumeAcceptedTransfers.clear();
        _resumeFullTransfers.clear();

        for(Map.Entry<Binary, MessageInstance> entry : unsettledCopy.entrySet())
        {
            Binary deliveryTag = entry.getKey();
            final MessageInstance queueEntry = entry.getValue();
            if(initialUnsettledMap == null || !initialUnsettledMap.containsKey(deliveryTag))
            {
                queueEntry.setRedelivered();
                queueEntry.release();
                _unsettledMap.remove(deliveryTag);
            }
            else if(initialUnsettledMap.get(deliveryTag) instanceof Outcome)
            {
                Outcome outcome = (Outcome) initialUnsettledMap.get(deliveryTag);

                if(outcome instanceof Accepted)
                {
                    AutoCommitTransaction txn = new AutoCommitTransaction(_vhost.getMessageStore());
                    if(_consumer.acquires())
                    {
                        if(queueEntry.acquire() || queueEntry.isAcquired())
                        {
                            txn.dequeue(Collections.singleton(queueEntry),
                                        new ServerTransaction.Action()
                                        {
                                            public void postCommit()
                                            {
                                                queueEntry.delete();
                                            }

                                            public void onRollback()
                                            {
                                            }
                                        });
                        }
                    }
                }
                else if(outcome instanceof Released)
                {
                    AutoCommitTransaction txn = new AutoCommitTransaction(_vhost.getMessageStore());
                    if(_consumer.acquires())
                    {
                        txn.dequeue(Collections.singleton(queueEntry),
                                new ServerTransaction.Action()
                                {
                                    public void postCommit()
                                    {
                                        queueEntry.release();
                                    }

                                    public void onRollback()
                                    {
                                        //To change body of implemented methods use File | Settings | File Templates.
                                    }
                                });
                    }
                }
                //_unsettledMap.remove(deliveryTag);
                initialUnsettledMap.remove(deliveryTag);
                _resumeAcceptedTransfers.add(deliveryTag);
            }
            else
            {
                _resumeFullTransfers.add(queueEntry);
                // exists in receivers map, but not yet got an outcome ... should resend with resume = true
            }
            // TODO - else
        }


    }

    public Map getUnsettledOutcomeMap()
    {
        Map<Binary, MessageInstance> unsettled = new HashMap<Binary, MessageInstance>(_unsettledMap);

        for(Map.Entry<Binary, MessageInstance> entry : unsettled.entrySet())
        {
            entry.setValue(null);
        }

        return unsettled;
    }

    public void setCloseAction(Runnable action)
    {
        _closeAction = action;
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _vhost;
    }

    public ConsumerImpl getConsumer()
    {
        return _consumer;
    }
}
