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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.updater.VoidTask;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class GenericRecoverer
{
    private static final Logger LOGGER = Logger.getLogger(GenericRecoverer.class);

    private final ConfiguredObject<?> _parentOfRoot;
    private final String _rootCategory;

    public GenericRecoverer(ConfiguredObject<?> parentOfRoot, String rootCategory)
    {
        _parentOfRoot = parentOfRoot;
        _rootCategory = rootCategory;
    }

    public void recover(final List<ConfiguredObjectRecord> records)
    {
        _parentOfRoot.getTaskExecutor().run(new VoidTask()
        {
            @Override
            public void execute()
            {
                performRecover(records);
            }

            @Override
            public String toString()
            {
                return _rootCategory + " recovery";
            }
        });

    }

    private void performRecover(List<ConfiguredObjectRecord> records)
    {
        ConfiguredObjectRecord rootRecord = null;
        for (ConfiguredObjectRecord record : records)
        {
            if (_rootCategory.equals(record.getType()))
            {
                rootRecord = record;
                break;
            }
        }

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Root record " + rootRecord);
        }

        if (rootRecord != null)
        {

            if (rootRecord.getParents() == null || rootRecord.getParents().isEmpty())
            {
                records = new ArrayList<ConfiguredObjectRecord>(records);

                String parentOfRootCategory = _parentOfRoot.getCategoryClass().getSimpleName();
                Map<String, UUID> rootParents = Collections.singletonMap(parentOfRootCategory, _parentOfRoot.getId());
                records.remove(rootRecord);
                records.add(new ConfiguredObjectRecordImpl(rootRecord.getId(), _rootCategory, rootRecord.getAttributes(), rootParents));
            }

            resolveObjects(_parentOfRoot, records);
        }
    }

    private void resolveObjects(ConfiguredObject<?> parentObject, List<ConfiguredObjectRecord> records)
    {
        ConfiguredObjectFactory factory = parentObject.getObjectFactory();
        Map<UUID, ConfiguredObject<?>> resolvedObjects = new HashMap<UUID, ConfiguredObject<?>>();
        resolvedObjects.put(parentObject.getId(), parentObject);

        Collection<ConfiguredObjectRecord> recordsWithUnresolvedParents = new ArrayList<ConfiguredObjectRecord>(records);
        Collection<UnresolvedConfiguredObject<? extends ConfiguredObject>> recordsWithUnresolvedDependencies =
                new ArrayList<UnresolvedConfiguredObject<? extends ConfiguredObject>>();

        boolean updatesMade;

        do
        {
            updatesMade = false;
            Iterator<ConfiguredObjectRecord> iter = recordsWithUnresolvedParents.iterator();
            while (iter.hasNext())
            {
                ConfiguredObjectRecord record = iter.next();
                Collection<ConfiguredObject<?>> parents = new ArrayList<ConfiguredObject<?>>();
                boolean foundParents = true;
                for (UUID parentId : record.getParents().values())
                {
                    if (!resolvedObjects.containsKey(parentId))
                    {
                        foundParents = false;
                        break;
                    }
                    else
                    {
                        parents.add(resolvedObjects.get(parentId));
                    }
                }
                if (foundParents)
                {
                    iter.remove();
                    ConfiguredObject<?>[] parentArray = parents.toArray(new ConfiguredObject<?>[parents.size()]);
                    UnresolvedConfiguredObject<? extends ConfiguredObject> recovered =  factory.recover(record, parentArray);
                    Collection<ConfiguredObjectDependency<?>> dependencies = recovered.getUnresolvedDependencies();
                    if (dependencies.isEmpty())
                    {
                        updatesMade = true;
                        ConfiguredObject<?> resolved = recovered.resolve();
                        resolvedObjects.put(resolved.getId(), resolved);
                    }
                    else
                    {
                        recordsWithUnresolvedDependencies.add(recovered);
                    }
                }

            }

            Iterator<UnresolvedConfiguredObject<? extends ConfiguredObject>> unresolvedIter = recordsWithUnresolvedDependencies.iterator();

            while(unresolvedIter.hasNext())
            {
                UnresolvedConfiguredObject<? extends ConfiguredObject> unresolvedObject = unresolvedIter.next();
                Collection<ConfiguredObjectDependency<?>> dependencies =
                        new ArrayList<ConfiguredObjectDependency<?>>(unresolvedObject.getUnresolvedDependencies());

                for(ConfiguredObjectDependency dependency : dependencies)
                {
                    if(dependency instanceof ConfiguredObjectIdDependency)
                    {
                        UUID id = ((ConfiguredObjectIdDependency)dependency).getId();
                        if(resolvedObjects.containsKey(id))
                        {
                            dependency.resolve(resolvedObjects.get(id));
                        }
                    }
                    else if(dependency instanceof ConfiguredObjectNameDependency)
                    {
                        ConfiguredObject<?> dependentObject = null;
                        for(ConfiguredObject<?> parent : unresolvedObject.getParents())
                        {
                            dependentObject = parent.findConfiguredObject(dependency.getCategoryClass(), ((ConfiguredObjectNameDependency)dependency).getName());
                            if(dependentObject != null)
                            {
                                break;
                            }
                        }
                        if(dependentObject != null)
                        {
                            dependency.resolve(dependentObject);
                        }
                    }
                    else
                    {
                        throw new ServerScopedRuntimeException("Unknown dependency type " + dependency.getClass().getSimpleName());
                    }
                }
                if(unresolvedObject.getUnresolvedDependencies().isEmpty())
                {
                    updatesMade = true;
                    unresolvedIter.remove();
                    ConfiguredObject<?> resolved = unresolvedObject.resolve();
                    resolvedObjects.put(resolved.getId(), resolved);
                }
            }

        } while(updatesMade && !(recordsWithUnresolvedDependencies.isEmpty() && recordsWithUnresolvedParents.isEmpty()));

        if(!recordsWithUnresolvedDependencies.isEmpty())
        {
            throw new IllegalArgumentException("Cannot resolve some objects: " + recordsWithUnresolvedDependencies);
        }
        if(!recordsWithUnresolvedParents.isEmpty())
        {
            throw new IllegalArgumentException("Cannot resolve object because their parents cannot be found" + recordsWithUnresolvedParents);
        }
    }

}
