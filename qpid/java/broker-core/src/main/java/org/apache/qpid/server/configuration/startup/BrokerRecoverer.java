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
package org.apache.qpid.server.configuration.startup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.MessageLogger;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.adapter.AccessControlProviderFactory;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.model.adapter.GroupProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerRecoverer implements ConfiguredObjectRecoverer<Broker>
{
    private static final Pattern MODEL_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+$");

    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final EventLogger _eventLogger;
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final AccessControlProviderFactory _accessControlProviderFactory;
    private final PortFactory _portFactory;
    private final TaskExecutor _taskExecutor;
    private final BrokerOptions _brokerOptions;
    private final GroupProviderFactory _groupProviderFactory;
    private final StoreConfigurationChangeListener _storeChangeListener;

    public BrokerRecoverer(AuthenticationProviderFactory authenticationProviderFactory, GroupProviderFactory groupProviderFactory,
            AccessControlProviderFactory accessControlProviderFactory, PortFactory portFactory, StatisticsGatherer statisticsGatherer,
            VirtualHostRegistry virtualHostRegistry, LogRecorder logRecorder, EventLogger eventLogger, TaskExecutor taskExecutor,
            BrokerOptions brokerOptions, StoreConfigurationChangeListener storeChangeListener)
    {
        _groupProviderFactory = groupProviderFactory;
        _portFactory = portFactory;
        _authenticationProviderFactory = authenticationProviderFactory;
        _accessControlProviderFactory = accessControlProviderFactory;
        _statisticsGatherer = statisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _eventLogger = eventLogger;
        _taskExecutor = taskExecutor;
        _brokerOptions = brokerOptions;
        _storeChangeListener = storeChangeListener;
    }

    @Override
    public Broker create(RecovererProvider recovererProvider, ConfigurationEntry entry, ConfiguredObject... parents)
    {
        Map<String, Object> attributesCopy = validateAttributes(entry);

        attributesCopy.put(Broker.MODEL_VERSION, Model.MODEL_VERSION);

        BrokerAdapter broker = new BrokerAdapter(entry.getId(), attributesCopy, _statisticsGatherer, _virtualHostRegistry,
                _logRecorder,
                _eventLogger, _authenticationProviderFactory,_groupProviderFactory, _accessControlProviderFactory,
                _portFactory, _taskExecutor, entry.getStore(), _brokerOptions);

        broker.addChangeListener(_storeChangeListener);

        Map<String, Collection<ConfigurationEntry>> childEntries = new HashMap<String, Collection<ConfigurationEntry>>(entry.getChildren());

        List<String> types = makePrioritisedListOfTypes(childEntries.keySet(), TrustStore.class.getSimpleName(), KeyStore.class.getSimpleName(), AuthenticationProvider.class.getSimpleName());

        for (String type : types)
        {
            recoverType(recovererProvider, _storeChangeListener, broker, childEntries, type);
        }

        return broker;
    }

    private List<String> makePrioritisedListOfTypes(Set<String> allTypes, String... priorityOrderedTypes)
    {
        List<String> prioritisedList = new ArrayList<String>(allTypes.size());
        Set<String> remainder = new HashSet<String>(allTypes);

        for (String type : priorityOrderedTypes)
        {
            Set<String> singleton = Collections.singleton(type);
            Set<String> intersection = new HashSet<String>(allTypes);
            intersection.retainAll(singleton);
            remainder.removeAll(singleton);
            prioritisedList.addAll(intersection);
        }

        prioritisedList.addAll(remainder);
        return prioritisedList;
    }

    private Map<String, Object> validateAttributes(ConfigurationEntry entry)
    {
        Map<String, Object> attributes = entry.getAttributes();

        String modelVersion = null;
        if (attributes.containsKey(Broker.MODEL_VERSION))
        {
            modelVersion = MapValueConverter.getStringAttribute(Broker.MODEL_VERSION, attributes, null);
        }

        if (modelVersion == null)
        {
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " must be specified");
        }

        if (!MODEL_VERSION_PATTERN.matcher(modelVersion).matches())
        {
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " is specified in incorrect format: "
                    + modelVersion);
        }

        int versionSeparatorPosition = modelVersion.indexOf(".");
        String majorVersionPart = modelVersion.substring(0, versionSeparatorPosition);
        int majorModelVersion = Integer.parseInt(majorVersionPart);
        int minorModelVersion = Integer.parseInt(modelVersion.substring(versionSeparatorPosition + 1));

        if (majorModelVersion != Model.MODEL_MAJOR_VERSION || minorModelVersion > Model.MODEL_MINOR_VERSION)
        {
            throw new IllegalConfigurationException("The model version '" + modelVersion
                    + "' in configuration is incompatible with the broker model version '" + Model.MODEL_VERSION + "'");
        }

        if(!Model.MODEL_VERSION.equals(modelVersion))
        {
            String oldVersion;
            do
            {
                oldVersion = modelVersion;
                StoreUpgrader.upgrade(entry.getStore());
                entry = entry.getStore().getRootEntry();
                attributes = entry.getAttributes();
                modelVersion = MapValueConverter.getStringAttribute(Broker.MODEL_VERSION, attributes, null);
            }
            while(!(modelVersion.equals(oldVersion) || modelVersion.equals(Model.MODEL_VERSION)));
        }

        return new HashMap<String, Object>(attributes);
    }

    private void recoverType(RecovererProvider recovererProvider,
                             StoreConfigurationChangeListener storeChangeListener,
                             BrokerAdapter broker,
                             Map<String, Collection<ConfigurationEntry>> childEntries,
                             String type)
    {
        ConfiguredObjectRecoverer<?> recoverer = recovererProvider.getRecoverer(type);
        if (recoverer == null)
        {
            throw new IllegalConfigurationException("Cannot recover entry for the type '" + type + "' from broker");
        }
        Collection<ConfigurationEntry> entries = childEntries.get(type);
        for (ConfigurationEntry childEntry : entries)
        {
            ConfiguredObject object = recoverer.create(recovererProvider, childEntry, broker);
            if (object == null)
            {
                throw new IllegalConfigurationException("Cannot create configured object for the entry " + childEntry);
            }
            broker.recoverChild(object);
            object.addChangeListener(storeChangeListener);
        }
    }
}
