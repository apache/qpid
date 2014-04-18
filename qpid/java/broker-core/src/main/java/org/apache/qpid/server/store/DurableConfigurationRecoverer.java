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
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;

public class DurableConfigurationRecoverer implements ConfigurationRecoveryHandler
{
    private static final Logger _logger = Logger.getLogger(DurableConfigurationRecoverer.class);

    private final Map<String, Map<UUID, ConfiguredObject>> _resolvedObjects = new HashMap<String, Map<UUID, ConfiguredObject>>();

    private final Map<String, Map<UUID, UnresolvedObject>> _unresolvedObjects =
            new HashMap<String, Map<UUID, UnresolvedObject>>();
    private final List<ConfiguredObjectRecord> _records = new ArrayList<ConfiguredObjectRecord>();

    private final Map<String, Map<UUID, List<DependencyListener>>> _dependencyListeners =
            new HashMap<String, Map<UUID, List<DependencyListener>>>();
    private final Map<String, Map<String, List<DependencyListener>>> _dependencyNameListeners =
            new HashMap<String, Map<String, List<DependencyListener>>>();

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
    public void beginConfigurationRecovery(final DurableConfigurationStore store)
    {
        _logSubject = new MessageStoreLogSubject(_name, store.getClass().getSimpleName());

        _store = store;
        _eventLogger.message(_logSubject, ConfigStoreMessages.RECOVERY_START());
    }

    @Override
    public void configuredObject(ConfiguredObjectRecord record)
    {
        _records.add(record);
    }

    @Override
    public String completeConfigurationRecovery()
    {
        String configVersion = getConfigVersionFromRecords();

        _upgrader = _upgraderProvider.getUpgrader(configVersion, this);

        for (ConfiguredObjectRecord record : _records)
        {
            // We don't yet recover the VirtualHost record.
            if (!"VirtualHost".equals(record.getType()))
            {
                _upgrader.configuredObject(record);
            }
        }
        _upgrader.complete();
        checkUnresolvedDependencies();
        applyUpgrade();

        _eventLogger.message(_logSubject, ConfigStoreMessages.RECOVERY_COMPLETE());
        return Model.MODEL_VERSION;
    }

    private String getConfigVersionFromRecords()
    {
        String configVersion = Model.MODEL_VERSION;
        for (ConfiguredObjectRecord record : _records)
        {
            if ("VirtualHost".equals(record.getType()))
            {
                configVersion = (String) record.getAttributes().get("modelVersion");
                _logger.debug("Confifuration has config version : " + configVersion);
                break;
            }
        }
        return configVersion;
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
                               final String name,
                               final DependencyListener dependencyListener)
    {
        if(id != null)
        {
            Map<UUID, List<DependencyListener>> typeListeners = _dependencyListeners.get(type);
            if (typeListeners == null)
            {
                typeListeners = new HashMap<UUID, List<DependencyListener>>();
                _dependencyListeners.put(type, typeListeners);
            }
            List<DependencyListener> objectListeners = typeListeners.get(id);
            if (objectListeners == null)
            {
                objectListeners = new ArrayList<DependencyListener>();
                typeListeners.put(id, objectListeners);
            }
            objectListeners.add(dependencyListener);
        }
        else
        {
            Map<String, List<DependencyListener>> typeListeners = _dependencyNameListeners.get(type);
            if (typeListeners == null)
            {
                typeListeners = new HashMap<String, List<DependencyListener>>();
                _dependencyNameListeners.put(type, typeListeners);
            }
            List<DependencyListener> objectListeners = typeListeners.get(name);
            if (objectListeners == null)
            {
                objectListeners = new ArrayList<DependencyListener>();
                typeListeners.put(name, objectListeners);
            }
            objectListeners.add(dependencyListener);
        }
    }

    Object getResolvedObject(final String type, final UUID id)
    {
        Map<UUID, ConfiguredObject> objects = _resolvedObjects.get(type);
        return objects == null ? null : objects.get(id);
    }

    Object getResolvedObject(final String type, final String name)
    {
        Map<UUID, ConfiguredObject> objects = _resolvedObjects.get(type);
        if(objects != null)
        {
            for (ConfiguredObject object : objects.values())
            {
                if(object.getName().equals(name))
                {
                    return object;
                }
            }
        }
        return null;

    }

    void resolve(final String type, final UUID id, final ConfiguredObject object)
    {
        Map<UUID, ConfiguredObject> typeObjects = _resolvedObjects.get(type);
        if(typeObjects == null)
        {
            typeObjects = new HashMap<UUID, ConfiguredObject>();
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

        Map<String, List<DependencyListener>> typeNameListeners = _dependencyNameListeners.get(type);
        if(typeNameListeners != null)
        {
            List<DependencyListener> listeners = typeNameListeners.remove(object.getName());
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
