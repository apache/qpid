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
 *
 */
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;

public class ConfiguredObjectToMapConverter
{
    /** Name of the key used for the statistics map */
    public static final String STATISTICS_MAP_KEY = "statistics";

    private Model _model = Model.getInstance();

    public Map<String, Object> convertObjectToMap(final ConfiguredObject<?> confObject,
                                                  Class<? extends  ConfiguredObject> clazz,
                                                  int depth)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();

        incorporateAttributesIntoMap(confObject, object);
        incorporateStatisticsIntoMap(confObject, object);

        if(depth > 0)
        {
            incorporateChildrenIntoMap(confObject, clazz, depth, object);
        }
        return object;
    }

    /**
     * Used for unit test only.
     */
    void setModel(Model model)
    {
        _model = model;
    }

    private void incorporateAttributesIntoMap(
            final ConfiguredObject<?> confObject, Map<String, Object> object)
    {
        for(String name : confObject.getAttributeNames())
        {
            Object value = confObject.getAttribute(name);
            if(value instanceof ConfiguredObject)
            {
                object.put(name, ((ConfiguredObject) value).getName());
            }
            else if(value instanceof Collection)
            {
                List<Object> converted = new ArrayList();
                for(Object member : (Collection)value)
                {
                    if(member instanceof ConfiguredObject)
                    {
                        converted.add(((ConfiguredObject)member).getName());
                    }
                    else
                    {
                        converted.add(member);
                    }
                }
                object.put(name, converted);
            }
            else if(value != null)
            {
                object.put(name, value);
            }
        }
    }

    private void incorporateStatisticsIntoMap(
            final ConfiguredObject confObject, Map<String, Object> object)
    {

        Map<String, Object> statMap = confObject.getStatistics();

        if(!statMap.isEmpty())
        {
            object.put(STATISTICS_MAP_KEY, statMap);
        }

    }

    private void incorporateChildrenIntoMap(
            final ConfiguredObject confObject,
            Class<? extends ConfiguredObject> clazz, int depth,
            Map<String, Object> object)
    {
        for(Class<? extends ConfiguredObject> childClass : _model.getChildTypes(clazz))
        {
            Collection<? extends ConfiguredObject> children = confObject.getChildren(childClass);
            if(children != null)
            {
                List<Map<String, Object>> childObjects = new ArrayList<Map<String, Object>>();

                for(ConfiguredObject child : children)
                {
                    childObjects.add(convertObjectToMap(child, childClass, depth-1));
                }

                if(!childObjects.isEmpty())
                {
                    object.put(childClass.getSimpleName().toLowerCase()+"s",childObjects);
                }
            }
        }
    }



}
