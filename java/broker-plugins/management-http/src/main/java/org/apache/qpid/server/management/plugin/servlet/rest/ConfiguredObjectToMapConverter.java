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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.Model;

public class ConfiguredObjectToMapConverter
{
    /** Name of the key used for the statistics map */
    public static final String STATISTICS_MAP_KEY = "statistics";

    private static Set<String> CONFIG_EXCLUDED_ATTRIBUTES =
            new HashSet<>(Arrays.asList(ConfiguredObject.ID,
                                        ConfiguredObject.DURABLE,
                                        ConfiguredObject.CREATED_BY,
                                        ConfiguredObject.CREATED_TIME,
                                        ConfiguredObject.LAST_UPDATED_BY,
                                        ConfiguredObject.LAST_UPDATED_TIME));

    public Map<String, Object> convertObjectToMap(final ConfiguredObject<?> confObject,
                                                  Class<? extends ConfiguredObject> clazz,
                                                  int depth,
                                                  final boolean useActualValues,
                                                  final boolean inheritedActuals,
                                                  final boolean includeSystemContext,
                                                  final boolean extractAsConfig,
                                                  final int oversizeThreshold,
                                                  final boolean isSecureTransport
                                                 )
    {
        Map<String, Object> object = new LinkedHashMap<>();

        incorporateAttributesIntoMap(confObject, object, useActualValues, inheritedActuals, includeSystemContext,
                                     extractAsConfig, oversizeThreshold, isSecureTransport);
        if(!extractAsConfig)
        {
            incorporateStatisticsIntoMap(confObject, object);
        }

        if(depth > 0)
        {
            incorporateChildrenIntoMap(confObject, clazz, depth, object, useActualValues, inheritedActuals,
                                       includeSystemContext, extractAsConfig, oversizeThreshold, isSecureTransport);
        }
        return object;
    }


    private void incorporateAttributesIntoMap(
            final ConfiguredObject<?> confObject,
            Map<String, Object> object,
            final boolean useActualValues,
            final boolean inheritedActuals,
            final boolean includeSystemContext,
            final boolean extractAsConfig,
            final int oversizeThreshold,
            final boolean isSecureTransport)
    {
        // if extracting as config add a fake attribute for each secondary parent
        if(extractAsConfig && confObject.getModel().getParentTypes(confObject.getCategoryClass()).size()>1)
        {
            Iterator<Class<? extends ConfiguredObject>> parentClasses =
                    confObject.getModel().getParentTypes(confObject.getCategoryClass()).iterator();

            // ignore the first parent which is supplied by structure
            parentClasses.next();

            while(parentClasses.hasNext())
            {
                Class<? extends ConfiguredObject> parentClass = parentClasses.next();
                ConfiguredObject parent = confObject.getParent(parentClass);
                if(parent != null)
                {
                    String categoryName = parentClass.getSimpleName();
                    object.put(categoryName.substring(0,1).toLowerCase()+categoryName.substring(1), parent.getName());
                }
            }
        }

        for(String name : confObject.getAttributeNames())
        {
            if (!(extractAsConfig && CONFIG_EXCLUDED_ATTRIBUTES.contains(name)))
            {
                Object value =
                        useActualValues ? confObject.getActualAttributes().get(name) : confObject.getAttribute(name);
                if (value instanceof ConfiguredObject)
                {
                    object.put(name, ((ConfiguredObject) value).getName());
                }
                else if (ConfiguredObject.CONTEXT.equals(name))
                {
                    Map<String, Object> contextValues = new HashMap<>();
                    if (useActualValues)
                    {
                        collectContext(contextValues, confObject.getModel(), confObject, inheritedActuals);
                    }
                    else
                    {
                        for (String contextName : confObject.getContextKeys(!includeSystemContext))
                        {
                            contextValues.put(contextName, confObject.getContextValue(String.class, contextName));
                        }
                    }
                    if (!contextValues.isEmpty())
                    {
                        object.put(ConfiguredObject.CONTEXT, contextValues);
                    }
                }
                else if (value instanceof Collection)
                {
                    List<Object> converted = new ArrayList<>();
                    for (Object member : (Collection) value)
                    {
                        if (member instanceof ConfiguredObject)
                        {
                            converted.add(((ConfiguredObject) member).getName());
                        }
                        else
                        {
                            converted.add(member);
                        }
                    }
                    object.put(name, converted);
                }
                else if (value != null)
                {
                    ConfiguredObjectAttribute<?, ?> attribute = confObject.getModel()
                            .getTypeRegistry()
                            .getAttributeTypes(confObject.getClass())
                            .get(name);

                    if (attribute.isSecure() && !(isSecureTransport && extractAsConfig))
                    {
                        // do not expose actual secure attribute value
                        // getAttribute() returns encoded value
                        value =  confObject.getAttribute(name);
                    }

                    if(attribute.isOversized() && !extractAsConfig)
                    {
                        String valueString = String.valueOf(value);
                        if(valueString.length() > oversizeThreshold)
                        {

                            String replacementValue = "".equals(attribute.getOversizedAltText())
                                    ? String.valueOf(value).substring(0, oversizeThreshold - 4) + "..."
                                    : attribute.getOversizedAltText();

                            object.put(name, replacementValue);
                        }
                        else
                        {
                            object.put(name, value);
                        }
                    }
                    else
                    {
                        object.put(name, value);
                    }
                }
                else if (extractAsConfig)
                {
                    ConfiguredObjectAttribute<?, ?> attribute = confObject.getModel()
                            .getTypeRegistry()
                            .getAttributeTypes(confObject.getClass())
                            .get(name);

                    if(attribute.isPersisted() &&  attribute.isDerived())
                    {
                        object.put(name, confObject.getAttribute(name));
                    }
                }
            }
        }
    }

    private void collectContext(Map<String, Object> contextValues, Model model, ConfiguredObject<?> confObject, boolean inheritedContext)
    {
        Object value = confObject.getActualAttributes().get(ConfiguredObject.CONTEXT);
        if (inheritedContext)
        {
            Collection<Class<? extends ConfiguredObject>> parents = model.getParentTypes(confObject.getCategoryClass());
            if(parents != null && !parents.isEmpty())
            {
                ConfiguredObject parent = confObject.getParent(parents.iterator().next());
                if(parent != null)
                {
                    collectContext(contextValues, model, parent, inheritedContext);
                }
            }
        }
        if (value instanceof Map)
        {
            contextValues.putAll((Map<String,String>)value);
        }
    }

    private void incorporateStatisticsIntoMap(
            final ConfiguredObject<?> confObject, Map<String, Object> object)
    {

        Map<String, Object> statMap = new TreeMap<String,Object>(confObject.getStatistics());

        if(!statMap.isEmpty())
        {
            object.put(STATISTICS_MAP_KEY, statMap);
        }

    }

    private void incorporateChildrenIntoMap(
            final ConfiguredObject confObject,
            Class<? extends ConfiguredObject> clazz,
            int depth,
            Map<String, Object> object,
            final boolean useActualValues,
            final boolean inheritedActuals,
            final boolean includeSystemContext,
            final boolean extractAsConfig,
            final int oversizeThreshold,
            final boolean isSecure)
    {
        List<Class<? extends ConfiguredObject>> childTypes = new ArrayList<>(confObject.getModel().getChildTypes(clazz));

        Collections.sort(childTypes, new Comparator<Class<? extends ConfiguredObject>>()
        {
            @Override
            public int compare(final Class<? extends ConfiguredObject> o1, final Class<? extends ConfiguredObject> o2)
            {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        for(Class<? extends ConfiguredObject> childClass : childTypes)
        {
            if(!(extractAsConfig && confObject.getModel().getParentTypes(childClass).iterator().next() != confObject.getCategoryClass()))
            {

                Collection children = confObject.getChildren(childClass);
                if(children != null)
                {
                    List<? extends ConfiguredObject> sortedChildren = new ArrayList<ConfiguredObject>(children);
                    Collections.sort(sortedChildren, new Comparator<ConfiguredObject>()
                    {
                        @Override
                        public int compare(final ConfiguredObject o1, final ConfiguredObject o2)
                        {
                            return o1.getName().compareTo(o2.getName());
                        }
                    });

                    List<Map<String, Object>> childObjects = new ArrayList<>();

                    for (ConfiguredObject child : sortedChildren)
                    {
                        if (!(extractAsConfig && !child.isDurable()))
                        {
                            childObjects.add(convertObjectToMap(child,
                                                                childClass,
                                                                depth - 1,
                                                                useActualValues,
                                                                inheritedActuals,
                                                                includeSystemContext,
                                                                extractAsConfig,
                                                                oversizeThreshold,
                                                                isSecure));
                        }
                    }

                    if (!childObjects.isEmpty())
                    {
                        String childTypeSingular = childClass.getSimpleName().toLowerCase();
                        object.put(childTypeSingular + (childTypeSingular.endsWith("s") ? "es" : "s"), childObjects);
                    }
                }
            }
        }
    }



}
