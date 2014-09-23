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
package org.apache.qpid.client.messaging.address;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.client.AMQDestination.Binding;

public class Link
{
    public enum FilterType { SQL92, XQUERY, SUBJECT }
    
    public enum Reliability { UNRELIABLE, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE }
    
    private String name;
    private String _filter;
    private FilterType _filterType = FilterType.SUBJECT;
    private boolean _isNoLocal;
    private boolean _isDurable;
    private int _consumerCapacity = -1;
    private int _producerCapacity = -1;
    private Subscription subscription;
    private Reliability reliability = Reliability.AT_LEAST_ONCE;
    private List<Binding> _bindings = Collections.emptyList();
    private SubscriptionQueue _subscriptionQueue;

    public Reliability getReliability()
    {
        return reliability;
    }

    public void setReliability(Reliability reliability)
    {
        this.reliability = reliability;
    }

    public boolean isDurable()
    {
        return _isDurable;
    }

    public void setDurable(boolean durable)
    {
        _isDurable = durable;
    }
     
    public String getFilter()
    {
        return _filter;
    }

    public void setFilter(String filter)
    {
        this._filter = filter;
    }

    public FilterType getFilterType()
    {
        return _filterType;
    }

    public void setFilterType(FilterType type)
    {
        _filterType = type;
    }

    public boolean isNoLocal()
    {
        return _isNoLocal;
    }

    public void setNoLocal(boolean noLocal)
    {
        _isNoLocal = noLocal;
    }

    public int getConsumerCapacity()
    {
        return _consumerCapacity;
    }

    public void setConsumerCapacity(int capacity)
    {
        _consumerCapacity = capacity;
    }
    
    public int getProducerCapacity()
    {
        return _producerCapacity;
    }

    public void setProducerCapacity(int capacity)
    {
        _producerCapacity = capacity;
    }
    
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }
    
    public Subscription getSubscription()
    {
        return this.subscription;
    }    
 
    public void setSubscription(Subscription subscription)
    {
        this.subscription = subscription;
    }   

    public List<Binding> getBindings()
    {
        return _bindings;
    }

    public void setBindings(List<Binding> bindings)
    {
        _bindings = bindings;
    }

    public SubscriptionQueue getSubscriptionQueue()
    {
        return _subscriptionQueue;
    }

    public void setSubscriptionQueue(SubscriptionQueue subscriptionQueue)
    {
        this._subscriptionQueue = subscriptionQueue;
    }

    public static class SubscriptionQueue
    {
        private Map<String,Object> _declareArgs = new HashMap<String,Object>();
        private boolean _isAutoDelete = true;
        private boolean _isExclusive = true;
        private String _alternateExchange;

        public Map<String,Object> getDeclareArgs()
        {
            return _declareArgs;
        }

        public void setDeclareArgs(Map<String,Object> options)
        {
            _declareArgs = options;
        }

        public boolean isAutoDelete()
        {
            return _isAutoDelete;
        }

        public void setAutoDelete(boolean autoDelete)
        {
            _isAutoDelete = autoDelete;
        }

        public boolean isExclusive()
        {
            return _isExclusive;
        }

        public void setExclusive(boolean exclusive)
        {
            _isExclusive = exclusive;
        }

        public String getAlternateExchange()
        {
            return _alternateExchange;
        }

        public void setAlternateExchange(String altExchange)
        {
            _alternateExchange = altExchange;
        }
    }
    
    public static class Subscription
    {
        private Map<String,Object> args = Collections.emptyMap();
        private boolean exclusive = false;
        
        public Map<String, Object> getArgs()
        {
            return args;
        }
        
        public void setArgs(Map<String, Object> args)
        {
            this.args = args;
        }
        
        public boolean isExclusive()
        {
            return exclusive;
        }
        
        public void setExclusive(boolean exclusive)
        {
            this.exclusive = exclusive;
        }
    }
}
