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
package org.apache.qpid.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.model.adapter.VirtualHostAdapter;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public interface VirtualHostFactory extends Pluggable
{
    String getType();

    VirtualHost createVirtualHost(VirtualHostRegistry virtualHostRegistry,
                                  StatisticsGatherer brokerStatisticsGatherer,
                                  SecurityManager parentSecurityManager,
                                  VirtualHostConfiguration hostConfig,
                                  org.apache.qpid.server.model.VirtualHost virtualHost);

    void validateAttributes(Map<String, Object> attributes);

    Map<String, Object> createVirtualHostConfiguration(VirtualHostAdapter virtualHostAdapter);

    Map<String,Object> convertVirtualHostConfiguration(Configuration configuration);

    static final class TYPES
    {
        private TYPES()
        {
        }

        public static Collection<String> get()
        {
            QpidServiceLoader<VirtualHostFactory> qpidServiceLoader = new QpidServiceLoader<VirtualHostFactory>();
            Iterable<VirtualHostFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(VirtualHostFactory.class);
            List<String> names = new ArrayList<String>();
            for(VirtualHostFactory factory : factories)
            {
                names.add(factory.getType());
            }
            return Collections.unmodifiableCollection(names);
        }
    }


    static final class FACTORIES
    {
        private FACTORIES()
        {
        }

        public static VirtualHostFactory get(String type)
        {
            QpidServiceLoader<VirtualHostFactory> qpidServiceLoader = new QpidServiceLoader<VirtualHostFactory>();
            Iterable<VirtualHostFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(VirtualHostFactory.class);
            for(VirtualHostFactory factory : factories)
            {
                if(factory.getType().equals(type))
                {
                    return factory;
                }
            }
            return null;
        }
    }
}
