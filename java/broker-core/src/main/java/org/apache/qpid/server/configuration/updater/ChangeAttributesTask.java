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
package org.apache.qpid.server.configuration.updater;

import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.qpid.server.model.ConfiguredObject;

public class ChangeAttributesTask implements TaskExecutor.Task<Void>
{
    private final Map<String, Object> _attributes;
    private final ConfiguredObject _object;

    public ChangeAttributesTask(ConfiguredObject target, Map<String, Object> attributes)
    {
        super();
        _object = target;
        _attributes = attributes;
    }

    @Override
    public Void call()
    {
        _object.setAttributes(_attributes);
        return null;
    }

    public Map<String, Object> getAttributes()
    {
        return _attributes;
    }

    public ConfiguredObject getObject()
    {
        return _object;
    }

    @Override
    public String toString()
    {
        return "ChangeAttributesTask [object=" + _object + ", attributes=" + _attributes + "]";
    }
}
