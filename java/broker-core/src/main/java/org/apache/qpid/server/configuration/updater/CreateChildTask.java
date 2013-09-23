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

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.qpid.server.model.ConfiguredObject;

public final class CreateChildTask implements Callable<ConfiguredObject>
{
    private ConfiguredObject _object;
    private Class<? extends ConfiguredObject> _childClass;
    private Map<String, Object> _attributes;
    private ConfiguredObject[] _otherParents;

    public CreateChildTask(ConfiguredObject object, Class<? extends ConfiguredObject> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        _object = object;
        _childClass = childClass;
        _attributes = attributes;
        _otherParents = otherParents;
    }

    public ConfiguredObject getObject()
    {
        return _object;
    }

    public Class<? extends ConfiguredObject> getChildClass()
    {
        return _childClass;
    }

    public Map<String, Object> getAttributes()
    {
        return _attributes;
    }

    public ConfiguredObject[] getOtherParents()
    {
        return _otherParents;
    }

    @Override
    public ConfiguredObject call()
    {
        return _object.createChild(_childClass, _attributes, _otherParents);
    }

    @Override
    public String toString()
    {
        return "CreateChildTask [object=" + _object + ", childClass=" + _childClass + ", attributes=" + _attributes
                + ", otherParents=" + Arrays.toString(_otherParents) + "]";
    }

}
