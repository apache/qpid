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
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BindingMessages;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.logging.subjects.BindingLogSubject;
import org.apache.qpid.server.logging.subjects.ExchangeLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
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

public abstract class AbstractExchange implements Exchange
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
    private final ExchangeType<? extends Exchange> _type;
    private UUID _id;
    private final AtomicInteger _bindingCountHigh = new AtomicInteger();
    private final AtomicLong _receivedMessageCount = new AtomicLong();
    private final AtomicLong _receivedMessageSize = new AtomicLong();
    private final AtomicLong _routedMessageCount = new AtomicLong();
    private final AtomicLong _routedMessageSize = new AtomicLong();
    private final AtomicLong _droppedMessageCount = new AtomicLong();
    private final AtomicLong _droppedMessageSize = new AtomicLong();

    private final CopyOnWriteArrayList<Exchange.BindingListener> _listeners = new CopyOnWriteArrayList<Exchange.BindingListener>();

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();

    public AbstractExchange(final ExchangeType<? extends Exchange> type)
    {
        _type = type;
    }

    @Override
    public String getTypeName()
    {
        return _type.getType();
    }

    public void initialise(UUID id,
                           VirtualHost host,
                           String name,
                           boolean durable,
                           boolean autoDelete)
            throws AMQException
    {
        _virtualHost = host;
        _name = name;
        _durable = durable;
        _autoDelete = autoDelete;

        _id = id;
        _logSubject = new ExchangeLogSubject(this, this.getVirtualHost());

        // Log Exchange creation
        CurrentActor.get().message(ExchangeMessages.CREATED(getType().getType(), name, durable));
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public void close() throws AMQException
    {

        if(_closed.compareAndSet(false,true))
        {
            List<Binding> bindings = new ArrayList<Binding>(_bindings);
            for(Binding binding : bindings)
            {
                removeBinding(binding);
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
            if(bindingKey.equals(b.getBindingKey()) && queue == b.getQueue())
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
            if(bindingKey.equals(b.getBindingKey()) && queue == b.getQueue())
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
            if(queue == b.getQueue())
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
            if(queue == b.getQueue() &&
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

    public ExchangeType getType()
    {
        return _type;
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

    public final int send(final ServerMessage message,
                          final InstanceProperties instanceProperties,
                          final ServerTransaction txn,
                          final Action<QueueEntry> postEnqueueAction)
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
                    for(int i = 0; i < baseQueues.length; i++)
                    {
                        try
                        {
                            baseQueues[i].enqueue(message, postEnqueueAction);
                        }
                        catch (AMQException e)
                        {
                            // TODO
                            throw new RuntimeException(e);
                        }
                    }
                    _reference.release();
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
            throws AMQSecurityException, AMQInternalException
    {
        return makeBinding(null, bindingKey, queue, arguments, false, false);
    }

    @Override
    public boolean replaceBinding(final UUID id, final String bindingKey,
                                  final AMQQueue queue,
                                  final Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException
    {
        return makeBinding(id, bindingKey, queue, arguments, false, true);
    }

    @Override
    public void restoreBinding(final UUID id, final String bindingKey, final AMQQueue queue,
                               final Map<String, Object> argumentMap)
            throws AMQSecurityException, AMQInternalException
    {
        makeBinding(id, bindingKey,queue, argumentMap,true, false);
    }

    @Override
    public void removeBinding(final Binding b) throws AMQSecurityException, AMQInternalException
    {
        removeBinding(b.getBindingKey(), b.getQueue(), b.getArguments());
    }

    @Override
    public Binding removeBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException
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

        // The default exchange bindings must reflect the existence of queues, allow
        // all operations on it to succeed. It is up to the broker to prevent illegal
        // attempts at binding to this exchange, not the ACLs.
        // Check access
        if (!_virtualHost.getSecurityManager().authoriseUnbind(this, bindingKey, queue))
        {
            throw new AMQSecurityException("Permission denied: unbinding " + bindingKey);
        }

        BindingImpl b = _bindingsMap.remove(new BindingImpl(null, bindingKey,queue,arguments));

        if (b != null)
        {
            doRemoveBinding(b);
            queue.removeBinding(b);

            if (b.isDurable())
            {
                DurableConfigurationStoreHelper.removeBinding(_virtualHost.getDurableConfigurationStore(), b);
            }
            b.logDestruction();
        }

        return b;
    }


    @Override
    public Binding getBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        assert queue != null;

        if(bindingKey == null)
        {
            bindingKey = "";
        }

        if(arguments == null)
        {
            arguments = Collections.emptyMap();
        }

        BindingImpl b = new BindingImpl(null, bindingKey,queue,arguments);
        return _bindingsMap.get(b);
    }

    private final ConcurrentHashMap<BindingImpl, BindingImpl> _bindingsMap = new ConcurrentHashMap<BindingImpl, BindingImpl>();

    private boolean makeBinding(UUID id,
                                String bindingKey,
                                AMQQueue queue,
                                Map<String, Object> arguments,
                                boolean restore,
                                boolean force) throws AMQSecurityException, AMQInternalException
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

        //Perform ACLs
        if (!_virtualHost.getSecurityManager().authoriseBind(AbstractExchange.this, queue, bindingKey))
        {
            throw new AMQSecurityException("Permission denied: binding " + bindingKey);
        }

        if (id == null)
        {
            id = UUIDGenerator.generateBindingUUID(getName(),
                                                   queue.getName(),
                                                   bindingKey,
                                                   _virtualHost.getName());
        }
        BindingImpl b = new BindingImpl(id, bindingKey, queue, arguments);
        BindingImpl existingMapping = _bindingsMap.putIfAbsent(b, b);
        if (existingMapping == null || force)
        {
            if (existingMapping != null)
            {
                removeBinding(existingMapping);
            }

            if (b.isDurable() && !restore)
            {
                DurableConfigurationStoreHelper.createBinding(_virtualHost.getDurableConfigurationStore(), b);
            }

            queue.addBinding(b);
            doAddBinding(b);
            b.logCreation();

            return true;
        }
        else
        {
            return false;
        }
    }

    private final class BindingImpl extends Binding
    {
        private final BindingLogSubject _logSubject;
        //TODO : persist creation time
        private long _createTime = System.currentTimeMillis();

        private BindingImpl(UUID id,
                            String bindingKey,
                            final AMQQueue queue,
                            final Map<String, Object> arguments)
        {
            super(id, bindingKey, queue, AbstractExchange.this, arguments);
            _logSubject = new BindingLogSubject(bindingKey,AbstractExchange.this,queue);

        }

        public void onClose(final Exchange exchange) throws AMQSecurityException, AMQInternalException
        {
            removeBinding(this);
        }

        void logCreation()
        {
            CurrentActor.get().message(_logSubject, BindingMessages.CREATED(String.valueOf(getArguments()),
                                                                            getArguments() != null
                                                                            && !getArguments().isEmpty()));
        }

        void logDestruction()
        {
            CurrentActor.get().message(_logSubject, BindingMessages.DELETED());
        }

        public String getOrigin()
        {
            return (String) getArguments().get("qpid.fed.origin");
        }

        public long getCreateTime()
        {
            return _createTime;
        }

        public boolean isDurable()
        {
            return getQueue().isDurable() && getExchange().isDurable();
        }

    }



}
