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
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultExchangeRegistry implements ExchangeRegistry
{
    private static final Logger LOGGER = Logger.getLogger(DefaultExchangeRegistry.class);
    /**
     * Maps from exchange name to exchange instance
     */
    private ConcurrentMap<String, Exchange> _exchangeMap = new ConcurrentHashMap<String, Exchange>();

    private Exchange _defaultExchange;

    private final VirtualHost _host;
    private final QueueRegistry _queueRegistry;

    private final Collection<RegistryChangeListener> _listeners =
            Collections.synchronizedCollection(new ArrayList<RegistryChangeListener>());

    public DefaultExchangeRegistry(VirtualHost host, QueueRegistry queueRegistry)
    {
        _host = host;
        _queueRegistry = queueRegistry;
    }

    public void initialise(ExchangeFactory exchangeFactory) throws AMQException
    {
        //create 'standard' exchanges:
        new ExchangeInitialiser().initialise(exchangeFactory, this, getDurableConfigurationStore());

        _defaultExchange = new DefaultExchange(_queueRegistry);

        UUID defaultExchangeId =
                UUIDGenerator.generateExchangeUUID(ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString(), _host.getName());

        _defaultExchange.initialise(defaultExchangeId, _host, ExchangeDefaults.DEFAULT_EXCHANGE_NAME,false, false);

    }

    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _host.getDurableConfigurationStore();
    }

    public void registerExchange(Exchange exchange) throws AMQException
    {
        _exchangeMap.put(exchange.getNameShortString().toString(), exchange);
        synchronized (_listeners)
        {
            for(RegistryChangeListener listener : _listeners)
            {
                listener.exchangeRegistered(exchange);
            }

        }
    }

    public void setDefaultExchange(Exchange exchange)
    {
        _defaultExchange = exchange;
    }

    public Exchange getDefaultExchange()
    {
        return _defaultExchange;
    }

    public void unregisterExchange(String name, boolean inUse) throws AMQException
    {
        final Exchange exchange = _exchangeMap.get(name);
        if (exchange == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Unknown exchange " + name, null);
        }

        if (!_host.getSecurityManager().authoriseDelete(exchange))
        {
            throw new AMQSecurityException();
        }

        // TODO: check inUse argument

        Exchange e = _exchangeMap.remove(name);
        if (e != null)
        {
            e.close();

            synchronized (_listeners)
            {
                for(RegistryChangeListener listener : _listeners)
                {
                    listener.exchangeUnregistered(exchange);
                }
            }

        }
        else
        {
            throw new AMQException("Unknown exchange " + name);
        }
    }

    public Collection<Exchange> getExchanges()
    {
        return new ArrayList<Exchange>(_exchangeMap.values());
    }

    public void addRegistryChangeListener(RegistryChangeListener listener)
    {
        _listeners.add(listener);
    }

    public Exchange getExchange(String name)
    {
        if ((name == null) || name.length() == 0)
        {
            return getDefaultExchange();
        }
        else
        {
            return _exchangeMap.get(name);
        }
    }

    @Override
    public void clearAndUnregisterMbeans()
    {
        for (final Exchange exchange : getExchanges())
        {
            //TODO: this is a bit of a hack, what if the listeners aren't aware
            //that we are just unregistering the MBean because of HA, and aren't
            //actually removing the exchange as such.
            synchronized (_listeners)
            {
                for(RegistryChangeListener listener : _listeners)
                {
                    listener.exchangeUnregistered(exchange);
                }
            }
        }
        _exchangeMap.clear();
    }

    @Override
    public synchronized Exchange getExchange(UUID exchangeId)
    {
        if (exchangeId == null)
        {
            return getDefaultExchange();
        }
        else
        {
            Collection<Exchange> exchanges = _exchangeMap.values();
            for (Exchange exchange : exchanges)
            {
                if (exchange.getId().equals(exchangeId))
                {
                    return exchange;
                }
            }
            return null;
        }
    }

    public boolean isReservedExchangeName(String name)
    {
        if (name == null || ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString().equals(name)
                || name.startsWith("amq.") || name.startsWith("qpid."))
        {
            return true;
        }
        Collection<ExchangeType<? extends Exchange>> registeredTypes = _host.getExchangeTypes();
        for (ExchangeType<? extends Exchange> type : registeredTypes)
        {
            if (type.getDefaultExchangeName().toString().equals(name))
            {
                return true;
            }
        }
        return false;
    }
}
