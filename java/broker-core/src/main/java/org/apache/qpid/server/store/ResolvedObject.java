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
package org.apache.qpid.server.store;

import java.util.Collection;
import java.util.Collections;

import org.apache.qpid.server.model.ConfiguredObject;

public class ResolvedObject<C extends ConfiguredObject<C>> implements UnresolvedConfiguredObject<C>
{

    private final C _resolved;


    private ResolvedObject(final C resolved)
    {
        _resolved = resolved;
    }

    @Override
    public ConfiguredObject<?>[] getParents()
    {
        final Collection<Class<? extends ConfiguredObject>> parentTypes =
                _resolved.getModel().getParentTypes(_resolved.getCategoryClass());
        ConfiguredObject<?>[] parents = new ConfiguredObject[parentTypes.size()];
        int i = 0;
        for(Class<? extends ConfiguredObject> parentType : parentTypes)
        {
            parents[i] = _resolved.getParent(parentType);
            i++;
        }
        return parents;
    }

    @Override
    public Collection<ConfiguredObjectDependency<?>> getUnresolvedDependencies()
    {
        return Collections.emptySet();
    }

    @Override
    public C resolve()
    {
        return _resolved;
    }

    public static <T extends ConfiguredObject<T>> ResolvedObject<T> newInstance(T object)
    {
        return new ResolvedObject<T>(object);
    }
}
