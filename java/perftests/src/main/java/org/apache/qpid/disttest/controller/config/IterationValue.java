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
package org.apache.qpid.disttest.controller.config;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.disttest.message.Command;

public class IterationValue
{
    private final Map<String, String> _iterationPropertyValuesWithUnderscores;

    public IterationValue(Map<String, String> iterationMap)
    {
        _iterationPropertyValuesWithUnderscores = iterationMap;
    }

    public IterationValue()
    {
        _iterationPropertyValuesWithUnderscores = Collections.emptyMap();
    }

    public Map<String, String> getIterationPropertyValuesWithUnderscores()
    {
        return _iterationPropertyValuesWithUnderscores;
    }

    public void applyToCommand(Command command)
    {
        try
        {
            Map<String, String> withoutUnderscoresToMatchCommandPropertyNames = getIterationPropertyValuesWithoutUnderscores();
            BeanUtilsBean.getInstance().copyProperties(command, withoutUnderscoresToMatchCommandPropertyNames);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Couldn't copy properties from iteration " + this + " to " + command, e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException("Couldn't copy properties from iteration " + this + " to " + command, e);
        }
    }

    private Map<String, String> getIterationPropertyValuesWithoutUnderscores()
    {
        Map<String, String> iterationPropertyValues = new HashMap<String, String>();
        for (String propertyNameWithUnderscore : _iterationPropertyValuesWithUnderscores.keySet())
        {
            String propertyName = propertyNameWithUnderscore.replaceFirst("_", "");
            String propertyValue = _iterationPropertyValuesWithUnderscores.get(propertyNameWithUnderscore);

            iterationPropertyValues.put(propertyName, propertyValue);
        }
        return iterationPropertyValues;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).append("iterationMap", _iterationPropertyValuesWithUnderscores).toString();
    }

}
