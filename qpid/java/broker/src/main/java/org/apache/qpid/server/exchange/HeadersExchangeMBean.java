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

import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.server.binding.Binding;

import javax.management.JMException;
import javax.management.openmbean.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
     * HeadersExchangeMBean class implements the management interface for the
 * Header Exchanges.
 */
@MBeanDescription("Management Bean for Headers Exchange")
final class HeadersExchangeMBean extends AbstractExchangeMBean<HeadersExchange>
{

    @MBeanConstructor("Creates an MBean for AMQ Headers exchange")
    public HeadersExchangeMBean(final HeadersExchange headersExchange) throws JMException
    {
        super(headersExchange);
        init();
    }

    /**
     * initialises the OpenType objects.
     */
    protected void init() throws OpenDataException
    {

        _bindingItemTypes = new OpenType[3];
        _bindingItemTypes[0] = SimpleType.INTEGER;
        _bindingItemTypes[1] = SimpleType.STRING;
        _bindingItemTypes[2] = new ArrayType(1, SimpleType.STRING);
        _bindingDataType = new CompositeType("Exchange Binding", "Queue name and header bindings",
                HEADERS_COMPOSITE_ITEM_NAMES.toArray(new String[HEADERS_COMPOSITE_ITEM_NAMES.size()]), 
                HEADERS_COMPOSITE_ITEM_DESC.toArray(new String[HEADERS_COMPOSITE_ITEM_DESC.size()]), _bindingItemTypes);
        _bindinglistDataType = new TabularType("Exchange Bindings", "List of exchange bindings for " + getName(),
                                               _bindingDataType, HEADERS_TABULAR_UNIQUE_INDEX.toArray(new String[HEADERS_TABULAR_UNIQUE_INDEX.size()]));
    }

    public TabularData bindings() throws OpenDataException
    {
        TabularDataSupport bindingList = new TabularDataSupport(_bindinglistDataType);
        int count = 1;
        for (Binding binding : getExchange().getBindings())
        {

            String queueName = binding.getQueue().getNameShortString().toString();


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
            CompositeData bindingData = new CompositeDataSupport(_bindingDataType, 
                    HEADERS_COMPOSITE_ITEM_NAMES.toArray(new String[HEADERS_COMPOSITE_ITEM_NAMES.size()]), bindingItemValues);
            bindingList.put(bindingData);
        }

        return bindingList;
    }


} // End of MBean class
