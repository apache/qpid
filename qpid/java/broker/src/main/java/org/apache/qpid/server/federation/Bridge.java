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
package org.apache.qpid.server.federation;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.BridgeConfig;
import org.apache.qpid.server.configuration.BridgeConfigType;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.flow.FlowCreditManager_0_10;
import org.apache.qpid.server.flow.WindowCreditManager;
import org.apache.qpid.server.message.MessageMetaData_0_10;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.subscription.Subscription_0_10;
import org.apache.qpid.server.transport.ServerSession;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageReject;
import org.apache.qpid.transport.MessageRejectCode;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Bridge implements BridgeConfig
{
    private final boolean _durable;
    private final boolean _dynamic;
    private final boolean _queueBridge;
    private final boolean _localSource;
    private final String _source;
    private final String _destination;
    private final String _key;
    private final String _tag;
    private final String _excludes;
    private final BrokerLink _link;
    private UUID _id;
    private long _createTime = System.currentTimeMillis();

    private Session _session;

    private BridgeImpl _delegate;

    private final int _bridgeNo;
    private AutoCommitTransaction _transaction;

    public Bridge(final BrokerLink brokerLink,
                  final int bridgeNo,
                  final boolean durable,
                  final boolean dynamic,
                  final boolean srcIsQueue,
                  final boolean srcIsLocal,
                  final String src,
                  final String dest,
                  final String key,
                  final String tag,
                  final String excludes)
    {
        _link = brokerLink;
        _bridgeNo = bridgeNo;
        _durable = durable;
        _dynamic = dynamic;
        _queueBridge = srcIsQueue;
        _localSource = srcIsLocal;
        _source = src;
        _destination = dest;
        _key = key;
        _tag = tag;
        _excludes = excludes;
        _id = brokerLink.getConfigStore().createId();

        _transaction = new AutoCommitTransaction(getVirtualHost().getMessageStore());

        if(dynamic)
        {
            if(srcIsLocal)
            {
                // TODO
            }
            else
            {
                if(srcIsQueue)
                {
                    // TODO
                }
                else
                {
                    _delegate = new DynamicExchangeBridge();
                }
            }
        }
        else
        {
            if(srcIsLocal)
            {
                if(srcIsQueue)
                {
                    _delegate = new StaticQueuePushBridge();
                }
                else
                {
                    _delegate = new StaticExchangePushBridge();
                }
            }
            else
            {
                if(srcIsQueue)
                {
                    _delegate = new StaticQueuePullBridge();
                }
                else
                {
                    _delegate = new StaticExchangePullBridge();
                }
            }
        }
    }

    public UUID getId()
    {
        return _id;
    }

    public BridgeConfigType getConfigType()
    {
        return BridgeConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getLink();
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isDynamic()
    {
        return _dynamic;
    }

    public boolean isQueueBridge()
    {
        return _queueBridge;
    }

    public boolean isLocalSource()
    {
        return _localSource;
    }

    public String getSource()
    {
        return _source;
    }

    public String getDestination()
    {
        return _destination;
    }

    public String getKey()
    {
        return _key;
    }

    public String getTag()
    {
        return _tag;
    }

    public String getExcludes()
    {
        return _excludes;
    }

    public BrokerLink getLink()
    {
        return _link;
    }

    public Integer getChannelId()
    {
        return (_session == null) ? 0 : _session.getChannel();
    }

    public int getAckBatching()
    {
        return 0;
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final Bridge bridge = (Bridge) o;

        if (_durable != bridge._durable)
        {
            return false;
        }
        if (_dynamic != bridge._dynamic)
        {
            return false;
        }
        if (_localSource != bridge._localSource)
        {
            return false;
        }
        if (_queueBridge != bridge._queueBridge)
        {
            return false;
        }
        if (_destination != null ? !_destination.equals(bridge._destination) : bridge._destination != null)
        {
            return false;
        }
        if (_excludes != null ? !_excludes.equals(bridge._excludes) : bridge._excludes != null)
        {
            return false;
        }
        if (_key != null ? !_key.equals(bridge._key) : bridge._key != null)
        {
            return false;
        }
        if (_source != null ? !_source.equals(bridge._source) : bridge._source != null)
        {
            return false;
        }
        if (_tag != null ? !_tag.equals(bridge._tag) : bridge._tag != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (_durable ? 1 : 0);
        result = 31 * result + (_dynamic ? 1 : 0);
        result = 31 * result + (_queueBridge ? 1 : 0);
        result = 31 * result + (_localSource ? 1 : 0);
        result = 31 * result + (_source != null ? _source.hashCode() : 0);
        result = 31 * result + (_destination != null ? _destination.hashCode() : 0);
        result = 31 * result + (_key != null ? _key.hashCode() : 0);
        result = 31 * result + (_tag != null ? _tag.hashCode() : 0);
        result = 31 * result + (_excludes != null ? _excludes.hashCode() : 0);
        return result;
    }

    public void setSession(final Session session)
    {
        _session = session;
        _delegate.setSession(session);
    }

    private long getMessageWindowSize()
    {
        return 10l;
    }


    VirtualHost getVirtualHost()
    {
        return _link.getVirtualHost();
    }

    public void close()
    {
        // TODO
        _delegate.close();
        _session = null;
    }


    private interface BridgeImpl
    {
        void setSession(Session session);

        void close();
    }

    private abstract class AbstractPullBridge implements BridgeImpl, SessionListener
    {
        public final void setSession(final Session session)
        {
            session.setSessionListener(this);
            onSession();

        }

        abstract void onSession();



        public void message(final Session ssn, final MessageTransfer xfr)
        {
            ExchangeRegistry exchangeRegistry = getVirtualHost().getExchangeRegistry();

            Exchange exchange = exchangeRegistry.getExchange(_destination);

            // TODO - deal with exchange not existing

            DeliveryProperties delvProps = null;
            if(xfr.getHeader() != null && (delvProps = xfr.getHeader().get(DeliveryProperties.class)) != null && delvProps.hasTtl() && !delvProps.hasExpiration())
            {
                delvProps.setExpiration(System.currentTimeMillis() + delvProps.getTtl());
            }

            MessageMetaData_0_10 messageMetaData = new MessageMetaData_0_10(xfr);
            final MessageStore store = getVirtualHost().getMessageStore();
            StoredMessage<MessageMetaData_0_10> storeMessage = store.addMessage(messageMetaData);
            storeMessage.addContent(0,xfr.getBody());
            storeMessage.flushToStore();
            MessageTransferMessage message = new MessageTransferMessage(storeMessage, ((ServerSession)_session).getReference());

            ArrayList<? extends BaseQueue> queues = exchange.route(message);



            if(queues != null && queues.size() != 0)
            {
                enqueue(message, queues);
            }
            else
            {
                if(delvProps == null || !delvProps.hasDiscardUnroutable() || !delvProps.getDiscardUnroutable())
                {
                    if(xfr.getAcceptMode() == MessageAcceptMode.EXPLICIT)
                    {
                        RangeSet rejects = new RangeSet();
                        rejects.add(xfr.getId());
                        MessageReject reject = new MessageReject(rejects, MessageRejectCode.UNROUTABLE, "Unroutable");
                        ssn.invoke(reject);
                    }
                    else
                    {
                        Exchange alternate = exchange.getAlternateExchange();
                        if(alternate != null)
                        {
                            queues = alternate.route(message);
                            if(queues != null && queues.size() != 0)
                            {
                                enqueue(message, queues);
                            }
                            else
                            {
                                //TODO - log the message discard
                            }
                        }
                        else
                        {
                            //TODO - log the message discard
                        }


                    }
                }


            }

            ssn.processed(xfr);

        }


        private void enqueue(final ServerMessage message, final ArrayList<? extends BaseQueue> queues)
        {
            _transaction.enqueue(queues,message, new ServerTransaction.Action()
                        {

                            BaseQueue[] _queues = queues.toArray(new BaseQueue[queues.size()]);

                            public void postCommit()
                            {
                                for(int i = 0; i < _queues.length; i++)
                                {
                                    try
                                    {
                                        _queues[i].enqueue(message);
                                    }
                                    catch (AMQException e)
                                    {
                                        // TODO

                                        throw new RuntimeException(e);
                                    }
                                }
                            }

                            public void onRollback()
                            {
                                // NO-OP
                            }
                        });

        }

        public void exception(final Session session, final SessionException exception)
        {
            // TODO - Handle exceptions
        }

        public void closed(final Session session)
        {
            // TODO - handle close
        }

        public void opened(final Session session)
        {
            // this method never called
        }

        public void resumed(final Session session)
        {
            // will never resume these sessions
        }



    }

    private final class StaticExchangePullBridge extends AbstractPullBridge
    {
        private final String _tmpQueueName = "bridge_queue_" + _bridgeNo + "_" + _link.getFederationTag();;

        public void onSession()
        {

            final HashMap<String, Object> options = new HashMap<String, Object>();
            options.put("qpid.trace.exclude", _link.getFederationTag());
            options.put("qpid.trace.id",_link.getRemoteFederationTag());
            _session.queueDeclare(_tmpQueueName,null, options, Option.AUTO_DELETE, Option.EXCLUSIVE);
            _session.sync();
            // todo check exception
            final Map<String,Object> bindingArgs = new HashMap<String,Object>();
            _session.exchangeBind(_tmpQueueName, _source, _key, bindingArgs);
            _session.sync();
            // todo check exception

            final Map<String,Object> subscribeOptions = Collections.EMPTY_MAP;
            final String subName = String.valueOf(_bridgeNo);
            _session.messageSubscribe(_tmpQueueName,
                                      subName,MessageAcceptMode.NONE,MessageAcquireMode.PRE_ACQUIRED,null,0l, subscribeOptions);
            _session.sync();
            // todo check exception

            _session.messageSetFlowMode(subName,MessageFlowMode.WINDOW);
            _session.messageFlow(subName, MessageCreditUnit.MESSAGE, getMessageWindowSize());
            _session.messageFlow(subName, MessageCreditUnit.BYTE, 0xFFFFFFFF);

        }

        public void close()
        {
            // TODO
        }
    }

    private final class StaticQueuePullBridge extends AbstractPullBridge
    {

        public void onSession()
        {

            final Map<String,Object> subscribeOptions = Collections.EMPTY_MAP;
            final String subName = String.valueOf(_bridgeNo);
            _session.messageSubscribe(_source,
                                      subName,MessageAcceptMode.NONE,MessageAcquireMode.PRE_ACQUIRED,null,0l, subscribeOptions);
            _session.sync();
            // todo check exception

            _session.messageSetFlowMode(subName,MessageFlowMode.WINDOW);
            _session.messageFlow(subName, MessageCreditUnit.MESSAGE, getMessageWindowSize());
            _session.messageFlow(subName, MessageCreditUnit.BYTE, 0xFFFFFFFF);

        }

        public void close()
        {
            // TODO
        }
    }

    private final class DynamicExchangeBridge extends AbstractPullBridge implements Exchange.BindingListener
    {
        private final String _tmpQueueName = "bridge_queue_" + _bridgeNo + "_" + _link.getFederationTag();

        private final ConcurrentMap<Binding,Binding> _bindings = new ConcurrentHashMap<Binding,Binding>();


        void onSession()
        {


            final HashMap<String, Object> options = new HashMap<String, Object>();
            options.put("qpid.trace.exclude", _link.getFederationTag());
            options.put("qpid.trace.id",_link.getRemoteFederationTag());
            _session.queueDeclare(_tmpQueueName,null, options, Option.AUTO_DELETE, Option.EXCLUSIVE);
            _session.sync();
            // todo - check exception

            final Map<String,Object> subscribeOptions = Collections.EMPTY_MAP;
            final String subName = String.valueOf(_bridgeNo);
            _session.messageSubscribe(_tmpQueueName,
                                      subName,MessageAcceptMode.NONE,MessageAcquireMode.PRE_ACQUIRED,null,0l, subscribeOptions);
            _session.sync();
            // todo check exception
            _session.messageSetFlowMode(subName,MessageFlowMode.WINDOW);
            _session.messageFlow(subName, MessageCreditUnit.MESSAGE, getMessageWindowSize());
            _session.messageFlow(subName, MessageCreditUnit.BYTE, 0xFFFFFFFF);
            _session.sync();
            // todo check exception


            ExchangeRegistry exchangeRegistry = getVirtualHost().getExchangeRegistry();

            Exchange exchange = exchangeRegistry.getExchange(_destination);

            // TODO - check null

            exchange.addBindingListener(this);

            Collection<Binding> bindings = exchange.getBindings();
            for(Binding binding : bindings)
            {
                propogateBinding(binding);
            }

        }

        private void propogateBinding(final Binding binding)
        {
            if(_bindings.putIfAbsent(binding,binding)== null)
            {
                Map<String,Object> arguments = new HashMap<String,Object>(binding.getArguments());

                if(arguments.get("qpid.fed.origin") == null)
                {
                    arguments.put("qpid.fed.op","");
                    arguments.put("qpid.fed.origin",_link.getFederationTag());
                    arguments.put("qpid.fed.tags",_link.getFederationTag());
                }
                else
                {
                    String tags = (String) arguments.get("qpid.fed.tags");
                    if(tags == null)
                    {
                        tags = _link.getFederationTag();
                    }
                    else
                    {
                        if(Arrays.asList(tags.split(",")).contains(_link.getFederationTag()))
                        {
                            return;
                        }
                        tags += "," + _link.getFederationTag();
                    }
                    arguments.put("qpid.fed.tags", tags);
                }

                _session.exchangeBind(_tmpQueueName, _source, binding.getBindingKey(), arguments);
                _session.sync();
                // TODO - check exception?

            }
        }

        private void propogateBindingRemoval(final Binding binding)
        {
            if(_bindings.remove(binding) != null)
            {
                // TODO - this is wrong!!!!
                _session.exchangeUnbind(_tmpQueueName, _source, binding.getBindingKey());
            }
        }


        public void bindingAdded(final Exchange exchange, final Binding binding)
        {
            propogateBinding(binding);
        }

        public void bindingRemoved(final Exchange exchange, final Binding binding)
        {
            propogateBindingRemoval(binding);
        }

        public void close()
        {
            // TODO
        }
    }

    private class StaticExchangePushBridge implements BridgeImpl, SessionListener
    {
        private final String _tmpQueueName = "bridge_queue_" + _bridgeNo + "_" + _link.getFederationTag();
        private AMQQueue _queue;

        public void setSession(final Session session)
        {
            assert session instanceof ServerSession;

            session.setSessionListener(this);

            ExchangeRegistry exchangeRegistry = getVirtualHost().getExchangeRegistry();

            Exchange exchange = exchangeRegistry.getExchange(_source);

            // TODO - Check null

            final HashMap<String, Object> options = new HashMap<String, Object>();
            options.put("qpid.trace.exclude", _link.getFederationTag());
            options.put("qpid.trace.id",_link.getRemoteFederationTag());

            try
            {
                _queue = AMQQueueFactory.createAMQQueueImpl(_tmpQueueName,
                                                        isDurable(),
                                                        _link.getFederationTag(),
                                                        false,
                                                        false,
                                                        getVirtualHost(),
                                                        options);
            }
            catch (AMQException e)
            {
                // TODO
                throw new RuntimeException(e);
            }
                
            FlowCreditManager_0_10 creditManager = new WindowCreditManager(0xFFFFFFFF,getMessageWindowSize());

            //TODO Handle the passing of non-null Filters and Arguments here
            
            Subscription_0_10 sub = new Subscription_0_10((ServerSession)session,
                                                          _destination,
                                                          MessageAcceptMode.NONE,
                                                          MessageAcquireMode.PRE_ACQUIRED,
                                                          MessageFlowMode.WINDOW,
                                                          creditManager, null,null);

            ((ServerSession)session).register(_destination, sub);

            try
            {
                _queue.registerSubscription(sub, true);
                getVirtualHost().getBindingFactory().addBinding(_key, _queue, exchange, Collections.<String, Object>emptyMap());
            }
            catch (AMQException e)
            {
                // TODO
                throw new RuntimeException(e);
            }
        }

        public void close()
        {
            // TODO
        }

        public void opened(final Session session)
        {
            // this method never called
        }

        public void resumed(final Session session)
        {
            // this session will never be resumed
        }

        public void message(final Session ssn, final MessageTransfer xfr)
        {
            // messages should not be sent ... should probably log error
        }

        public void exception(final Session session, final SessionException exception)
        {
            // TODO
        }

        public void closed(final Session session)
        {
            // TODO
        }
    }

    private class StaticQueuePushBridge implements BridgeImpl, SessionListener
    {
        private AMQQueue _queue;

        public void setSession(final Session session)
        {
            assert session instanceof ServerSession;

            session.setSessionListener(this);

            QueueRegistry queueRegistry = getVirtualHost().getQueueRegistry();

            _queue = queueRegistry.getQueue(_source);

            // TODO - null check

            FlowCreditManager_0_10 creditManager = new WindowCreditManager(0xFFFFFFFF,getMessageWindowSize());

          //TODO Handle the passing of non-null Filters and Arguments here
            
            Subscription_0_10 sub = new Subscription_0_10((ServerSession)session,
                                                          _destination,
                                                          MessageAcceptMode.NONE,
                                                          MessageAcquireMode.PRE_ACQUIRED,
                                                          MessageFlowMode.WINDOW,
                                                          creditManager, null,null);

            ((ServerSession)session).register(_destination, sub);

            try
            {
                _queue.registerSubscription(sub, false);
            }
            catch (AMQException e)
            {
                // TODO
                throw new RuntimeException(e);
            }

        }

        public void close()
        {
            // TODO
        }

        public void opened(final Session session)
        {
            // never called
        }

        public void resumed(final Session session)
        {
            // session will not resume
        }

        public void message(final Session ssn, final MessageTransfer xfr)
        {
            // should never be called ... should probably log error
        }

        public void exception(final Session session, final SessionException exception)
        {
            // TODO
        }

        public void closed(final Session session)
        {
            // TODO
        }
    }

}
