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
package org.apache.qpid.server.configuration.store;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.StoreException;

public class StoreConfigurationChangeListener implements ConfigurationChangeListener
{
    private ConfigurationEntryStore _store;

    public StoreConfigurationChangeListener(ConfigurationEntryStore store)
    {
        super();
        _store = store;
    }

    @Override
    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        if (newState == State.DELETED)
        {
            _store.remove(object.getId());
            object.removeChangeListener(this);
        }
    }

    @Override
    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        // exclude VirtualHost children from storing in broker store
        if (!(object instanceof VirtualHost))
        {
            child.addChangeListener(this);
            ConfigurationEntry parentEntry = toConfigurationEntry(object);
            ConfigurationEntry childEntry = toConfigurationEntry(child);
            _store.save(parentEntry, childEntry);
        }

    }

    @Override
    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        _store.save(toConfigurationEntry(object));
    }

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        _store.save(toConfigurationEntry(object));
    }

    private ConfigurationEntry toConfigurationEntry(ConfiguredObject object)
    {
        Class<? extends ConfiguredObject> objectType = object.getCategoryClass();
        Set<UUID> childrenIds = getChildrenIds(object, objectType);
        ConfigurationEntry entry = new ConfigurationEntry(object.getId(), objectType.getSimpleName(),
                object.getActualAttributes(), childrenIds, _store);
        return entry;
    }

    private Set<UUID> getChildrenIds(ConfiguredObject object, Class<? extends ConfiguredObject> objectType)
    {
        // Virtual Host children's IDs should not be stored in broker store
        if (object instanceof VirtualHost)
        {
            return Collections.emptySet();
        }
        Set<UUID> childrenIds = new TreeSet<UUID>();
        Collection<Class<? extends ConfiguredObject>> childClasses = Model.getInstance().getChildTypes(objectType);
        if (childClasses != null)
        {
            for (Class<? extends ConfiguredObject> childClass : childClasses)
            {
                Collection<? extends ConfiguredObject> children = object.getChildren(childClass);
                if (children != null)
                {
                    for (ConfiguredObject childObject : children)
                    {
                        childrenIds.add(childObject.getId());
                    }
                }
            }
        }
        return childrenIds;
    }

    @Override
    public String toString()
    {
        return "StoreConfigurationChangeListener [store=" + _store + "]";
    }
}
