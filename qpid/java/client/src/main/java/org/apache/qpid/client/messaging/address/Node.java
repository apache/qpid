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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.naming.OperationNotSupportedException;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.Binding;

public abstract class Node
{ 
    protected int _nodeType = AMQDestination.UNKNOWN_TYPE;
    protected boolean _isDurable;
    protected boolean _isAutoDelete;
    protected String _alternateExchange;
    protected List<Binding> _bindings = new ArrayList<Binding>();
    protected Map<String,Object> _declareArgs = Collections.emptyMap();
    
    public int getType()
    {
        return _nodeType;
    }
    
    public boolean isDurable()
    {
        return _isDurable;
    }
    
    public void setDurable(boolean durable)
    {
        _isDurable = durable;
    }

    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }
    
    public void setAutoDelete(boolean autoDelete)
    {
        _isAutoDelete = autoDelete;
    }
    
    public String getAlternateExchange()
    {
        return _alternateExchange;
    }
    
    public void setAlternateExchange(String altExchange)
    {
        _alternateExchange = altExchange;
    }
    
    public List<Binding> getBindings()
    {
        return _bindings;
    }
    
    public void setBindings(List<Binding> bindings)
    {
        _bindings = bindings;
    }
    
    public void addBinding(Binding binding) {
        this._bindings.add(binding);
    }
    
    public Map<String,Object> getDeclareArgs()
    {
        return _declareArgs;
    }
    
    public void setDeclareArgs(Map<String,Object> options)
    {
        _declareArgs = options;
    }   
    
    public static class QueueNode extends Node 
    {
       protected boolean _isExclusive;      
       protected QpidQueueOptions _queueOptions = new QpidQueueOptions();
       
       public QueueNode()
       {
           _nodeType = AMQDestination.QUEUE_TYPE;
       }
       
       public boolean isExclusive()
       {
           return _isExclusive;
       }
       
       public void setExclusive(boolean exclusive)
       {
           _isExclusive = exclusive;
       }  
    }
    
    public static class ExchangeNode extends Node 
    {
       protected QpidExchangeOptions _exchangeOptions = new QpidExchangeOptions();
       protected String _exchangeType;
       
       public ExchangeNode()
       {
           _nodeType = AMQDestination.TOPIC_TYPE;
       }
       
       public String getExchangeType()
       {
           return _exchangeType;
       }
       
       public void setExchangeType(String exchangeType)
       {
           _exchangeType = exchangeType;
       }
    
    }
    
    public static class UnknownNodeType extends Node 
    {
    }
}
