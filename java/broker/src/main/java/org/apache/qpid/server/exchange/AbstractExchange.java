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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ExchangeConfigType;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.logging.subjects.ExchangeLogSubject;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.util.ArrayList;
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

public abstract class AbstractExchange implements Exchange, Managable
{


    private AMQShortString _name;
    private final AtomicBoolean _closed = new AtomicBoolean();

    private Exchange _alternateExchange;

    protected boolean _durable;
    protected int _ticket;

    private VirtualHost _virtualHost;

    private final List<Exchange.Task> _closeTaskList = new CopyOnWriteArrayList<Exchange.Task>();


    protected AbstractExchangeMBean _exchangeMbean;

    /**
     * Whether the exchange is automatically deleted once all queues have detached from it
     */
    protected boolean _autoDelete;

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

    private final CopyOnWriteArrayList<Exchange.BindingListener> _listeners = new CopyOnWriteArrayList<Exchange.BindingListener>();

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();

    public AbstractExchange(final ExchangeType<? extends Exchange> type)
    {
        _type = type;
    }

    public AMQShortString getNameShortString()
    {
        return _name;
    }

    public final AMQShortString getTypeShortString()
    {
        return _type.getName();
    }

    /**
     * Concrete exchanges must implement this method in order to create the managed representation. This is
     * called during initialisation (template method pattern).
     * @return the MBean
     */
    protected abstract AbstractExchangeMBean createMBean() throws JMException;

    public void initialise(VirtualHost host, AMQShortString name, boolean durable, int ticket, boolean autoDelete)
            throws AMQException
    {
        _virtualHost = host;
        _name = name;
        _durable = durable;
        _autoDelete = autoDelete;
        _ticket = ticket;

        // TODO - fix
        _id = getConfigStore().createId();

        getConfigStore().addConfiguredObject(this);
        try
        {
            _exchangeMbean = createMBean();
            _exchangeMbean.register();
        }
        catch (JMException e)
        {
            getLogger().error(e);
        }
        _logSubject = new ExchangeLogSubject(this, this.getVirtualHost());

        // Log Exchange creation
        CurrentActor.get().message(ExchangeMessages.CREATED(String.valueOf(getTypeShortString()), String.valueOf(name), durable));
    }

    public ConfigStore getConfigStore()
    {
        return getVirtualHost().getConfigStore();
    }

    public abstract Logger getLogger();

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public int getTicket()
    {
        return _ticket;
    }

    public void close() throws AMQException
    {

        if(_closed.compareAndSet(false,true))
        {
            if (_exchangeMbean != null)
            {
                _exchangeMbean.unregister();
            }
            getConfigStore().removeConfiguredObject(this);
            if(_alternateExchange != null)
            {
                _alternateExchange.removeReference(this);
            }

            CurrentActor.get().message(_logSubject, ExchangeMessages.DELETED());

            for(Task task : _closeTaskList)
            {
                task.onClose(this);
            }
            _closeTaskList.clear();
        }
    }

    public String toString()
    {
        return getClass().getSimpleName() + "[" + getNameShortString() +"]";
    }

    public ManagedObject getManagedObject()
    {
        return _exchangeMbean;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public QueueRegistry getQueueRegistry()
    {
        return getVirtualHost().getQueueRegistry();
    }

    public boolean isBound(String bindingKey, Map<String,Object> arguments, AMQQueue queue)
    {
        return isBound(new AMQShortString(bindingKey), queue);
    }


    public boolean isBound(String bindingKey, AMQQueue queue)
    {
        return isBound(new AMQShortString(bindingKey), queue);
    }

    public boolean isBound(String bindingKey)
    {
        return isBound(new AMQShortString(bindingKey));
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

    public void addCloseTask(final Task task)
    {
        _closeTaskList.add(task);
    }

    public void removeCloseTask(final Task task)
    {
        _closeTaskList.remove(task);
    }

    public final void addBinding(final Binding binding)
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

    public final void removeBinding(final Binding binding)
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
        // TODO - Fix
        return Collections.EMPTY_MAP;
    }

    public UUID getId()
    {
        return _id;
    }

    public ExchangeConfigType getConfigType()
    {
        return ExchangeConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return _virtualHost;
    }

    public long getBindingCount()
    {
        return getBindings().size();
    }



    public final ArrayList<? extends BaseQueue> route(final InboundMessage message)
    {
        _receivedMessageCount.incrementAndGet();
        _receivedMessageSize.addAndGet(message.getSize());
        final ArrayList<? extends BaseQueue> queues = doRoute(message);
        if(queues != null && !queues.isEmpty())
        {
            _routedMessageCount.incrementAndGet();
            _routedMessageSize.addAndGet(message.getSize());
        }
        return queues;
    }

    protected abstract ArrayList<? extends BaseQueue> doRoute(final InboundMessage message);

    public long getMsgReceives()
    {
        return _receivedMessageCount.get();
    }

    public long getMsgRoutes()
    {
        return _routedMessageCount.get();
    }

    public long getByteReceives()
    {
        return _receivedMessageSize.get();
    }

    public long getByteRoutes()
    {
        return _routedMessageSize.get();
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
}
