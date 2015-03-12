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
package org.apache.qpid.server.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.util.ServerScopedRuntimeException;

/**
 * Simple facade over a {@link ServiceLoader} to instantiate all configured implementations of an interface.
 */
public class QpidServiceLoader
{
    private static final Logger _logger = LoggerFactory.getLogger(QpidServiceLoader.class);

    public <C extends Pluggable> Iterable<C> instancesOf(Class<C> clazz)
    {
        return instancesOf(clazz, false);
    }

    /**
     * @throws RuntimeException if at least one implementation is not found.
     */
    public <C extends Pluggable> Iterable<C> atLeastOneInstanceOf(Class<C> clazz)
    {
        return instancesOf(clazz, true);
    }

    public <C extends Pluggable> Map<String,C> getInstancesByType(Class<C> clazz)
    {
        Map<String,C> instances = new HashMap<>();
        for(C instance : instancesOf(clazz))
        {
            instances.put(instance.getType(), instance);
        }
        return Collections.unmodifiableMap(instances);
    }

    private <C extends Pluggable> Iterable<C> instancesOf(Class<C> clazz, boolean atLeastOne)
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Iterator<C> serviceLoaderIterator = ServiceLoader.load(clazz, classLoader).iterator();

        // create a new list so we can log the count
        List<C> serviceImplementations = new ArrayList<C>();
        while(serviceLoaderIterator.hasNext())
        {
            C next = serviceLoaderIterator.next();
            if(!isDisabled(clazz, next))
            {
                serviceImplementations.add(next);
            }
        }

        if(atLeastOne && serviceImplementations.isEmpty())
        {
            throw new ServerScopedRuntimeException("At least one implementation of " + clazz + " expected");
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Found " + serviceImplementations.size() + " implementations of " + clazz);
        }

        return serviceImplementations;
    }

    private <C extends Pluggable> boolean isDisabled(Class<C> clazz, final C next)
    {
        return Boolean.getBoolean("qpid.plugin.disabled:"+clazz.getSimpleName().toLowerCase()+"."+next.getType())
                || (next instanceof ConfiguredObjectTypeFactory && isDisabledConfiguredType((ConfiguredObjectTypeFactory<?>) next));
    }

    private boolean isDisabledConfiguredType(final ConfiguredObjectTypeFactory<?> typeFactory)
    {
        String simpleName = typeFactory.getCategoryClass().getSimpleName().toLowerCase();
        return Boolean.getBoolean("qpid.type.disabled:" + simpleName
                                  + "." + typeFactory.getType());
    }
}
