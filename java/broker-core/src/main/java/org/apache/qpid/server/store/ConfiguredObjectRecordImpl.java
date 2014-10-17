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
package org.apache.qpid.server.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ConfiguredObjectRecordImpl implements ConfiguredObjectRecord
{
    private UUID _id;
    private String _type;
    private final Map<String,Object> _attributes;
    private final Map<String,UUID> _parents;


    public ConfiguredObjectRecordImpl(ConfiguredObjectRecord record)
    {
        this(record.getId(), record.getType(), record.getAttributes(), record.getParents());
    }

    public ConfiguredObjectRecordImpl(UUID id, String type, Map<String, Object> attributes)
    {
        this(id,type,attributes,Collections.<String,UUID>emptyMap());
    }

    public ConfiguredObjectRecordImpl(UUID id, String type, Map<String, Object> attributes, Map<String,UUID> parents)
    {
        super();
        _id = id;
        _type = type;
        _attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        _parents = Collections.unmodifiableMap(new LinkedHashMap<>(parents));
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public String getType()
   {
        return _type;
    }

    @Override
    public Map<String,Object> getAttributes()
    {
        return _attributes;
    }

    @Override
    public Map<String, UUID> getParents()
    {
        return _parents;
    }

    @Override
    public String toString()
    {
        return "ConfiguredObjectRecord [id=" + _id + ", type=" + _type + ", attributes=" + _attributes + ", parents=" + _parents + "]";
    }

    @Override
    public boolean equals(Object o)
    {
        if(this == o)
        {
            return true;
        }
        if(o == null || getClass() != o.getClass())
        {
            return false;
        }

        ConfiguredObjectRecordImpl that = (ConfiguredObjectRecordImpl) o;

        return _type.equals(that._type) && _id.equals(that._id) && _attributes.equals(that._attributes);
    }

    @Override
    public int hashCode()
    {
        int result = _id.hashCode();
        result = 31 * result + _type.hashCode();
        result = 31 * result + _attributes.hashCode();
        return result;
    }
}
