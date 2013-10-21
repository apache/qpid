package org.apache.qpid.server.virtualhost;/*
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.plugin.VirtualHostFactory;

public class VirtualHostFactoryRegistry
{
    private static Map<String, VirtualHostFactory> getFactoryMap()
    {
        Map<String, VirtualHostFactory> virtualHostFactories = new HashMap<String, VirtualHostFactory>();
        QpidServiceLoader<VirtualHostFactory> qpidServiceLoader = new QpidServiceLoader<VirtualHostFactory>();
        Iterable<VirtualHostFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(VirtualHostFactory.class);
        for (VirtualHostFactory virtualHostFactory : factories)
        {
            String type = virtualHostFactory.getType();
            VirtualHostFactory factory = virtualHostFactories.put(type, virtualHostFactory);
            if (factory != null)
            {
                throw new IllegalStateException("VirtualHostFactory with type name '" + type
                        + "' is already registered using class '" + factory.getClass().getName() + "', can not register class '"
                        + virtualHostFactory.getClass().getName() + "'");
            }
        }
        return virtualHostFactories;
    }


    public static Collection<VirtualHostFactory> getFactories()
    {
        return Collections.unmodifiableCollection(getFactoryMap().values());
    }

    public static Collection<String> getVirtualHostTypes()
    {
        return Collections.unmodifiableCollection(getFactoryMap().keySet());
    }

    public static VirtualHostFactory getFactory(String type)
    {
        return getFactoryMap().get(type);
    }
}
