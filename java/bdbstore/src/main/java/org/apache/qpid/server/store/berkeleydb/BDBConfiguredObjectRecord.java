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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.store.ConfiguredObjectRecord;

public class BDBConfiguredObjectRecord implements ConfiguredObjectRecord
{
    private final UUID _id;
    private final String _type;
    private final Map<String,Object> _attributes;
    private Map<String, UUID> _parents = new HashMap<>();

    public BDBConfiguredObjectRecord(final UUID id, final String type, final Map<String, Object> attributes)
    {
        _id = id;
        _type = type;
        _attributes = Collections.unmodifiableMap(attributes);
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

    void addParent(String parentType, ConfiguredObjectRecord parent)
    {
        _parents.put(parentType, parent.getId());
    }

    @Override
    public Map<String, UUID> getParents()
    {
        return Collections.unmodifiableMap(_parents);
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final BDBConfiguredObjectRecord that = (BDBConfiguredObjectRecord) o;

        if (_attributes != null ? !_attributes.equals(that._attributes) : that._attributes != null)
        {
            return false;
        }
        if (_id != null ? !_id.equals(that._id) : that._id != null)
        {
            return false;
        }
        if (_type != null ? !_type.equals(that._type) : that._type != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _id != null ? _id.hashCode() : 0;
        result = 31 * result + (_type != null ? _type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "BDBConfiguredObjectRecord [id=" + _id + ", type=" + _type + ", name=" + (_attributes == null ? null : _attributes.get("name")) + ", parents=" + _parents + "]";
    }

}
