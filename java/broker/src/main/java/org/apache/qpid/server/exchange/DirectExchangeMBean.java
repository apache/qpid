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
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
     * MBean class implementing the management interfaces.
 */
@MBeanDescription("Management Bean for Direct Exchange")
final class DirectExchangeMBean extends AbstractExchangeMBean<DirectExchange>
{
    @MBeanConstructor("Creates an MBean for AMQ direct exchange")
    public DirectExchangeMBean(final DirectExchange exchange)  throws JMException
    {
        super(exchange);

        init();
    }

    public TabularData bindings() throws OpenDataException
    {
        TabularDataSupport bindingList = new TabularDataSupport(_bindinglistDataType);

        Map<String, List<String>> bindingMap = new HashMap<String, List<String>>();

        for (Binding binding : getExchange().getBindings())
        {
            String key = binding.getBindingKey();
            List<String> queueList = bindingMap.get(key);
            if(queueList == null)
            {
                queueList = new ArrayList<String>();
                bindingMap.put(key, queueList);
            }
            queueList.add(binding.getQueue().getNameShortString().toString());

        }

        for(Map.Entry<String, List<String>> entry : bindingMap.entrySet())
        {
            Object[] bindingItemValues = {entry.getKey(), entry.getValue().toArray(new String[0])};
            CompositeData bindingData = new CompositeDataSupport(_bindingDataType,
                    COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]),
                    bindingItemValues);
            bindingList.put(bindingData);
        }

        return bindingList;
    }



}// End of MBean class
