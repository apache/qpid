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

import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkListener;
import org.apache.qpid.amqp_1_0.type.*;

import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.filter.JMSSelectorMessageFilter;
import org.apache.qpid.server.filter.SimpleFilterManager;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SendingLink_1_0 implements SendingLinkListener, Link_1_0, DeliveryStateHandler
{
    private VirtualHost _vhost;
    private SendingDestination _destination;

    private Subscription_1_0 _subscription;
    private boolean _draining;
    private final Map<Binary, QueueEntry> _unsettledMap =
            new HashMap<Binary, QueueEntry>();

    private final ConcurrentHashMap<Binary, UnsettledAction> _unsettledActionMap =
            new ConcurrentHashMap<Binary, UnsettledAction>();
    private volatile SendingLinkAttachment _linkAttachment;
    private TerminusDurability _durability;
    private List<QueueEntry> _resumeFullTransfers = new ArrayList<QueueEntry>();
    private List<Binary> _resumeAcceptedTransfers = new ArrayList<Binary>();
    private Runnable _closeAction;

    public SendingLink_1_0(final SendingLinkAttachment linkAttachment,
                           final VirtualHost vhost,
                           final SendingDestination destination)
    {
        _vhost = vhost;
        _destination = destination;
        _linkAttachment = linkAttachment;
        final Source source = (Source) linkAttachment.getSource();
        _durability = source.getDurable();
        linkAttachment.setDeliveryStateHandler(this);
        QueueDestination qd = null;
        AMQQueue queue = null;



        boolean noLocal = false;
        JMSSelectorMessageFilter messageFilter = null;

        if(destination instanceof QueueDestination)
        {
            queue = ((QueueDestination) _destination).getQueue();
            if(queue.getArguments().containsKey("topic"))
            {
                source.setDistributionMode(StdDistMode.COPY);
            }
            qd = (QueueDestination) destination;

            Map<Symbol,Filter> filters = source.getFilter();

            Map<Symbol,Filter> actualFilters = new HashMap<Symbol,Filter>();

            for(Map.Entry<Symbol,Filter> entry : filters.entrySet())
            {
                if(entry.getValue() instanceof NoLocalFilter)
                {
                    actualFilters.put(entry.getKey(), entry.getValue());
                    noLocal = true;
                }
                else if(messageFilter == null && entry.getValue() instanceof JMSSelectorFilter)
                {

                    JMSSelectorFilter selectorFilter = (JMSSelectorFilter) entry.getValue();
                    try
                    {
                        messageFilter = new JMSSelectorMessageFilter(selectorFilter.getValue());

                        actualFilters.put(entry.getKey(), entry.getValue());
                    }
                    catch (AMQInvalidArgumentException e)
                    {

                    }


                }
            }
            source.setFilter(actualFilters.isEmpty() ? null : actualFilters);

        }
        else if(destination instanceof ExchangeDestination)
        {
            try
            {
                queue = AMQQueueFactory.createAMQQueueImpl(UUID.randomUUID().toString(), false, null, true,
                        false, _vhost, Collections.EMPTY_MAP);
                Exchange exchange = ((ExchangeDestination) destination).getExchange();

                String binding = "";

                Map filters = source.getFilter();

                if(filters != null && !filters.isEmpty())
                {

                    source.setFilter(null);
                    if(exchange.getType() == DirectExchange.TYPE)
                    {
                        if(filters.size() == 1 && filters.values().iterator().next() instanceof ExactSubjectFilter)
                        {
                            ExactSubjectFilter filter = (ExactSubjectFilter) filters.values().iterator().next();
                            source.setFilter(filters);
                            binding = filter.getValue();
                        }
                    }
                    else if(exchange.getType() == TopicExchange.TYPE)
                    {
                        if(filters.size() == 1 && filters.values().iterator().next() instanceof MatchingSubjectFilter)
                        {
                            MatchingSubjectFilter filter = (MatchingSubjectFilter) filters.values().iterator().next();
                            source.setFilter(filters);
                            binding = filter.getValue();
                        }
                    }
                }

                vhost.getBindingFactory().addBinding(binding,queue,exchange,null);
                source.setDistributionMode(StdDistMode.COPY);

                qd = new QueueDestination(queue);
            }
            catch (AMQSecurityException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            catch (AMQInternalException e)
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }


        }

        _subscription = new Subscription_1_0(this, qd);
        _subscription.setNoLocal(noLocal);
        _subscription.setFilters(new SimpleFilterManager(messageFilter));

        try
        {

            queue.registerSubscription(_subscription, false);
        }
        catch (AMQException e)
        {
            e.printStackTrace();  //TODO
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
        if(!TerminusDurability.UNSETTLED_STATE.equals(_durability) ||
           (detach != null && Boolean.TRUE.equals(detach.getClosed())))
        {

            try
            {

                _subscription.getQueue().unregisterSubscription(_subscription);

            }
            catch (AMQException e)
            {
                e.printStackTrace();  //TODO
            }

            DeliveryState state = new Released();

            for(UnsettledAction action : _unsettledActionMap.values())
            {

                action.process(state,Boolean.TRUE);
            }
            _unsettledActionMap.clear();

            endpoint.detach();
            if(_closeAction != null)
            {
                _closeAction.run();
            }
        }
        else if(detach == null || detach.getError() != null)
        {
            _linkAttachment = null;
            _subscription.flowStateChanged();
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
                && getEndpoint().getLinkCredit().compareTo(UnsignedInteger.ZERO) > 0)
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
            _subscription.flowStateChanged();
        }

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

    public void addUnsettled(Binary tag, UnsettledAction unsettledAction, QueueEntry queueEntry)
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
        return getEndpoint().getTransactionId();
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

        if(_subscription.isActive())
        {
            _subscription.suspend();
        }

        _linkAttachment = linkAttachment;

        SendingLinkEndpoint endpoint = linkAttachment.getEndpoint();
        endpoint.setDeliveryStateHandler(this);
        Map initialUnsettledMap = endpoint.getInitialUnsettledMap();
        Map<Binary, QueueEntry> unsettledCopy = new HashMap<Binary, QueueEntry>(_unsettledMap);
        _resumeAcceptedTransfers.clear();
        _resumeFullTransfers.clear();
        for(Map.Entry<Binary, QueueEntry> entry : unsettledCopy.entrySet())
        {
            Binary deliveryTag = entry.getKey();
            final QueueEntry queueEntry = entry.getValue();
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
                    AutoCommitTransaction txn = new AutoCommitTransaction(_vhost.getTransactionLog());
                    if(_subscription.acquires())
                    {
                        txn.dequeue(Collections.singleton(queueEntry),
                                new ServerTransaction.Action()
                                {
                                    public void postCommit()
                                    {
                                        queueEntry.discard();
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
                    AutoCommitTransaction txn = new AutoCommitTransaction(_vhost.getTransactionLog());
                    if(_subscription.acquires())
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
        Map<Binary, QueueEntry> unsettled = new HashMap<Binary, QueueEntry>(_unsettledMap);

        for(Map.Entry<Binary, QueueEntry> entry : unsettled.entrySet())
        {
            entry.setValue(null);
        }

        return unsettled;
    }

    public void setCloseAction(Runnable action)
    {
        _closeAction = action;
    }
}
