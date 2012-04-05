/*
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
 */
package org.apache.qpid.disttest.client.property;

import org.apache.commons.lang.StringUtils;
import org.apache.qpid.disttest.DistributedTestException;

/**
 * Creates property value instances using given alias (type) value.
 */
public class PropertyValueFactory
{
    public PropertyValue createPropertyValue(String type)
    {
        try
        {
            return (PropertyValue)getPropertyValueClass(type).newInstance();
        }
        catch(Exception e)
        {
            throw new DistributedTestException("Unable to create a generator for a type:" + type, e);
        }
    }

    public Class<?> getPropertyValueClass(String type) throws ClassNotFoundException
    {
        String className = "org.apache.qpid.disttest.client.property." + StringUtils.capitalize(type) + "PropertyValue";
        return Class.forName(className);
    }
}
