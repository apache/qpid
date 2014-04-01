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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;

import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

public class DurableConfigurationRecoverer implements ConfigurationRecoveryHandler
{
    private static final Logger _logger = Logger.getLogger(DurableConfigurationRecoverer.class);

    private final Map<String, Map<UUID, Object>> _resolvedObjects = new HashMap<String, Map<UUID, Object>>();

    private final Map<String, Map<UUID, UnresolvedObject>> _unresolvedObjects =
            new HashMap<String, Map<UUID, UnresolvedObject>>();

    private final Map<String, Map<UUID, List<DependencyListener>>> _dependencyListeners =
            new HashMap<String, Map<UUID, List<DependencyListener>>>();
    private final Map<String, DurableConfiguredObjectRecoverer> _recoverers;
    private final UpgraderProvider _upgraderProvider;
    private final EventLogger _eventLogger;

    private DurableConfigurationStoreUpgrader _upgrader;

    private DurableConfigurationStore _store;
    private final String _name;

    private MessageStoreLogSubject _logSubject;

    public DurableConfigurationRecoverer(final String name,
                                         Map<String, DurableConfiguredObjectRecoverer> recoverers,
                                         UpgraderProvider upgraderProvider, EventLogger eventLogger)
    {
        _recoverers = recoverers;
        _name = name;
        _upgraderProvider = upgraderProvider;
        _eventLogger = eventLogger;
    }

    @Override
    public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
    {
        _logSubject = new MessageStoreLogSubject(_name, store.getClass().getSimpleName());

        _store = store;
        _upgrader = _upgraderProvider.getUpgrader(configVersion, this);
        _eventLogger.message(_logSubject, ConfigStoreMessages.RECOVERY_START());
    }

    @Override
    public void configuredObject(ConfiguredObjectRecord record)
    {
        _upgrader.configuredObject(record);
    }

    void onConfiguredObject(ConfiguredObjectRecord record)
    {
        DurableConfiguredObjectRecoverer recoverer = getRecoverer(record.getType());
        if(recoverer == null)
        {
            throw new IllegalConfigurationException("Unknown type for configured object: " + record.getType());
        }
        recoverer.load(this, record);
    }

    private DurableConfiguredObjectRecoverer getRecoverer(final String type)
    {
        DurableConfiguredObjectRecoverer recoverer = _recoverers.get(type);
        return recoverer;
    }

    @Override
    public int completeConfigurationRecovery()
    {
        _upgrader.complete();
        checkUnresolvedDependencies();
        applyUpgrade();

        _eventLogger.message(_logSubject, ConfigStoreMessages.RECOVERY_COMPLETE());
        return CURRENT_CONFIG_VERSION;
    }

    private void applyUpgrade()
    {

        final Collection<ConfiguredObjectRecord> updates = new HashSet<ConfiguredObjectRecord>(_upgrader.getUpdatedRecords().values());
        final Collection<ConfiguredObjectRecord> deletes = new HashSet<ConfiguredObjectRecord>(_upgrader.getDeletedRecords().values());

        // Due to the way the upgraders work it is possible that the updates list may contain nulls
        updates.remove(null);
        deletes.remove(null);

        if(!updates.isEmpty())
        {
            _store.update(true,updates.toArray(new ConfiguredObjectRecord[updates.size()]));
        }
        if(!deletes.isEmpty())
        {
            _store.remove(deletes.toArray(new ConfiguredObjectRecord[deletes.size()]));
        }

    }

    private void checkUnresolvedDependencies()
    {
        if(_unresolvedObjects != null && !_unresolvedObjects.isEmpty())
        {
            boolean unresolvedObjectsExist = false;
            for(Map.Entry<String, Map<UUID, UnresolvedObject>>entry : _unresolvedObjects.entrySet())
            {
                for(Map.Entry<UUID,UnresolvedObject> obj : entry.getValue().entrySet())
                {
                    unresolvedObjectsExist = true;
                    StringBuilder errorMessage = new StringBuilder("Durable configured object of type ");
                    errorMessage.append(entry.getKey()).append(" with id ").append(obj.getKey())
                            .append(" has unresolved dependencies: ");
                    for(UnresolvedDependency dep : obj.getValue().getUnresolvedDependencies())
                    {
                        errorMessage.append(dep.getType()).append(" with id ").append(dep.getId()).append("; ");
                    }
                    _logger.error(errorMessage);
                }
            }
            if(unresolvedObjectsExist)
            {
                throw new IllegalConfigurationException("Durable configuration has unresolved dependencies");
            }
        }
    }

    void addResolutionListener(final String type,
                               final UUID id,
                               final DependencyListener dependencyListener)
    {
        Map<UUID, List<DependencyListener>> typeListeners = _dependencyListeners.get(type);
        if(typeListeners == null)
        {
            typeListeners = new HashMap<UUID, List<DependencyListener>>();
            _dependencyListeners.put(type, typeListeners);
        }
        List<DependencyListener> objectListeners = typeListeners.get(id);
        if(objectListeners == null)
        {
            objectListeners = new ArrayList<DependencyListener>();
            typeListeners.put(id, objectListeners);
        }
        objectListeners.add(dependencyListener);

    }

    Object getResolvedObject(final String type, final UUID id)
    {
        Map<UUID, Object> objects = _resolvedObjects.get(type);
        return objects == null ? null : objects.get(id);
    }

    void resolve(final String type, final UUID id, final Object object)
    {
        Map<UUID, Object> typeObjects = _resolvedObjects.get(type);
        if(typeObjects == null)
        {
            typeObjects = new HashMap<UUID, Object>();
            _resolvedObjects.put(type, typeObjects);
        }
        typeObjects.put(id, object);
        Map<UUID, UnresolvedObject> unresolved = _unresolvedObjects.get(type);
        if(unresolved != null)
        {
            unresolved.remove(id);
        }

        Map<UUID, List<DependencyListener>> typeListeners = _dependencyListeners.get(type);
        if(typeListeners != null)
        {
            List<DependencyListener> listeners = typeListeners.remove(id);
            if(listeners != null)
            {
                for(DependencyListener listener : listeners)
                {
                    listener.dependencyResolved(type, id, object);
                }
            }
        }
    }

    void addUnresolvedObject(final String type,
                             final UUID id,
                             final UnresolvedObject obj)
    {
        Map<UUID, UnresolvedObject> typeObjects = _unresolvedObjects.get(type);
        if(typeObjects == null)
        {
            typeObjects = new HashMap<UUID, UnresolvedObject>();
            _unresolvedObjects.put(type, typeObjects);
        }
        typeObjects.put(id, obj);
    }


}
