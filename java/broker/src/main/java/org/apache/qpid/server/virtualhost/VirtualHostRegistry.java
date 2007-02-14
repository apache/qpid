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
package org.apache.qpid.server.virtualhost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class VirtualHostRegistry
{
    private final Map<String, VirtualHost> _registry = new ConcurrentHashMap<String,VirtualHost>();


    private String _defaultVirtualHostName;

    public synchronized void registerVirtualHost(VirtualHost host) throws Exception
    {
        if(_registry.containsKey(host.getName()))
        {
            throw new Exception("Virtual Host with name " + host.getName() + " already registered.");
        }
        _registry.put(host.getName(),host);
    }

    public VirtualHost getVirtualHost(String name)
    {
        if(name == null || name.trim().length() == 0 )
        {
            name = getDefaultVirtualHostName();
        }

        return _registry.get(name);
    }

    private String getDefaultVirtualHostName()
    {
        return _defaultVirtualHostName;
    }

    public void setDefaultVirtualHostName(String defaultVirtualHostName)
    {
        _defaultVirtualHostName = defaultVirtualHostName;
    }


    public Collection<VirtualHost> getVirtualHosts()
    {
        return new ArrayList<VirtualHost>(_registry.values());
    }
}
