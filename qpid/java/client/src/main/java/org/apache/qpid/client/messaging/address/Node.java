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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.Binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class Node
{ 
    private int _nodeType = AMQDestination.UNKNOWN_TYPE;
    private boolean _isDurable;
    private boolean _isAutoDelete;
    private String _alternateExchange;
    private List<Binding> _bindings = new ArrayList<Binding>();
    private Map<String,Object> _declareArgs = Collections.emptyMap();

    protected Node(int nodeType)
    {
        _nodeType = nodeType;
    }

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
       private boolean _isExclusive;
       private QpidQueueOptions _queueOptions = new QpidQueueOptions();
       
       public QueueNode()
       {
           super(AMQDestination.QUEUE_TYPE);
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
       private QpidExchangeOptions _exchangeOptions = new QpidExchangeOptions();
       private String _exchangeType;
       
       public ExchangeNode()
       {
           super(AMQDestination.TOPIC_TYPE);
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
        public UnknownNodeType()
        {
            super(AMQDestination.UNKNOWN_TYPE);
        }
    }
}
