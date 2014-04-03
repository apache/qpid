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
package org.apache.qpid.server.configuration.store;

import org.apache.log4j.Logger;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryImpl;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.util.MapValueConverter;

import java.util.*;

public class ManagementModeStoreHandler implements ConfigurationEntryStore
{
    private static final Logger LOGGER = Logger.getLogger(ManagementModeStoreHandler.class);

    private static final String MANAGEMENT_MODE_PORT_PREFIX = "MANAGEMENT-MODE-PORT-";
    private static final String PORT_TYPE = Port.class.getSimpleName();
    private static final String VIRTUAL_HOST_TYPE = VirtualHost.class.getSimpleName();
    private static final String ATTRIBUTE_STATE = VirtualHost.STATE;
    private static final Object MANAGEMENT_MODE_AUTH_PROVIDER = "mm-auth";


    private final ConfigurationEntryStore _store;
    private final Map<UUID, ConfigurationEntry> _cliEntries;
    private final Map<UUID, Object> _quiescedEntriesOriginalState;
    private final UUID _rootId;
    private final BrokerOptions _options;
    private ConfiguredObject<?> _parent;

    public ManagementModeStoreHandler(ConfigurationEntryStore store, BrokerOptions options)
    {
        ConfigurationEntry storeRoot = store.getRootEntry();
        _options = options;
        _store = store;
        _rootId = storeRoot.getId();
        _cliEntries = createPortsFromCommandLineOptions(options);
        _quiescedEntriesOriginalState = quiesceEntries(storeRoot, options);
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        return getEntry(_rootId);
    }

    @Override
    public ConfigurationEntry getEntry(UUID id)
    {
        synchronized (_store)
        {
            if (_cliEntries.containsKey(id))
            {
                return _cliEntries.get(id);
            }

            ConfigurationEntry entry = _store.getEntry(id);
            if (_quiescedEntriesOriginalState.containsKey(id))
            {
                entry = createEntryWithState(entry, State.QUIESCED);
            }
            else if (id == _rootId)
            {
                entry = createRootWithCLIEntries(entry);
            }
            return entry;
        }
    }

    @Override
    public void openConfigurationStore(final ConfiguredObject<?> parent, final Map<String, Object> storeSettings)
            throws StoreException
    {
        _parent = parent;
        _store.openConfigurationStore(parent,storeSettings);
    }

    @Override
    public void recoverConfigurationStore(final ConfigurationRecoveryHandler recoveryHandler) throws StoreException
    {

        final Map<UUID,ConfiguredObjectRecord> records = new HashMap<UUID, ConfiguredObjectRecord>();
        final ConfigurationRecoveryHandler localRecoveryHandler = new ConfigurationRecoveryHandler()
        {
            private int _version;
            private boolean _quiesceRmiPort = _options.getManagementModeRmiPortOverride() > 0;
            private boolean _quiesceJmxPort = _options.getManagementModeJmxPortOverride() > 0;
            private boolean _quiesceHttpPort = _options.getManagementModeHttpPortOverride() > 0;
            @Override
            public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
            {
                _version = configVersion;
            }

            @Override
            public void configuredObject(final ConfiguredObjectRecord object)
            {
                String entryType = object.getType();
                Map<String, Object> attributes = object.getAttributes();
                boolean quiesce = false;
                if (VIRTUAL_HOST_TYPE.equals(entryType) && _options.isManagementModeQuiesceVirtualHosts())
                {
                    quiesce = true;
                }
                else if (PORT_TYPE.equals(entryType))
                {
                    if (attributes == null)
                    {
                        throw new IllegalConfigurationException("Port attributes are not set in " + object);
                    }
                    Set<Protocol> protocols = getPortProtocolsAttribute(attributes);
                    if (protocols == null)
                    {
                        quiesce = true;
                    }
                    else
                    {
                        for (Protocol protocol : protocols)
                        {
                            switch (protocol)
                            {
                                case JMX_RMI:
                                    quiesce = _quiesceJmxPort || _quiesceRmiPort ;
                                    break;
                                case RMI:
                                    quiesce = _quiesceRmiPort;
                                    break;
                                case HTTP:
                                    quiesce = _quiesceHttpPort;
                                    break;
                                default:
                                    quiesce = true;
                            }
                        }
                    }
                }
                if (quiesce)
                {
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Management mode quiescing entry " + object);
                    }

                    // save original state
                    _quiescedEntriesOriginalState.put(object.getId(), attributes.get(ATTRIBUTE_STATE));
                    Map<String,Object> modifiedAttributes = new HashMap<String, Object>(attributes);
                    modifiedAttributes.put(ATTRIBUTE_STATE, State.QUIESCED);
                    ConfiguredObjectRecord record = new ConfiguredObjectRecordImpl(object.getId(), object.getType(), modifiedAttributes, object.getParents());
                    records.put(record.getId(), record);

                }
                else
                {
                    records.put(object.getId(), object);
                }
            }


            @Override
            public int completeConfigurationRecovery()
            {
                return _version;
            }
        };

        ConfiguredObjectRecord parent = records.get(_parent.getId());
        if(parent == null)
        {
            parent = _parent.asObjectRecord();
        }
        for(ConfigurationEntry entry : _cliEntries.values())
        {
            records.put(entry.getId(),new ConfiguredObjectRecordImpl(entry.getId(), entry.getType(), entry.getAttributes(), Collections.singletonMap(parent.getType(), parent)));
        }


        _store.recoverConfigurationStore(localRecoveryHandler);

        recoveryHandler.beginConfigurationRecovery(this,0);

        for(ConfiguredObjectRecord record : records.values())
        {
            recoveryHandler.configuredObject(record);
        }
        recoveryHandler.completeConfigurationRecovery();
    }


    @Override
    public void create(final ConfiguredObjectRecord object)
    {
        synchronized (_store)
        {
            Collection<ConfigurationEntry> entriesToSave = new ArrayList<ConfigurationEntry>();
            entriesToSave.add(new ConfigurationEntryImpl(object.getId(),
                                                         object.getType(),
                                                         object.getAttributes(),
                                                         Collections.<UUID>emptySet(),
                                                         this));
            for (ConfiguredObjectRecord parent : object.getParents().values())
            {
                ConfigurationEntry parentEntry = getEntry(parent.getId());
                Set<UUID> children = new HashSet<UUID>(parentEntry.getChildrenIds());
                children.add(object.getId());
                ConfigurationEntry replacementEntry = new ConfigurationEntryImpl(parentEntry.getId(),
                                                                                 parent.getType(),
                                                                                 parent.getAttributes(),
                                                                                 children,
                                                                                 this);
                entriesToSave.add(replacementEntry);
            }
            save(entriesToSave.toArray(new ConfigurationEntry[entriesToSave.size()]));
        }
    }

    @Override
    public void update(final boolean createIfNecessary, final ConfiguredObjectRecord... records) throws StoreException
    {
        synchronized (_store)
        {
            Map<UUID, ConfigurationEntry> updates = new HashMap<UUID, ConfigurationEntry>();


            for (ConfiguredObjectRecord record : records)
            {
                Set<UUID> currentChildren;

                final ConfigurationEntry entry = getEntry(record.getId());

                if (entry == null)
                {
                    if (createIfNecessary)
                    {
                        currentChildren = new HashSet<UUID>();
                    }
                    else
                    {
                        throw new StoreException("Cannot update record with id "
                                                 + record.getId()
                                                 + " as it does not exist");
                    }
                }
                else
                {
                    currentChildren = new HashSet<UUID>(entry.getChildrenIds());
                }

                updates.put(record.getId(),
                            new ConfigurationEntryImpl(record.getId(),
                                                       record.getType(),
                                                       record.getAttributes(),
                                                       currentChildren,
                                                       this)
                           );
            }

            for (ConfiguredObjectRecord record : records)
            {
                for (ConfiguredObjectRecord parent : record.getParents().values())
                {
                    ConfigurationEntry existingParentEntry = updates.get(parent.getId());
                    if (existingParentEntry == null)
                    {
                        existingParentEntry = getEntry(parent.getId());
                        if (existingParentEntry == null)
                        {
                            if(parent.getType().equals(SystemContext.class.getSimpleName()))
                            {
                                continue;
                            }
                            throw new StoreException("Unknown parent of type " + parent.getType() + " with id " + parent
                                    .getId());
                        }

                        Set<UUID> children = new HashSet<UUID>(existingParentEntry.getChildrenIds());
                        if (!children.contains(record.getId()))
                        {
                            children.add(record.getId());
                            ConfigurationEntry newParentEntry = new ConfigurationEntryImpl(existingParentEntry.getId(),
                                                                                           existingParentEntry.getType(),
                                                                                           existingParentEntry.getAttributes(),
                                                                                           children,
                                                                                           this);
                            updates.put(newParentEntry.getId(), newParentEntry);
                        }
                    }
                }

            }

            save(updates.values().toArray(new ConfigurationEntry[updates.size()]));
        }
    }

    @Override
    public void closeConfigurationStore() throws StoreException
    {
    }

    @Override
    public void save(ConfigurationEntry... entries)
    {
        synchronized (_store)
        {
            ConfigurationEntry[] entriesToSave = new ConfigurationEntry[entries.length];

            for (int i = 0; i < entries.length; i++)
            {
                ConfigurationEntry entry = entries[i];
                UUID id = entry.getId();
                if (_cliEntries.containsKey(id))
                {
                    throw new IllegalConfigurationException("Cannot save configuration provided as command line argument:"
                            + entry);
                }
                else if (_quiescedEntriesOriginalState.containsKey(id))
                {
                    // save entry with the original state
                    entry = createEntryWithState(entry, _quiescedEntriesOriginalState.get(id));
                }
                else if (_rootId.equals(id))
                {
                    // save root without command line entries
                    Set<UUID> childrenIds = new HashSet<UUID>(entry.getChildrenIds());
                    if (!_cliEntries.isEmpty())
                    {
                        childrenIds.removeAll(_cliEntries.keySet());
                    }
                    HashMap<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
                    entry = new ConfigurationEntryImpl(entry.getId(), entry.getType(), attributes, childrenIds, this);
                }
                entriesToSave[i] = entry;
            }

            _store.save(entriesToSave);
        }
    }

    @Override
    public synchronized UUID[] remove(final ConfiguredObjectRecord... records)
    {
        synchronized (_store)
        {
            UUID[] idsToRemove = new UUID[records.length];
            for(int i = 0; i < records.length; i++)
            {
                idsToRemove[i] = records[i].getId();
            }

            for (UUID id : idsToRemove)
            {
                if (_cliEntries.containsKey(id))
                {
                    throw new IllegalConfigurationException("Cannot change configuration for command line entry:"
                                                            + _cliEntries.get(id));
                }
            }
            UUID[] result = _store.remove(records);
            for (UUID id : idsToRemove)
            {
                if (_quiescedEntriesOriginalState.containsKey(id))
                {
                    _quiescedEntriesOriginalState.remove(id);
                }
            }
            return result;
        }
    }

    @Override
    public void copyTo(String copyLocation)
    {
        synchronized (_store)
        {
            _store.copyTo(copyLocation);
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _store.getStoreLocation();
    }

    @Override
    public int getVersion()
    {
        return _store.getVersion();
    }

    @Override
    public String getType()
    {
        return _store.getType();
    }

    private Map<UUID, ConfigurationEntry> createPortsFromCommandLineOptions(BrokerOptions options)
    {
        int managementModeRmiPortOverride = options.getManagementModeRmiPortOverride();
        if (managementModeRmiPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid rmi port is specified: " + managementModeRmiPortOverride);
        }
        int managementModeJmxPortOverride = options.getManagementModeJmxPortOverride();
        if (managementModeJmxPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid jmx port is specified: " + managementModeJmxPortOverride);
        }
        int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();
        if (managementModeHttpPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid http port is specified: " + managementModeHttpPortOverride);
        }
        Map<UUID, ConfigurationEntry> cliEntries = new HashMap<UUID, ConfigurationEntry>();
        if (managementModeRmiPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeRmiPortOverride, Protocol.RMI);
            cliEntries.put(entry.getId(), entry);
            if (managementModeJmxPortOverride == 0)
            {
                ConfigurationEntry connectorEntry = createCLIPortEntry(managementModeRmiPortOverride + 100, Protocol.JMX_RMI);
                cliEntries.put(connectorEntry.getId(), connectorEntry);
            }
        }
        if (managementModeJmxPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeJmxPortOverride, Protocol.JMX_RMI);
            cliEntries.put(entry.getId(), entry);
        }
        if (managementModeHttpPortOverride != 0)
        {
            ConfigurationEntry entry = createCLIPortEntry(managementModeHttpPortOverride, Protocol.HTTP);
            cliEntries.put(entry.getId(), entry);
        }
        return cliEntries;
    }

    private ConfigurationEntry createCLIPortEntry(int port, Protocol protocol)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, port);
        attributes.put(Port.PROTOCOLS, Collections.singleton(protocol));
        attributes.put(Port.NAME, MANAGEMENT_MODE_PORT_PREFIX + protocol.name());
        if (protocol != Protocol.RMI)
        {
            attributes.put(Port.AUTHENTICATION_PROVIDER, MANAGEMENT_MODE_AUTH_PROVIDER);
        }
        ConfigurationEntry portEntry = new ConfigurationEntryImpl(UUID.randomUUID(), PORT_TYPE, attributes,
                Collections.<UUID> emptySet(), this);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Add management mode port configuration " + portEntry + " for port " + port + " and protocol "
                    + protocol);
        }
        return portEntry;
    }

    private ConfigurationEntry createRootWithCLIEntries(ConfigurationEntry storeRoot)
    {
        Set<UUID> childrenIds = new HashSet<UUID>(storeRoot.getChildrenIds());
        if (!_cliEntries.isEmpty())
        {
            childrenIds.addAll(_cliEntries.keySet());
        }
        ConfigurationEntry root = new ConfigurationEntryImpl(storeRoot.getId(), storeRoot.getType(), new HashMap<String, Object>(
                storeRoot.getAttributes()), childrenIds, this);
        return root;
    }

    private Map<UUID, Object> quiesceEntries(ConfigurationEntry storeRoot, BrokerOptions options)
    {
        Map<UUID, Object> quiescedEntries = new HashMap<UUID, Object>();
        Set<UUID> childrenIds;
        int managementModeRmiPortOverride = options.getManagementModeRmiPortOverride();
        int managementModeJmxPortOverride = options.getManagementModeJmxPortOverride();
        int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();
        childrenIds = storeRoot.getChildrenIds();
        for (UUID id : childrenIds)
        {
            ConfigurationEntry entry = _store.getEntry(id);
            String entryType = entry.getType();
            Map<String, Object> attributes = entry.getAttributes();
            boolean quiesce = false;
            if (VIRTUAL_HOST_TYPE.equals(entryType) && options.isManagementModeQuiesceVirtualHosts())
            {
                quiesce = true;
            }
            else if (PORT_TYPE.equals(entryType))
            {
                if (attributes == null)
                {
                    throw new IllegalConfigurationException("Port attributes are not set in " + entry);
                }
                Set<Protocol> protocols = getPortProtocolsAttribute(attributes);
                if (protocols == null)
                {
                    quiesce = true;
                }
                else
                {
                    for (Protocol protocol : protocols)
                    {
                        switch (protocol)
                        {
                        case JMX_RMI:
                            quiesce = managementModeJmxPortOverride > 0 || managementModeRmiPortOverride > 0;
                            break;
                        case RMI:
                            quiesce = managementModeRmiPortOverride > 0;
                            break;
                        case HTTP:
                            quiesce = managementModeHttpPortOverride > 0;
                            break;
                        default:
                            quiesce = true;
                        }
                    }
                }
            }
            if (quiesce)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Management mode quiescing entry " + entry);
                }

                // save original state
                quiescedEntries.put(entry.getId(), attributes.get(ATTRIBUTE_STATE));
            }
        }
        return quiescedEntries;
    }

    private Set<Protocol> getPortProtocolsAttribute(Map<String, Object> attributes)
    {
        Object object = attributes.get(Port.PROTOCOLS);
        if (object == null)
        {
            return null;
        }
        return MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
    }

    private ConfigurationEntry createEntryWithState(ConfigurationEntry entry, Object state)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        if (state == null)
        {
            attributes.remove(ATTRIBUTE_STATE);
        }
        else
        {
            attributes.put(ATTRIBUTE_STATE, state);
        }
        Set<UUID> originalChildren = entry.getChildrenIds();
        Set<UUID> children = null;
        if (originalChildren != null)
        {
            children = new HashSet<UUID>(originalChildren);
        }
        return new ConfigurationEntryImpl(entry.getId(), entry.getType(), attributes, children, entry.getStore());
    }

}
