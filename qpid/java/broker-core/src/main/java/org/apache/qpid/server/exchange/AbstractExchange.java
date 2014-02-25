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
package org.apache.qpid.server.exchange;

import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.logging.subjects.ExchangeLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.UnknownExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractExchange<T extends Exchange> implements Exchange<T>
{
    private static final Logger _logger = Logger.getLogger(AbstractExchange.class);
    private String _name;
    private final AtomicBoolean _closed = new AtomicBoolean();

    private Exchange _alternateExchange;

    private boolean _durable;

    private VirtualHost _virtualHost;

    private final List<Action<Exchange>> _closeTaskList = new CopyOnWriteArrayList<Action<Exchange>>();

    /**
     * Whether the exchange is automatically deleted once all queues have detached from it
     */
    private boolean _autoDelete;

    //The logSubject for ths exchange
    private LogSubject _logSubject;
    private Map<ExchangeReferrer,Object> _referrers = new ConcurrentHashMap<ExchangeReferrer,Object>();

    private final CopyOnWriteArrayList<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private final UUID _id;
    private final AtomicInteger _bindingCountHigh = new AtomicInteger();
    private final AtomicLong _receivedMessageCount = new AtomicLong();
    private final AtomicLong _receivedMessageSize = new AtomicLong();
    private final AtomicLong _routedMessageCount = new AtomicLong();
    private final AtomicLong _routedMessageSize = new AtomicLong();
    private final AtomicLong _droppedMessageCount = new AtomicLong();
    private final AtomicLong _droppedMessageSize = new AtomicLong();

    private final CopyOnWriteArrayList<Exchange.BindingListener> _listeners = new CopyOnWriteArrayList<Exchange.BindingListener>();

    private final ConcurrentHashMap<BindingIdentifier, Binding> _bindingsMap = new ConcurrentHashMap<BindingIdentifier, Binding>();


    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();
    private StateChangeListener<Binding, State> _bindingListener;

    public AbstractExchange(VirtualHost vhost, Map<String, Object> attributes) throws UnknownExchangeException
    {
        _virtualHost = vhost;

        _id = MapValueConverter.getUUIDAttribute(org.apache.qpid.server.model.Exchange.ID, attributes);
        _name = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.NAME, attributes);
        _durable = MapValueConverter.getBooleanAttribute(org.apache.qpid.server.model.Exchange.DURABLE, attributes);
        _autoDelete = MapValueConverter.getEnumAttribute(LifetimePolicy.class, org.apache.qpid.server.model.Exchange.LIFETIME_POLICY, attributes, LifetimePolicy.PERMANENT) != LifetimePolicy.PERMANENT;
        _logSubject = new ExchangeLogSubject(this, this.getVirtualHost());

        // check ACL
        _virtualHost.getSecurityManager().authoriseCreateExchange(this);

        Object alternateExchangeAttr = attributes.get(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE);
        if(alternateExchangeAttr != null)
        {
            if(alternateExchangeAttr instanceof Exchange)
            {
                setAlternateExchange((Exchange) alternateExchangeAttr);
            }
            else if(alternateExchangeAttr instanceof UUID)
            {
                setAlternateExchange(vhost.getExchange((UUID) alternateExchangeAttr));
            }
            else if(alternateExchangeAttr instanceof String)
            {
                setAlternateExchange(vhost.getExchange((String) alternateExchangeAttr));
                if(_alternateExchange == null)
                {
                    try
                    {
                        UUID altExcAsUUID = UUID.fromString((String)alternateExchangeAttr);
                        setAlternateExchange(vhost.getExchange(altExcAsUUID));
                    }
                    catch (IllegalArgumentException e)
                    {
                        // ignore - we'll throw an exception shortly because _alternateExchange will be null
                    }
                }
            }
            if(_alternateExchange == null)
            {
                throw new UnknownExchangeException(alternateExchangeAttr.toString());
            }

        }
        _bindingListener = new StateChangeListener<Binding, State>()
        {
            @Override
            public void stateChanged(final Binding binding, final State oldState, final State newState)
            {
                if(newState == State.DELETED)
                {
                    removeBinding(binding);
                }
            }
        };
        // Log Exchange creation
        CurrentActor.get().message(ExchangeMessages.CREATED(getExchangeType().getType(), _name, _durable));
    }

    public abstract ExchangeType<T> getExchangeType();

    @Override
    public String getTypeName()
    {
        return getExchangeType().getType();
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public void close()
    {

        if(_closed.compareAndSet(false,true))
        {
            List<Binding> bindings = new ArrayList<Binding>(_bindings);
            for(Binding binding : bindings)
            {
                binding.removeStateChangeListener(_bindingListener);
                binding.delete();
            }

            if(_alternateExchange != null)
            {
                _alternateExchange.removeReference(this);
            }

            CurrentActor.get().message(_logSubject, ExchangeMessages.DELETED());

            for(Action<Exchange> task : _closeTaskList)
            {
                task.performAction(this);
            }
            _closeTaskList.clear();
        }
    }

    public String toString()
    {
        return getClass().getSimpleName() + "[" + getName() +"]";
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public final boolean isBound(String bindingKey, Map<String,Object> arguments, AMQQueue queue)
    {
        for(Binding b : _bindings)
        {
            if(bindingKey.equals(b.getBindingKey()) && queue == b.getAMQQueue())
            {
                return (b.getArguments() == null || b.getArguments().isEmpty())
                       ? (arguments == null || arguments.isEmpty())
                       : b.getArguments().equals(arguments);
            }
        }
        return false;
    }

    public final boolean isBound(String bindingKey, AMQQueue queue)
    {
        for(Binding b : _bindings)
        {
            if(bindingKey.equals(b.getBindingKey()) && queue == b.getAMQQueue())
            {
                return true;
            }
        }
        return false;
    }

    public final boolean isBound(String bindingKey)
    {
        for(Binding b : _bindings)
        {
            if(bindingKey.equals(b.getBindingKey()))
            {
                return true;
            }
        }
        return false;
    }

    public final boolean isBound(AMQQueue queue)
    {
        for(Binding b : _bindings)
        {
            if(queue == b.getAMQQueue())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public final boolean isBound(Map<String, Object> arguments, AMQQueue queue)
    {
        for(Binding b : _bindings)
        {
            if(queue == b.getAMQQueue() &&
               ((b.getArguments() == null || b.getArguments().isEmpty())
                       ? (arguments == null || arguments.isEmpty())
                       : b.getArguments().equals(arguments)))
            {
                return true;
            }
        }
        return false;
    }


    public final boolean isBound(Map<String, Object> arguments)
    {
        for(Binding b : _bindings)
        {
            if(((b.getArguments() == null || b.getArguments().isEmpty())
                                   ? (arguments == null || arguments.isEmpty())
                                   : b.getArguments().equals(arguments)))
            {
                return true;
            }
        }
        return false;
    }


    @Override
    public final boolean isBound(String bindingKey, Map<String, Object> arguments)
    {
        for(Binding b : _bindings)
        {
            if(b.getBindingKey().equals(bindingKey) &&
               ((b.getArguments() == null || b.getArguments().isEmpty())
                       ? (arguments == null || arguments.isEmpty())
                       : b.getArguments().equals(arguments)))
            {
                return true;
            }
        }
        return false;
    }

    public final boolean hasBindings()
    {
        return !_bindings.isEmpty();
    }

    public Exchange getAlternateExchange()
    {
        return _alternateExchange;
    }

    public void setAlternateExchange(Exchange exchange)
    {
        if(_alternateExchange != null)
        {
            _alternateExchange.removeReference(this);
        }
        if(exchange != null)
        {
            exchange.addReference(this);
        }
        _alternateExchange = exchange;

    }

    public void removeReference(ExchangeReferrer exchange)
    {
        _referrers.remove(exchange);
    }

    public void addReference(ExchangeReferrer exchange)
    {
        _referrers.put(exchange, Boolean.TRUE);
    }

    public boolean hasReferrers()
    {
        return !_referrers.isEmpty();
    }

    public void addCloseTask(final Action<Exchange> task)
    {
        _closeTaskList.add(task);
    }

    public void removeCloseTask(final Action<Exchange> task)
    {
        _closeTaskList.remove(task);
    }

    public final void doAddBinding(final Binding binding)
    {
        _bindings.add(binding);
        int bindingCountSize = _bindings.size();
        int maxBindingsSize;
        while((maxBindingsSize = _bindingCountHigh.get()) < bindingCountSize)
        {
            _bindingCountHigh.compareAndSet(maxBindingsSize, bindingCountSize);
        }
        for(BindingListener listener : _listeners)
        {
            listener.bindingAdded(this, binding);
        }
        onBind(binding);
    }

    public long getBindingCountHigh()
    {
        return _bindingCountHigh.get();
    }

    public final void doRemoveBinding(final Binding binding)
    {
        onUnbind(binding);
        for(BindingListener listener : _listeners)
        {
            listener.bindingRemoved(this, binding);
        }
        _bindings.remove(binding);
    }

    public final Collection<Binding> getBindings()
    {
        return Collections.unmodifiableList(_bindings);
    }

    protected abstract void onBind(final Binding binding);

    protected abstract void onUnbind(final Binding binding);


    public String getName()
    {
        return _name.toString();
    }

    public Map<String, Object> getArguments()
    {
        return Collections.emptyMap();
    }

    public UUID getId()
    {
        return _id;
    }

    public long getBindingCount()
    {
        return getBindings().size();
    }


    final List<? extends BaseQueue> route(final ServerMessage message,
                                          final InstanceProperties instanceProperties)
    {
        _receivedMessageCount.incrementAndGet();
        _receivedMessageSize.addAndGet(message.getSize());
        List<? extends BaseQueue> queues = doRoute(message, instanceProperties);
        List<? extends BaseQueue> allQueues = queues;

        boolean deletedQueues = false;

        for(BaseQueue q : allQueues)
        {
            if(q.isDeleted())
            {
                if(!deletedQueues)
                {
                    deletedQueues = true;
                    queues = new ArrayList<BaseQueue>(allQueues);
                }
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Exchange: " + getName() + " - attempt to enqueue message onto deleted queue " + q.getName());
                }
                queues.remove(q);
            }
        }


        if(!queues.isEmpty())
        {
            _routedMessageCount.incrementAndGet();
            _routedMessageSize.addAndGet(message.getSize());
        }
        else
        {
            _droppedMessageCount.incrementAndGet();
            _droppedMessageSize.addAndGet(message.getSize());
        }
        return queues;
    }

    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                          final InstanceProperties instanceProperties,
                          final ServerTransaction txn,
                          final Action<? super MessageInstance<?, ? extends Consumer>> postEnqueueAction)
    {
        List<? extends BaseQueue> queues = route(message, instanceProperties);

        if(queues == null || queues.isEmpty())
        {
            Exchange altExchange = getAlternateExchange();
            if(altExchange != null)
            {
                return altExchange.send(message, instanceProperties, txn, postEnqueueAction);
            }
            else
            {
                return 0;
            }
        }
        else
        {
            final BaseQueue[] baseQueues = queues.toArray(new BaseQueue[queues.size()]);

            txn.enqueue(queues,message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        for(int i = 0; i < baseQueues.length; i++)
                        {
                            baseQueues[i].enqueue(message, postEnqueueAction);
                        }
                    }
                    finally
                    {
                        _reference.release();
                    }
                }

                public void onRollback()
                {
                    _reference.release();
                }
            });
            return queues.size();
        }
    }

    protected abstract List<? extends BaseQueue> doRoute(final ServerMessage message,
                                                         final InstanceProperties instanceProperties);

    public long getMsgReceives()
    {
        return _receivedMessageCount.get();
    }

    public long getMsgRoutes()
    {
        return _routedMessageCount.get();
    }

    public long getMsgDrops()
    {
        return _droppedMessageCount.get();
    }

    public long getByteReceives()
    {
        return _receivedMessageSize.get();
    }

    public long getByteRoutes()
    {
        return _routedMessageSize.get();
    }

    public long getByteDrops()
    {
        return _droppedMessageSize.get();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void addBindingListener(final BindingListener listener)
    {
        _listeners.add(listener);
    }

    public void removeBindingListener(final BindingListener listener)
    {
        _listeners.remove(listener);
    }

    @Override
    public boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        return makeBinding(null, bindingKey, queue, arguments, false, false);
    }

    @Override
    public boolean replaceBinding(final UUID id, final String bindingKey,
                                  final AMQQueue queue,
                                  final Map<String, Object> arguments)
    {
        return makeBinding(id, bindingKey, queue, arguments, false, true);
    }

    @Override
    public void restoreBinding(final UUID id, final String bindingKey, final AMQQueue queue,
                               final Map<String, Object> argumentMap)
    {
        makeBinding(id, bindingKey,queue, argumentMap,true, false);
    }

    private void removeBinding(final Binding binding)
    {
        String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getAMQQueue();

        assert queue != null;

        if (bindingKey == null)
        {
            bindingKey = "";
        }

        // Check access
        _virtualHost.getSecurityManager().authoriseUnbind(this, bindingKey, queue);

        Binding b = _bindingsMap.remove(new BindingIdentifier(bindingKey,queue));

        if (b != null)
        {
            doRemoveBinding(b);
            queue.removeBinding(b);

            if (b.isDurable())
            {
                DurableConfigurationStoreHelper.removeBinding(_virtualHost.getDurableConfigurationStore(), b);
            }
            b.delete();
        }

    }


    @Override
    public Binding getBinding(String bindingKey, AMQQueue queue)
    {
        assert queue != null;

        if(bindingKey == null)
        {
            bindingKey = "";
        }

        return _bindingsMap.get(new BindingIdentifier(bindingKey,queue));
    }

    private boolean makeBinding(UUID id,
                                String bindingKey,
                                AMQQueue queue,
                                Map<String, Object> arguments,
                                boolean restore,
                                boolean force)
    {
        assert queue != null;

        if (bindingKey == null)
        {
            bindingKey = "";
        }
        if (arguments == null)
        {
            arguments = Collections.emptyMap();
        }

        if (id == null)
        {
            id = UUIDGenerator.generateBindingUUID(getName(),
                                                   queue.getName(),
                                                   bindingKey,
                                                   _virtualHost.getName());
        }
        Binding b = new Binding(id, bindingKey, queue, this, arguments);
        Binding existingMapping = _bindingsMap.putIfAbsent(new BindingIdentifier(bindingKey,queue), b);
        if (existingMapping == null || force)
        {
            b.addStateChangeListener(_bindingListener);
            if (existingMapping != null)
            {
                existingMapping.delete();
            }

            if (b.isDurable() && !restore)
            {
                DurableConfigurationStoreHelper.createBinding(_virtualHost.getDurableConfigurationStore(), b);
            }

            queue.addBinding(b);
            doAddBinding(b);

            return true;
        }
        else
        {
            return false;
        }
    }


    private static final class BindingIdentifier
    {
        private final String _bindingKey;
        private final AMQQueue _destination;

        private BindingIdentifier(final String bindingKey, final AMQQueue destination)
        {
            _bindingKey = bindingKey;
            _destination = destination;
        }

        public String getBindingKey()
        {
            return _bindingKey;
        }

        public AMQQueue getDestination()
        {
            return _destination;
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

            final BindingIdentifier that = (BindingIdentifier) o;

            if (!_bindingKey.equals(that._bindingKey))
            {
                return false;
            }
            if (!_destination.equals(that._destination))
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _bindingKey.hashCode();
            result = 31 * result + _destination.hashCode();
            return result;
        }
    }

}
