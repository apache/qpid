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

import java.util.Map;
import java.util.UUID;

public abstract class AbstractDurableConfiguredObjectRecoverer<T> implements DurableConfiguredObjectRecoverer
{
    @Override
    public void load(final DurableConfigurationRecoverer durableConfigurationRecoverer,
                     final UUID id,
                     final Map<String, Object> attributes)
    {
        final UnresolvedObject obj = createUnresolvedObject(id, getType(), attributes);
        UnresolvedDependency[] dependencies = obj.getUnresolvedDependencies();
        for(final UnresolvedDependency dependency : dependencies)
        {
            Object dep;
            if((dep = durableConfigurationRecoverer.getResolvedObject(dependency.getType(), dependency.getId())) != null)
            {
                dependency.resolve(dep);
            }
            else
            {
                durableConfigurationRecoverer.addResolutionListener(dependency.getType(), dependency.getId(),
                                                                    new DependencyListener()
                                                                    {

                                                                        @Override
                                                                        public void dependencyResolved(final String depType,
                                                                                                       final UUID depId,
                                                                                                       final Object o)
                                                                        {
                                                                            dependency.resolve(o);
                                                                            if(obj.getUnresolvedDependencies().length == 0)
                                                                            {
                                                                                durableConfigurationRecoverer.resolve(getType(), id, obj.resolve());
                                                                            }
                                                                        }
                                                                    });
            }
        }
        if(obj.getUnresolvedDependencies().length == 0)
        {
            durableConfigurationRecoverer.resolve(getType(), id, obj.resolve());
        }
        else
        {
            durableConfigurationRecoverer.addUnresolvedObject(getType(), id, obj);
        }

    }

    public abstract UnresolvedObject<T> createUnresolvedObject(final UUID id,
                                                    final String type,
                                                    final Map<String, Object> attributes);

}
