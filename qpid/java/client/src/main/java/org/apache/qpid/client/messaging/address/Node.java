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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.Binding;

public class Node
{ 
    private int _nodeType = AMQDestination.UNKNOWN_TYPE;
    private String _name;
    private boolean _isDurable;
    private boolean _isAutoDelete;
    private boolean _isExclusive;
    private String _alternateExchange;
    private String _exchangeType = "topic"; // used when node is an exchange instead of a queue.
    private List<Binding> _bindings = Collections.emptyList();
    private Map<String,Object> _declareArgs = new HashMap<String,Object>();

    protected Node(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    public void setNodeType(int nodeType)
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

    public boolean isExclusive()
    {
        return _isExclusive;
    }

    public void setExclusive(boolean exclusive)
    {
        _isExclusive = exclusive;
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
    
    public Map<String,Object> getDeclareArgs()
    {
        return _declareArgs;
    }
    
    public void setDeclareArgs(Map<String,Object> options)
    {
        _declareArgs = options;
    }

    public void setExchangeType(String type)
    {
        _exchangeType = type;
    }

    public String getExchangeType()
    {
        return _exchangeType;
    }
}
