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

public class ConfiguredObjectRecord
{
    private UUID _id;
    private String _type;
    private Map<String,Object> _attributes;

    public ConfiguredObjectRecord(UUID id, String type, Map<String,Object> attributes)
    {
        super();
        _id = id;
        _type = type;
        _attributes = Collections.unmodifiableMap(new LinkedHashMap<String,Object>(attributes));
    }

    public UUID getId()
    {
        return _id;
    }

    public String getType()
   {
        return _type;
    }

    public Map<String,Object> getAttributes()
    {
        return _attributes;
    }

    @Override
    public String toString()
    {
        return "ConfiguredObjectRecord [id=" + _id + ", type=" + _type + ", attributes=" + _attributes + "]";
    }

}
