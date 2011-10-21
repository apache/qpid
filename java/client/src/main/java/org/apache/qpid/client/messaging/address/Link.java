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

import static org.apache.qpid.client.messaging.address.Link.Reliability.UNSPECIFIED;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.client.messaging.address.Node.QueueNode;

public class Link
{ 
    public enum FilterType { SQL92, XQUERY, SUBJECT }
    
    public enum Reliability { UNRELIABLE, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE, UNSPECIFIED }
    
    protected String name;
    protected String _filter;
    protected FilterType _filterType = FilterType.SUBJECT;
    protected boolean _isNoLocal;
    protected boolean _isDurable;
    protected int _consumerCapacity = 0;
    protected int _producerCapacity = 0;
    protected Node node;
    protected Subscription subscription;
    protected Reliability reliability = UNSPECIFIED;
    
    public Reliability getReliability()
    {
        return reliability;
    }

    public void setReliability(Reliability reliability)
    {
        this.reliability = reliability;
    }

    public Node getNode()
    {
        return node;
    }

    public void setNode(Node node)
    {
        this.node = node;
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
    
    public static class Subscription
    {
        private Map<String,Object> args = new HashMap<String,Object>();        
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
