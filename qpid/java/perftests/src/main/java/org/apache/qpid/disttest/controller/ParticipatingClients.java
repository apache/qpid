/*
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
 */
package org.apache.qpid.disttest.controller;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class ParticipatingClients
{
    private final BidiMap _configuredToRegisteredNameMap;

    public ParticipatingClients(ClientRegistry clientRegistry, List<String> configuredClientNamesForTest)
    {
        _configuredToRegisteredNameMap = mapConfiguredToRegisteredClientNames(configuredClientNamesForTest, clientRegistry);
    }

    public String getRegisteredNameFromConfiguredName(String clientConfiguredName)
    {
        String registeredClientName = (String) _configuredToRegisteredNameMap.get(clientConfiguredName);
        if (registeredClientName == null)
        {
            throw new IllegalArgumentException("Unrecognised client configured name " + clientConfiguredName
                    + " Mapping is " + _configuredToRegisteredNameMap);
        }
        return registeredClientName;
    }

    public String getConfiguredNameFromRegisteredName(String registeredClientName)
    {
        String clientConfiguredName = (String) _configuredToRegisteredNameMap.getKey(registeredClientName);
        if (clientConfiguredName == null)
        {
            throw new IllegalArgumentException("Unrecognised client registered name " + registeredClientName
                    + " Mapping is " + _configuredToRegisteredNameMap);
        }

        return clientConfiguredName;
    }

    private BidiMap mapConfiguredToRegisteredClientNames(List<String> configuredClientNamesForTest, ClientRegistry clientRegistry)
    {
        BidiMap configuredToRegisteredNameMap = new DualHashBidiMap();

        TreeSet<String> registeredClients = new TreeSet<String>(clientRegistry.getClients());
        for (String configuredClientName : configuredClientNamesForTest)
        {
            String allocatedClientName = registeredClients.pollFirst();
            if (allocatedClientName == null)
            {
                throw new IllegalArgumentException("Too few clients in registry " + clientRegistry + " configured clients " + configuredClientNamesForTest);
            }
            configuredToRegisteredNameMap.put(configuredClientName, allocatedClientName);
        }

        return configuredToRegisteredNameMap;
    }

    @SuppressWarnings("unchecked")
    public Collection<String> getRegisteredNames()
    {
        return _configuredToRegisteredNameMap.values();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("configuredToRegisteredNameMap", _configuredToRegisteredNameMap).toString();
    }


}
