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
package org.apache.qpid.server.model;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.store.ConfiguredObjectDependency;
import org.apache.qpid.server.store.ConfiguredObjectIdDependency;
import org.apache.qpid.server.store.ConfiguredObjectNameDependency;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

@ManagedObject (creatable = false)
public class SystemContext extends AbstractConfiguredObject<SystemContext>
{
    private static final UUID SYSTEM_ID = new UUID(0l, 0l);
    private final ConfiguredObjectFactory _objectFactory;
    private final EventLogger _eventLogger;
    private final LogRecorder _logRecorder;
    private final BrokerOptions _brokerOptions;

    @ManagedAttributeField
    private String _storePath;

    @ManagedAttributeField
    private String _storeType;

    public SystemContext(final TaskExecutor taskExecutor,
                         final ConfiguredObjectFactory configuredObjectFactory,
                         final EventLogger eventLogger,
                         final LogRecorder logRecorder,
                         final BrokerOptions brokerOptions)
    {
        super(SYSTEM_ID, createAttributes(brokerOptions), taskExecutor);
        _eventLogger = eventLogger;
        getTaskExecutor().start();
        _objectFactory = configuredObjectFactory;
        _logRecorder = logRecorder;
        _brokerOptions = brokerOptions;
        open();
    }

    public static Map<String, Object> createAttributes(final BrokerOptions brokerOptions)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(NAME, "System");
        attributes.put("storePath", brokerOptions.getConfigurationStoreLocation());
        attributes.put("storeTye", brokerOptions.getConfigurationStoreType());
        attributes.put(ConfiguredObject.CONTEXT, brokerOptions.getConfigProperties());
        return attributes;
    }

    public void resolveObjects(ConfiguredObjectRecord... records)
    {

        ConfiguredObjectFactory factory = getObjectFactory();

        Map<UUID, ConfiguredObject<?>> resolvedObjects = new HashMap<UUID, ConfiguredObject<?>>();
        resolvedObjects.put(getId(), this);

        Collection<ConfiguredObjectRecord> recordsWithUnresolvedParents = new ArrayList<ConfiguredObjectRecord>(Arrays.asList(records));
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
                for (ConfiguredObjectRecord parent : record.getParents().values())
                {
                    if (!resolvedObjects.containsKey(parent.getId()))
                    {
                        foundParents = false;
                        break;
                    }
                    else
                    {
                        parents.add(resolvedObjects.get(parent.getId()));
                    }
                }
                if (foundParents)
                {
                    iter.remove();
                    UnresolvedConfiguredObject<? extends ConfiguredObject> recovered =
                            factory.recover(record, parents.toArray(new ConfiguredObject<?>[parents.size()]));
                    Collection<ConfiguredObjectDependency<?>> dependencies =
                            recovered.getUnresolvedDependencies();
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

            Iterator<UnresolvedConfiguredObject<? extends ConfiguredObject>> unresolvedIter =
                    recordsWithUnresolvedDependencies.iterator();

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

    @Override
    protected boolean setState(final State currentState, final State desiredState)
    {
        throw new IllegalArgumentException("Cannot change the state of the SystemContext object");
    }

    @Override
    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getState()
    {
        return State.ACTIVE;
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalArgumentException("Cannot change the durability of the SystemContext object");
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalArgumentException("Cannot change the lifetime of the SystemContext object");
    }

    public ConfiguredObjectFactory getObjectFactory()
    {
        return _objectFactory;
    }

    public EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    public LogRecorder getLogRecorder()
    {
        return _logRecorder;
    }

    public BrokerOptions getBrokerOptions()
    {
        return _brokerOptions;
    }

    @ManagedAttribute( automate = true )
    public String getStorePath()
    {
        return _storePath;
    }

    @ManagedAttribute( automate = true )
    public String getStoreType()
    {
        return _storeType;
    }

    public void close()
    {
        try
        {


            if (getTaskExecutor() != null)
            {
                getTaskExecutor().stop();
            }

            _eventLogger.message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

        }
        finally
        {
            if (getTaskExecutor() != null)
            {
                getTaskExecutor().stopImmediately();
            }
        }

    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(getClass());
    }

    public Broker getBroker()
    {
        Collection<Broker> children = getChildren(Broker.class);
        if(children == null || children.isEmpty())
        {
            return null;
        }
        else if(children.size() != 1)
        {
            throw new IllegalConfigurationException("More than one broker has been registered in a single context");
        }
        return children.iterator().next();
    }
}
