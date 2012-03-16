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

package org.apache.qpid.server.jmx.mbeans;

import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;

import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExchangeMBean extends AMQManagedObject implements ManagedExchange
{

    private static final String[] TABULAR_UNIQUE_INDEX_ARRAY =
            TABULAR_UNIQUE_INDEX.toArray(new String[TABULAR_UNIQUE_INDEX.size()]);

    private static final String[] COMPOSITE_ITEM_NAMES_ARRAY =
            COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]);

    private static final String[] COMPOSITE_ITEM_DESCRIPTIONS_ARRAY =
            COMPOSITE_ITEM_DESCRIPTIONS.toArray(new String[COMPOSITE_ITEM_DESCRIPTIONS.size()]);

    private static final OpenType[] BINDING_ITEM_TYPES;
    private static final CompositeType BINDING_DATA_TYPE;
    private static final OpenType[] HEADERS_BINDING_ITEM_TYPES;


    private static final CompositeType HEADERS_BINDING_DATA_TYPE;

    private static final String[] HEADERS_COMPOSITE_ITEM_NAMES_ARRAY =
            HEADERS_COMPOSITE_ITEM_NAMES.toArray(new String[HEADERS_COMPOSITE_ITEM_NAMES.size()]);

    private static final String[] HEADERS_COMPOSITE_ITEM_DESCS_ARRAY =
            HEADERS_COMPOSITE_ITEM_DESC.toArray(new String[HEADERS_COMPOSITE_ITEM_DESC.size()]);
    private static final  String[] HEADERS_TABULAR_UNIQUE_INDEX_ARRAY =
            HEADERS_TABULAR_UNIQUE_INDEX.toArray(new String[HEADERS_TABULAR_UNIQUE_INDEX.size()]);
    public static final   String   HEADERS_EXCHANGE_TYPE              = "headers";

    static
    {
        try
        {
            BINDING_ITEM_TYPES = new OpenType[] {SimpleType.STRING, new ArrayType(1, SimpleType.STRING)};

            BINDING_DATA_TYPE= new CompositeType("Exchange Binding", "Binding key and Queue names",
                                                 COMPOSITE_ITEM_NAMES_ARRAY,
                                                 COMPOSITE_ITEM_DESCRIPTIONS_ARRAY,
                                                 BINDING_ITEM_TYPES);

            HEADERS_BINDING_ITEM_TYPES = new OpenType[] {SimpleType.INTEGER, 
                                                         SimpleType.STRING, 
                                                         new ArrayType(1, SimpleType.STRING)};
            
            HEADERS_BINDING_DATA_TYPE = new CompositeType("Exchange Binding", "Queue name and header bindings",
                                                          HEADERS_COMPOSITE_ITEM_NAMES_ARRAY,
                                                          HEADERS_COMPOSITE_ITEM_DESCS_ARRAY, 
                                                          HEADERS_BINDING_ITEM_TYPES);

            
        }
        catch(OpenDataException e)
        {
            throw new RuntimeException("Unexpected Error creating ArrayType", e);
        }
    }
    
    
    private final Exchange _exchange;
    private final VirtualHostMBean _vhostMBean;

    protected ExchangeMBean(Exchange exchange, VirtualHostMBean virtualHostMBean)
            throws JMException
    {
        super(ManagedExchange.class, ManagedExchange.TYPE, virtualHostMBean.getRegistry());
        _exchange = exchange;
        _vhostMBean = virtualHostMBean;

        register();
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(getName());
    }

    @Override
    public ManagedObject getParentObject()
    {
        return _vhostMBean;
    }

    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        String objNameString = super.getObjectName().toString();
        objNameString = objNameString + ",ExchangeType=" + getExchangeType();
        return new ObjectName(objNameString);
    }


    public String getName()
    {
        return _exchange.getName();
    }

    public String getExchangeType()
    {
        return _exchange.getExchangeType();
    }

    public Integer getTicketNo()
    {
        return 0;
    }

    public boolean isDurable()
    {
        return _exchange.isDurable();
    }

    public boolean isAutoDelete()
    {
        return _exchange.getLifetimePolicy() == LifetimePolicy.AUTO_DELETE;
    }

    public TabularData bindings() throws IOException, JMException
    {
        if(HEADERS_EXCHANGE_TYPE.equals(_exchange.getExchangeType()))
        {
            return getHeadersBindings(_exchange.getBindings()); 
        }
        else
        {
            return getNonHeadersBindings(_exchange.getBindings());
        }
    }


    private TabularData getHeadersBindings(Collection<Binding> bindings) throws OpenDataException
    {
        TabularType bindinglistDataType =
                new TabularType("Exchange Bindings", "List of exchange bindings for " + getName(),
                        HEADERS_BINDING_DATA_TYPE,
                        HEADERS_TABULAR_UNIQUE_INDEX_ARRAY);
        
        TabularDataSupport bindingList = new TabularDataSupport(bindinglistDataType);
        int count = 1;
        for (Binding binding : bindings)
        {

            String queueName = binding.getParent(Queue.class).getName();


            Map<String,Object> headerMappings = binding.getArguments();

            final List<String> mappingList = new ArrayList<String>();

            if(headerMappings != null)
            {
                for(Map.Entry<String,Object> entry : headerMappings.entrySet())
                {

                    mappingList.add(entry.getKey() + "=" + entry.getValue());
                }
            }


            Object[] bindingItemValues = {count++, queueName, mappingList.toArray(new String[0])};
            CompositeData bindingData = new CompositeDataSupport(HEADERS_BINDING_DATA_TYPE,
                                                                 HEADERS_COMPOSITE_ITEM_NAMES_ARRAY,
                                                                 bindingItemValues);
            bindingList.put(bindingData);
        }

        return bindingList;

    }

    private TabularData getNonHeadersBindings(Collection<Binding> bindings) throws OpenDataException
    {

        TabularType bindinglistDataType = 
                new TabularType("Exchange Bindings", "Exchange Bindings for " + getName(),
                                BINDING_DATA_TYPE,
                                TABULAR_UNIQUE_INDEX_ARRAY);
        
        TabularDataSupport bindingList = new TabularDataSupport(bindinglistDataType);

        Map<String, List<String>> bindingMap = new HashMap<String, List<String>>();

        for (Binding binding : bindings)
        {
            String key = "fanout".equals(_exchange.getExchangeType()) ? "*" : binding.getName();
            List<String> queueList = bindingMap.get(key);
            if(queueList == null)
            {
                queueList = new ArrayList<String>();
                bindingMap.put(key, queueList);
            }
            queueList.add(binding.getParent(Queue.class).getName());

        }

        for(Map.Entry<String, List<String>> entry : bindingMap.entrySet())
        {
            Object[] bindingItemValues = {entry.getKey(), entry.getValue().toArray(new String[0])};
            CompositeData bindingData = new CompositeDataSupport(BINDING_DATA_TYPE,
                                                                 COMPOSITE_ITEM_NAMES_ARRAY,
                                                                 bindingItemValues);
            bindingList.put(bindingData);
        }

        return bindingList;
    }

    public void createNewBinding(String queueName, String binding) throws JMException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();

        if(HEADERS_EXCHANGE_TYPE.equals(_exchange.getExchangeType()))
        {
            final String[] bindings = binding.split(",");
            for (int i = 0; i < bindings.length; i++)
            {
                final String[] keyAndValue = bindings[i].split("=");
                if (keyAndValue == null || keyAndValue.length == 0 || keyAndValue.length > 2 || keyAndValue[0].length() == 0)
                {
                    throw new JMException("Format for headers binding should be \"<attribute1>=<value1>,<attribute2>=<value2>\" ");
                }

                if(keyAndValue.length == 1)
                {
                    //no value was given, only a key. Use an empty value to signal match on key presence alone
                    arguments.put(keyAndValue[0], "");
                }
                else
                {
                    arguments.put(keyAndValue[0], keyAndValue[1]);
                }
            }
        }

        Queue queue = null;
        VirtualHost vhost = _exchange.getParent(VirtualHost.class);
        for(Queue aQueue : vhost.getQueues())
        {
            if(aQueue.getName().equals(queueName))
            {
                queue = aQueue;
                break;
            }
        }
        _exchange.createBinding(binding, queue, arguments, Collections.EMPTY_MAP);
    }

    public void removeBinding(
            @MBeanOperationParameter(name = ManagedQueue.TYPE, description = "Queue name") String queueName,
            @MBeanOperationParameter(name = "Binding", description = "Binding key") String binding)
            throws IOException, JMException
    {
        // TODO
    }
}
