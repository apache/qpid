package org.apache.qpid.server.configuration.startup;/*
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;

public abstract class StoreUpgrader
{

    private static Map<String, StoreUpgrader> _upgraders = new HashMap<String, StoreUpgrader>();

    // Note: don't use externally defined constants in upgraders in case they change, the values here MUST stay the same
    // no matter what changes are made to the code in the future

    private final static StoreUpgrader UPGRADE_1_0 = new StoreUpgrader("1.0")
    {
        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();
            Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
            Collection<ConfigurationEntry> vhosts = children.get("VirtualHost");
            Collection<ConfigurationEntry> changed =  new HashSet<ConfigurationEntry>();
            for(ConfigurationEntry vhost : vhosts)
            {
                Map<String, Object> attributes = vhost.getAttributes();
                if(attributes.containsKey("storeType"))
                {
                    attributes = new HashMap<String, Object>(attributes);
                    attributes.put("type", "STANDARD");

                    changed.add(new ConfigurationEntry(vhost.getId(),vhost.getType(),attributes,vhost.getChildrenIds(),store));

                }

            }
            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.1");
            changed.add(new ConfigurationEntry(root.getId(),root.getType(),attributes,root.getChildrenIds(),store));

            store.save(changed.toArray(new ConfigurationEntry[changed.size()]));

        }
    };

    private final static StoreUpgrader UPGRADE_1_1 = new StoreUpgrader("1.1")
    {
        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();

            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.2");
            ConfigurationEntry newRoot = new ConfigurationEntry(root.getId(),root.getType(),attributes,root.getChildrenIds(),store);

            store.save(newRoot);

        }
    };

    private StoreUpgrader(String version)
    {
        _upgraders.put(version, this);
    }

    public static void upgrade(ConfigurationEntryStore store)
    {
        StoreUpgrader upgrader = null;
        while ((upgrader = _upgraders.get(store.getRootEntry().getAttributes().get(Broker.MODEL_VERSION).toString())) != null)
        {
            upgrader.doUpgrade(store);
        }
    }

    protected abstract void doUpgrade(ConfigurationEntryStore store);


}
