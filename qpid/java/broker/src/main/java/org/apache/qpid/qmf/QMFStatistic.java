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

package org.apache.qpid.qmf;

import org.apache.qpid.transport.codec.Encoder;

import java.util.LinkedHashMap;

public class QMFStatistic
{
    private final LinkedHashMap<String,Object> _map = new LinkedHashMap<String,Object>();
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String UNIT = "unit";
    private static final String DESCRIPTION = "desc";


    public QMFStatistic(String name, QMFType type, String unit, String description)
    {
        _map.put(NAME, name);
        _map.put(TYPE, type.codeValue());
        if(unit != null)
        {
            _map.put(UNIT, unit);
        }
        if(description != null)
        {
            _map.put(DESCRIPTION, description);
        }

    }

    public void encode(Encoder encoder)
    {
        encoder.writeMap(_map);
    }

    public String getName()
    {
        return (String) _map.get(NAME);
    }
}
