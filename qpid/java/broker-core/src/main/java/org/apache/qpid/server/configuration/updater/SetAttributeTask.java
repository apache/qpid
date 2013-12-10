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

import java.util.concurrent.Callable;

import org.apache.qpid.server.model.ConfiguredObject;

public final class SetAttributeTask implements Callable<Object>
{
    private ConfiguredObject _object;
    private String _attributeName;
    private Object _expectedValue;
    private Object _desiredValue;

    public SetAttributeTask(ConfiguredObject object, String attributeName, Object expectedValue, Object desiredValue)
    {
        _object = object;
        _attributeName = attributeName;
        _expectedValue = expectedValue;
        _desiredValue = desiredValue;
    }

    public ConfiguredObject getObject()
    {
        return _object;
    }

    public String getAttributeName()
    {
        return _attributeName;
    }

    public Object getExpectedValue()
    {
        return _expectedValue;
    }

    public Object getDesiredValue()
    {
        return _desiredValue;
    }

    @Override
    public Object call()
    {
        return _object.setAttribute(_attributeName, _expectedValue, _desiredValue);
    }

    @Override
    public String toString()
    {
        return "SetAttributeTask [object=" + _object + ", attributeName=" + _attributeName + ", expectedValue=" + _expectedValue
                + ", desiredValue=" + _desiredValue + "]";
    }
}
