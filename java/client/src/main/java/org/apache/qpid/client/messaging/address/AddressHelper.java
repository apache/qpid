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
import java.util.List;
import java.util.Map;

import org.apache.qpid.client.AMQDestination.Binding;
import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.MapAccessor;
import org.apache.qpid.messaging.Address;

/**
 * Utility class for extracting information
 * from the address class  
 */
public class AddressHelper
{
    public static final String NODE_PROPS = "node-properties";
    public static final String X_PROPS = "x-properties";
    public static final String CREATE = "create";
    public static final String ASSERT = "assert";
    public static final String DELETE = "delete";
    public static final String FILTER = "filter";
    public static final String NO_LOCAL = "no-local";
    public static final String DURABLE = "durable";
    public static final String EXCLUSIVE = "exclusive";
    public static final String AUTO_DELETE = "auto-delete";
    public static final String TYPE = "type";
    public static final String ALT_EXCHANGE = "alt-exchange";
    public static final String BINDINGS = "bindings";
    public static final String BROWSE_ONLY = "browse";
    
    private Address address;
    private Accessor addressProps;
    private Accessor nodeProps;
    private Accessor xProps;
    
    public AddressHelper(Address address)
    {
        this.address = address;
        addressProps = new MapAccessor(address.getOptions());
        Map node_props = address.getOptions() == null || 
                         address.getOptions().get(NODE_PROPS) == null ?
                               null : (Map)address.getOptions().get(NODE_PROPS);
        nodeProps = new MapAccessor(node_props);
        xProps = new MapAccessor(node_props == null || node_props.get(X_PROPS) == null?
                                 null: (Map)node_props.get(X_PROPS));
    }
    
    public String getCreate()
    {
        return addressProps.getString(CREATE);
    }
    
    public String getAssert()
    {
        return addressProps.getString(ASSERT);
    }
    
    public String getDelete()
    {
        return addressProps.getString(DELETE);
    }

    public String getFilter()
    {
        return addressProps.getString(FILTER);
    }
    
    public boolean isNoLocal()
    {
        Boolean b = nodeProps.getBoolean(NO_LOCAL);
        return b == null ? false : b ;
    }
    
    public boolean isDurable()
    {
        Boolean b = nodeProps.getBoolean(DURABLE);
        return b == null ? false : b ;
    }
    
    public boolean isExclusive()
    {
        Boolean b = xProps.getBoolean(EXCLUSIVE);
        return b == null ? false : b ;
    }
    
    public boolean isAutoDelete()
    {
        Boolean b = xProps.getBoolean(AUTO_DELETE);
        return b == null ? false : b ;
    }
    
    public boolean isBrowseOnly()
    {
        Boolean b = xProps.getBoolean(BROWSE_ONLY);
        return b == null ? false : b ;
    }
    
    public String getNodeType()
    {
        return nodeProps.getString(TYPE);
    }
    
    public String getAltExchange()
    {
        return xProps.getString(ALT_EXCHANGE);
    }
    
    public QpidQueueOptions getQpidQueueOptions()
    {
        QpidQueueOptions options = new QpidQueueOptions();
        if (xProps.getInt(QpidQueueOptions.QPID_MAX_COUNT) != null)
        {
            options.setMaxCount(xProps.getInt(QpidQueueOptions.QPID_MAX_COUNT));
        }
        
        if (xProps.getInt(QpidQueueOptions.QPID_MAX_SIZE) != null)
        {
            options.setMaxSize(xProps.getInt(QpidQueueOptions.QPID_MAX_SIZE));
        }        
        
        if (xProps.getInt(QpidQueueOptions.QPID_POLICY_TYPE) != null)
        {
            options.setPolicyType(xProps.getString(QpidQueueOptions.QPID_POLICY_TYPE));
        }
        
        if (xProps.getInt(QpidQueueOptions.QPID_PERSIST_LAST_NODE) != null)
        {
            options.setPersistLastNode();
        }  
        
        if (xProps.getString(QpidQueueOptions.QPID_LAST_VALUE_QUEUE) != null)
        {
            options.setOrderingPolicy(xProps.getString(QpidQueueOptions.QPID_LAST_VALUE_QUEUE));
            options.setLvqKey(xProps.getString(QpidQueueOptions.QPID_LVQ_KEY));
        }
        else if (xProps.getString(QpidQueueOptions.QPID_LAST_VALUE_QUEUE_NO_BROWSE) != null)
        {
            options.setOrderingPolicy(xProps.getString(QpidQueueOptions.QPID_LAST_VALUE_QUEUE_NO_BROWSE));
            options.setLvqKey(xProps.getString(QpidQueueOptions.QPID_LVQ_KEY));
        }
        
        if (xProps.getString(QpidQueueOptions.QPID_QUEUE_EVENT_GENERATION) != null)
        {
            options.setQueueEvents(xProps.getString(QpidQueueOptions.QPID_QUEUE_EVENT_GENERATION));
        }
        
        return options;
    }
    
    public QpidExchangeOptions getQpidExchangeOptions()
    {
        QpidExchangeOptions options = new QpidExchangeOptions();
        if (xProps.getInt(QpidExchangeOptions.QPID_EXCLUSIVE_BINDING) != null)
        {
            options.setExclusiveBinding();
        }
        
        if (xProps.getInt(QpidExchangeOptions.QPID_INITIAL_VALUE_EXCHANGE) != null)
        {
            options.setInitialValueExchange();
        }        
        
        if (xProps.getInt(QpidExchangeOptions.QPID_MSG_SEQUENCE) != null)
        {
            options.setMessageSequencing();
        }
        return options;
    }
    
    public List<Binding> getBindings()
    {
        List<Binding> bindings = new ArrayList<Binding>();
        if (address.getOptions() != null &&
            address.getOptions().get(NODE_PROPS) != null &&
            ((Map)address.getOptions().get(NODE_PROPS)).get(X_PROPS) != null)
        {
            Map node_props = (Map)address.getOptions().get(NODE_PROPS);
            List<String> bindingList = 
                (List<String>)((Map)node_props.get(X_PROPS)).get(BINDINGS);
            if (bindingList != null)
            {
                for (String bindingStr: bindingList)
                {
                    Address addr = Address.parse(bindingStr);
                    Binding binding = new Binding(addr.getName(),
                                                  addr.getSubject(),
                                                  addr.getOptions());
                    bindings.add(binding);
                }
            }
        }
        return bindings;
    }
}
