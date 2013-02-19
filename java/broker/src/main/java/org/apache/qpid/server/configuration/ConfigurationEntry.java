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
package org.apache.qpid.server.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ConfigurationEntry
{
    public static final String ATTRIBUTE_NAME = "name";

    private final UUID _id;
    private final String _type;
    private final Map<String, Object> _attributes;
    private final Set<UUID> _childrenIds;
    private final ConfigurationEntryStore _store;

    public ConfigurationEntry(UUID id, String type, Map<String, Object> attributes, Set<UUID> childrenIds,
            ConfigurationEntryStore store)
    {
        super();
        _id = id;
        _type = type;
        _attributes = attributes;
        _childrenIds = childrenIds;
        _store = store;
    }

    public UUID getId()
    {
        return _id;
    }

    public String getType()
    {
        return _type;
    }

    public Map<String, Object> getAttributes()
    {
        return _attributes;
    }

    public Set<UUID> getChildrenIds()
    {
        return _childrenIds;
    }

    public ConfigurationEntryStore getStore()
    {
        return _store;
    }

    /**
     * Returns this entry's children. The collection should not be modified.
     */
    public Map<String, Collection<ConfigurationEntry>> getChildren()
    {
        Map<String, Collection<ConfigurationEntry>> children = null;
        if (_childrenIds == null)
        {
            children = Collections.emptyMap();
        }
        else
        {
            children = new HashMap<String, Collection<ConfigurationEntry>>();
            for (UUID childId : _childrenIds)
            {
                ConfigurationEntry entry = _store.getEntry(childId);
                String type = entry.getType();
                Collection<ConfigurationEntry> childrenOfType = children.get(type);
                if (childrenOfType == null)
                {
                    childrenOfType = new ArrayList<ConfigurationEntry>();
                    children.put(type, childrenOfType);
                }
                childrenOfType.add(entry);
            }
        }
        return Collections.unmodifiableMap(children);
    }

    public boolean hasChild(UUID id)
    {
        return _childrenIds.contains(id);
    }

    @Override
    public int hashCode()
    {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }

        ConfigurationEntry other = (ConfigurationEntry) obj;
        if (_id == null)
        {
            if (other._id != null)
            {
                return false;
            }
        }
        else if (!_id.equals(other._id))
        {
            return false;
        }

        if (_type == null)
        {
            if (other._type != null)
            {
                return false;
            }
        }
        else if (!_type.equals(other._type))
        {
            return false;
        }

        if (_store == null)
        {
            if (other._store != null)
            {
                return false;
            }
        }
        else if (!_store.equals(other._store))
        {
            return false;
        }

        if (_childrenIds == null)
        {
            if (other._childrenIds != null)
            {
                return false;
            }
        }
        else if (!_childrenIds.equals(other._childrenIds))
        {
            return false;
        }

        if (_attributes == null)
        {
            if (other._attributes != null)
            {
                return false;
            }
        }
        else if (!_attributes.equals(other._attributes))
        {
            return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return "ConfigurationEntry [_id=" + _id + ", _type=" + _type + ", _attributes=" + _attributes + ", _childrenIds="
                + _childrenIds + "]";
    }

    public Object setAttribute(String name, Object value)
    {
        return _attributes.put(name, value);
    }

    public ConfigurationEntry clone()
    {
        return new ConfigurationEntry(_id, _type, new HashMap<String, Object>(_attributes), new HashSet<UUID>(_childrenIds), _store);
    }
}
