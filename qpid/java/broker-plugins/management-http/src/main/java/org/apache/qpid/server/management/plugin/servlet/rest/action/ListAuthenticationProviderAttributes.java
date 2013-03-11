/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.management.plugin.servlet.rest.action;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.qpid.server.management.plugin.servlet.rest.Action;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class ListAuthenticationProviderAttributes implements Action
{
    private static final String ATTRIBUTES = "attributes";
    private static final String DESCRIPTIONS = "descriptions";
    private Map<String, AuthenticationManagerFactory> _factories;

    public ListAuthenticationProviderAttributes()
    {
        _factories = new TreeMap<String, AuthenticationManagerFactory>();
        Iterable<AuthenticationManagerFactory> factories = new QpidServiceLoader<AuthenticationManagerFactory>()
                .instancesOf(AuthenticationManagerFactory.class);
        for (AuthenticationManagerFactory factory : factories)
        {
            _factories.put(factory.getType(), factory);
        }
    }

    @Override
    public String getName()
    {
        return ListAuthenticationProviderAttributes.class.getSimpleName();
    }

    @Override
    public Object perform(Map<String, Object> request, Broker broker)
    {
        Map<String, Object> attributes = new TreeMap<String, Object>();
        for (String providerType : _factories.keySet())
        {
            AuthenticationManagerFactory factory = _factories.get(providerType);

            Map<String, Object> data = new HashMap<String, Object>();
            data.put(ATTRIBUTES, factory.getAttributeNames());
            Map<String, String> resources = factory.getAttributeDescriptions();
            if (resources != null)
            {
                data.put(DESCRIPTIONS, resources);
            }

            attributes.put(factory.getType(), data);
        }
        return attributes;
    }

}
