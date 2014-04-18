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

import java.util.UUID;

import org.apache.qpid.server.model.ConfiguredObject;

public abstract class AbstractDurableConfiguredObjectRecoverer<T extends ConfiguredObject> implements DurableConfiguredObjectRecoverer
{
    @Override
    public void load(final DurableConfigurationRecoverer durableConfigurationRecoverer,
                     final ConfiguredObjectRecord record)
    {
        final UnresolvedObject<T> obj = createUnresolvedObject(record);
        UnresolvedDependency[] dependencies = obj.getUnresolvedDependencies();
        for(final UnresolvedDependency dependency : dependencies)
        {
            if(dependency.getId() != null)
            {
                Object dep;
                if ((dep = durableConfigurationRecoverer.getResolvedObject(dependency.getType(), dependency.getId()))
                    != null)
                {
                    dependency.resolve(dep);
                }
                else
                {
                    durableConfigurationRecoverer.addResolutionListener(dependency.getType(), dependency.getId(),
                                                                        null, new DependencyListener()
                                                                        {

                                                                            @Override
                                                                            public void dependencyResolved(final String depType,
                                                                                                           final UUID depId,
                                                                                                           final ConfiguredObject o)
                                                                            {
                                                                                dependency.resolve(o);
                                                                                if (obj.getUnresolvedDependencies().length
                                                                                    == 0)
                                                                                {
                                                                                    durableConfigurationRecoverer.resolve(
                                                                                            getType(),
                                                                                            record.getId(),
                                                                                            obj.resolve());
                                                                                }
                                                                            }
                                                                        }
                                                                       );
                }
            }
            else
            {
                Object dep;

                if ((dep = durableConfigurationRecoverer.getResolvedObject(dependency.getType(), dependency.getName()))
                    != null)
                {
                    dependency.resolve(dep);
                }
                else
                {
                    durableConfigurationRecoverer.addResolutionListener(dependency.getType(), dependency.getId(),
                                                                        dependency.getName(), new DependencyListener()
                                                                        {

                                                                            @Override
                                                                            public void dependencyResolved(final String depType,
                                                                                                           final UUID depId,
                                                                                                           final ConfiguredObject o)
                                                                            {
                                                                                dependency.resolve(o);
                                                                                if (obj.getUnresolvedDependencies().length
                                                                                    == 0)
                                                                                {
                                                                                    durableConfigurationRecoverer.resolve(
                                                                                            getType(),
                                                                                            record.getId(),
                                                                                            obj.resolve());
                                                                                }
                                                                            }
                                                                        }
                                                                       );
                }
            }
        }
        if(obj.getUnresolvedDependencies().length == 0)
        {
            durableConfigurationRecoverer.resolve(getType(), record.getId(), obj.resolve());
        }
        else
        {
            durableConfigurationRecoverer.addUnresolvedObject(getType(), record.getId(), obj);
        }

    }

    public abstract UnresolvedObject<T> createUnresolvedObject(final ConfiguredObjectRecord record);

}
