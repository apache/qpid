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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
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
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.filter.SimpleFilterManager;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class SendingLink_1_0 implements SendingLinkListener, Link_1_0, DeliveryStateHandler
{
    private static final Logger _logger = Logger.getLogger(SendingLink_1_0.class);

    private VirtualHost _vhost;
    private SendingDestination _destination;

    private Consumer _consumer;
    private ConsumerTarget_1_0 _target;

    private boolean _draining;
    private final Map<Binary, MessageInstance> _unsettledMap =
            new HashMap<Binary, MessageInstance>();

    private final ConcurrentHashMap<Binary, UnsettledAction> _unsettledActionMap =
            new ConcurrentHashMap<Binary, UnsettledAction>();
    private volatile SendingLinkAttachment _linkAttachment;
    private TerminusDurability _durability;
    private List<MessageInstance> _resumeFullTransfers = new ArrayList<MessageInstance>();
    private List<Binary> _resumeAcceptedTransfers = new ArrayList<Binary>();
    private Runnable _closeAction;
    private final MessageSource _queue;


    public SendingLink_1_0(final SendingLinkAttachment linkAttachment,
                           final VirtualHost vhost,
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

        EnumSet<Consumer.Option> options = EnumSet.noneOf(Consumer.Option.class);


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
                options.add(Consumer.Option.ACQUIRES);
                options.add(Consumer.Option.SEES_REQUEUES);
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
                Exchange exchange = exchangeDestination.getExchange();

                if(queue == null)
                {
                    queue = _vhost.createQueue(
                                UUIDGenerator.generateQueueUUID(name, _vhost.getName()),
                                name,
                                isDurable,
                                null,
                                true,
                                true,
                                true,
                                Collections.EMPTY_MAP);
                }
                else
                {
                    List<Binding> bindings = queue.getBindings();
                    List<Binding> bindingsToRemove = new ArrayList<Binding>();
                    for(Binding existingBinding : bindings)
                    {
                        if(existingBinding.getExchange() != _vhost.getDefaultExchange()
                            && existingBinding.getExchange() != exchange)
                        {
                            bindingsToRemove.add(existingBinding);
                        }
                    }
                    for(Binding existingBinding : bindingsToRemove)
                    {
                        existingBinding.getExchange().removeBinding(existingBinding);
                    }
                }


                String binding = "";

                Map<Symbol,Filter> filters = source.getFilter();
                Map<Symbol,Filter> actualFilters = new HashMap<Symbol,Filter>();
                boolean hasBindingFilter = false;
                if(filters != null && !filters.isEmpty())
                {

                    for(Map.Entry<Symbol,Filter> entry : filters.entrySet())
                    {
                        if(!hasBindingFilter
                           && entry.getValue() instanceof ExactSubjectFilter
                           && exchange.getType() == DirectExchange.TYPE)
                        {
                            ExactSubjectFilter filter = (ExactSubjectFilter) filters.values().iterator().next();
                            source.setFilter(filters);
                            binding = filter.getValue();
                            actualFilters.put(entry.getKey(), entry.getValue());
                            hasBindingFilter = true;
                        }
                        else if(!hasBindingFilter
                                && entry.getValue() instanceof MatchingSubjectFilter
                                && exchange.getType() == TopicExchange.TYPE)
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

                exchange.addBinding(binding, queue,null);
                source.setDistributionMode(StdDistMode.COPY);

                if(!isDurable)
                {
                    final String queueName = name;
                    final AMQQueue tempQueue = queue;

                    final Action<Connection_1_0> deleteQueueTask =
                                            new Action<Connection_1_0>()
                                            {
                                                public void performAction(Connection_1_0 session)
                                                {
                                                    if (_vhost.getQueue(queueName) == tempQueue)
                                                    {
                                                        try
                                                        {
                                                            _vhost.removeQueue(tempQueue);
                                                        }
                                                        catch (AMQException e)
                                                        {
                                                            //TODO
                                                            _logger.error("Error removing queue", e);
                                                        }
                                                    }
                                                }
                                            };

                                    getSession().getConnection().addConnectionCloseTask(deleteQueueTask);

                                    queue.addQueueDeleteTask(new Action<AMQQueue>()
                                    {
                                        public void performAction(AMQQueue queue)
                                        {
                                            getSession().getConnection().removeConnectionCloseTask(deleteQueueTask);
                                        }


                                    });
                }

                qd = new QueueDestination(queue);
            }
            catch (AMQSecurityException e)
            {
                _logger.error("Security error", e);
                throw new RuntimeException(e);
            }
            catch (AMQInternalException e)
            {
                _logger.error("Internal error", e);
                throw new RuntimeException(e);
            }
            catch (AMQException e)
            {
                _logger.error("Error", e);
                throw new RuntimeException(e);
            }


            _target = new ConsumerTarget_1_0(this, true);
            options.add(Consumer.Option.ACQUIRES);
            options.add(Consumer.Option.SEES_REQUEUES);

        }
        else
        {
            throw new RuntimeException("Unknown destination type");
        }

        if(_target != null)
        {
            if(noLocal)
            {
                options.add(Consumer.Option.NO_LOCAL);
            }

            try
            {
                _consumer = _queue.addConsumer(_target,
                                               messageFilter == null ? null : new SimpleFilterManager(messageFilter),
                                               Message_1_0.class, getEndpoint().getName(), options);
            }
            catch (AMQException e)
            {
                //TODO
                _logger.error("Error registering subscription", e);
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

            try
            {

                _consumer.close();

            }
            catch (AMQException e)
            {
                //TODO
                _logger.error("Error unregistering subscription", e);
            }

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
                catch(AMQException e)
                {
                    //TODO
                    _logger.error("Error removing queue", e);
                }
            }

            if(_closeAction != null)
            {
                _closeAction.run();
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
            else if(initialUnsettledMap != null && (initialUnsettledMap.get(deliveryTag) instanceof Outcome))
            {
                Outcome outcome = (Outcome) initialUnsettledMap.get(deliveryTag);

                if(outcome instanceof Accepted)
                {
                    AutoCommitTransaction txn = new AutoCommitTransaction(_vhost.getMessageStore());
                    if(_consumer.acquires())
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
                                        //To change body of implemented methods use File | Settings | File Templates.
                                    }
                                });
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

    public VirtualHost getVirtualHost()
    {
        return _vhost;
    }
}
