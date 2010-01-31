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
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class FanoutExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(FanoutExchange.class);

    private static final Integer ONE = Integer.valueOf(1);

    /**
     * Maps from queue name to queue instances
     */
    private final ConcurrentHashMap<AMQQueue,Integer> _queues = new ConcurrentHashMap<AMQQueue,Integer>();

    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new FanoutExchangeMBean(this);
    }

    public Logger getLogger()
    {
        return _logger;
    }

    public static final ExchangeType<FanoutExchange> TYPE = new ExchangeType<FanoutExchange>()
    {

    	public AMQShortString getName()
    	{
    		return ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
    	}

    	public Class<FanoutExchange> getExchangeClass()
    	{
    		return FanoutExchange.class;
    	}

    	public FanoutExchange newInstance(VirtualHost host,
    									  AMQShortString name,
    									  boolean durable,
    									  int ticket,
    									  boolean autoDelete) throws AMQException
    	{
    		FanoutExchange exch = new FanoutExchange();
    		exch.initialise(host, name, durable, ticket, autoDelete);
    		return exch;
    	}

    	public AMQShortString getDefaultExchangeName()
    	{
    		return ExchangeDefaults.FANOUT_EXCHANGE_NAME;
    	}
    };

    public FanoutExchange()
    {
        super(TYPE);
    }

    public ArrayList<BaseQueue> doRoute(InboundMessage payload)
    {


        if (_logger.isDebugEnabled())
        {
            _logger.debug("Publishing message to queue " + _queues);
        }

        for(Binding b : getBindings())
        {
            b.incrementMatches();
        }

        return new ArrayList<BaseQueue>(_queues.keySet());

    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return _queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {

        return (_queues != null) && !_queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {

        return _queues.contains(queue);
    }

    public boolean hasBindings()
    {
        return !_queues.isEmpty();
    }

    protected void onBind(final Binding binding)
    {
        AMQQueue queue = binding.getQueue();
        assert queue != null;

        Integer oldVal;

        if((oldVal = _queues.putIfAbsent(queue, ONE)) != null)
        {
            Integer newVal = oldVal+1;
            while(!_queues.replace(queue, oldVal, newVal))
            {
                oldVal = _queues.get(queue);
                if(oldVal == null)
                {
                    oldVal = _queues.putIfAbsent(queue, ONE);
                    if(oldVal == null)
                    {
                        break;
                    }
                }
                newVal = oldVal + 1;
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Binding queue " + queue
                          + " with routing key " + new AMQShortString(binding.getBindingKey()) + " to exchange " + this);
        }
    }

    protected void onUnbind(final Binding binding)
    {
        AMQQueue queue = binding.getQueue();
        Integer oldValue = _queues.get(queue);

        boolean done = false;

        while(!(done || oldValue == null))
        {
            while(!(done || oldValue == null) && oldValue.intValue() == 1)
            {
                if(!_queues.remove(queue, oldValue))
                {
                    oldValue = _queues.get(queue);
                }
                else
                {
                    done = true;
                }
            }
            while(!(done || oldValue == null) && oldValue.intValue() != 1)
            {
                Integer newValue = oldValue - 1;
                if(!_queues.replace(queue, oldValue, newValue))
                {
                    oldValue = _queues.get(queue);
                }
                else
                {
                    done = true;
                }
            }
        }
    }
}
