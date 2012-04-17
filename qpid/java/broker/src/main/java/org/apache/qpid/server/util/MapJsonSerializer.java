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
package org.apache.qpid.server.util;

import java.io.StringWriter;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

public class MapJsonSerializer
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>()
    {
    };

    private ObjectMapper _mapper;

    public MapJsonSerializer()
    {
        _mapper = new ObjectMapper();
    }

    public String serialize(Map<String, Object> attributeMap)
    {
        StringWriter stringWriter = new StringWriter();
        try
        {
            _mapper.writeValue(stringWriter, attributeMap);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failure to serialize map:" + attributeMap, e);
        }
        return stringWriter.toString();
    }

    public Map<String, Object> deserialize(String json)
    {
        Map<String, Object> attributesMap = null;
        try
        {
            attributesMap = _mapper.readValue(json, MAP_TYPE_REFERENCE);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failure to deserialize json:" + json, e);
        }
        return attributesMap;
    }
}
