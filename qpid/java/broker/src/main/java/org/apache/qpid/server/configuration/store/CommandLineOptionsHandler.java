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

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.ProtocolExclusion;
import org.apache.qpid.server.ProtocolInclusion;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Protocol.ProtocolType;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.util.MapValueConverter;

public class CommandLineOptionsHandler implements ConfigurationEntryStore
{
    private static final String PORT_TYPE_NAME = Port.class.getSimpleName();
    private static final EnumSet<Protocol> AMQP_PROTOCOLS = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1,
            Protocol.AMQP_0_10, Protocol.AMQP_1_0);

    private final ConfigurationEntryStore _store;
    private final Map<UUID, MutableConfigurationEntry> _cliEntries;
    private final ConfigurationEntry _root;

    public CommandLineOptionsHandler(BrokerOptions options, ConfigurationEntryStore store)
    {
        super();
        _store = store;
        List<ConfigurationAction> configActions = buildConfigurationActions(options);
        if (!configActions.isEmpty())
        {
            ConfigurationEntry root = store.getRootEntry();
            Set<UUID> rootChildren = new HashSet<UUID>(root.getChildrenIds());
            Collection<ConfigurationEntry> storePorts = root.getChildren().get(PORT_TYPE_NAME);
            Collection<MutableConfigurationEntry> mutableConfigurationEntries = new ArrayList<MutableConfigurationEntry>();
            for (ConfigurationEntry configurationEntry : storePorts)
            {
                mutableConfigurationEntries.add(new PortMutableConfigurationEntry(configurationEntry));
                rootChildren.remove(configurationEntry.getId());
            }
            for (ConfigurationAction configurationAction : configActions)
            {
                mutableConfigurationEntries = configurationAction.apply(mutableConfigurationEntries);
            }
            _cliEntries = new HashMap<UUID, MutableConfigurationEntry>();
            for (MutableConfigurationEntry mutableConfigurationEntry : mutableConfigurationEntries)
            {
                _cliEntries.put(mutableConfigurationEntry.getId(), mutableConfigurationEntry);
                if (!mutableConfigurationEntry.isDisabled())
                {
                    rootChildren.add(mutableConfigurationEntry.getId());
                }
            }
            _root = new ConfigurationEntry(root.getId(), root.getType(), root.getAttributes(), rootChildren, this);
        }
        else
        {
            _root = null;
            _cliEntries = null;
        }
    }

    public List<ConfigurationAction> buildConfigurationActions(BrokerOptions options)
    {
        List<ConfigurationAction> actions = new ArrayList<ConfigurationAction>();

        Set<Integer> optionsAmqpPorts = options.getPorts();
        for (Integer port : optionsAmqpPorts)
        {
            actions.add(new AddAmqpPortAction(port, Transport.TCP, AMQP_PROTOCOLS, this));
        }
        Set<Integer> optionsAmqpSslPorts = options.getSSLPorts();
        for (Integer port : optionsAmqpSslPorts)
        {
            actions.add(new AddAmqpPortAction(port, Transport.SSL, AMQP_PROTOCOLS, this));
        }
        Integer jmxConnectorPort = options.getJmxPortConnectorServer();
        if (jmxConnectorPort != null)
        {
            actions.add(new AddJmxPortAction(jmxConnectorPort, Protocol.JMX_RMI, this));
        }
        Integer jmxRegistryPort = options.getJmxPortRegistryServer();
        if (jmxRegistryPort != null)
        {
            actions.add(new AddJmxPortAction(jmxRegistryPort, Protocol.RMI, this));
        }

        Set<Integer> exclude_1_0 = options.getExcludedPorts(ProtocolExclusion.v1_0);
        if (!exclude_1_0.isEmpty())
        {
            actions.add(new ProtocolExcludeAction(Protocol.AMQP_1_0, exclude_1_0));
        }
        Set<Integer> include_1_0 = options.getIncludedPorts(ProtocolInclusion.v1_0);
        if (!include_1_0.isEmpty())
        {
            actions.add(new ProtocolIncludeAction(Protocol.AMQP_1_0, include_1_0));
        }
        Set<Integer> exclude_0_10 = options.getExcludedPorts(ProtocolExclusion.v0_10);
        if (!exclude_0_10.isEmpty())
        {
            actions.add(new ProtocolExcludeAction(Protocol.AMQP_0_10, exclude_0_10));
        }
        Set<Integer> include_0_10 = options.getIncludedPorts(ProtocolInclusion.v0_10);
        if (!include_0_10.isEmpty())
        {
            actions.add(new ProtocolIncludeAction(Protocol.AMQP_0_10, include_0_10));
        }
        Set<Integer> exclude_0_9_1 = options.getExcludedPorts(ProtocolExclusion.v0_9_1);
        if (!exclude_0_9_1.isEmpty())
        {
            actions.add(new ProtocolExcludeAction(Protocol.AMQP_0_9_1, exclude_0_9_1));
        }
        Set<Integer> include_0_9_1 = options.getIncludedPorts(ProtocolInclusion.v0_9_1);
        if (!include_0_9_1.isEmpty())
        {
            actions.add(new ProtocolIncludeAction(Protocol.AMQP_0_9_1, include_0_9_1));
        }
        Set<Integer> exclude_0_9 = options.getExcludedPorts(ProtocolExclusion.v0_9);
        if (!exclude_0_9.isEmpty())
        {
            actions.add(new ProtocolExcludeAction(Protocol.AMQP_0_9, exclude_0_9));
        }
        Set<Integer> include_0_9 = options.getIncludedPorts(ProtocolInclusion.v0_9);
        if (!include_0_9.isEmpty())
        {
            actions.add(new ProtocolIncludeAction(Protocol.AMQP_0_9, include_0_9));
        }
        Set<Integer> exclude_0_8 = options.getExcludedPorts(ProtocolExclusion.v0_8);
        if (!exclude_0_8.isEmpty())
        {
            actions.add(new ProtocolExcludeAction(Protocol.AMQP_0_8, exclude_0_8));
        }
        Set<Integer> include_0_8 = options.getIncludedPorts(ProtocolInclusion.v0_8);
        if (!include_0_8.isEmpty())
        {
            actions.add(new ProtocolIncludeAction(Protocol.AMQP_0_8, include_0_8));
        }

        String bindingAddress = options.getBind();
        if (bindingAddress != null)
        {
            actions.add(new BindingAddressAction(bindingAddress));
        }
        return actions;
    }

    @Override
    public synchronized ConfigurationEntry getRootEntry()
    {
        if (_root == null)
        {
            return _store.getRootEntry();
        }
        return _root;
    }

    @Override
    public synchronized ConfigurationEntry getEntry(UUID id)
    {
        if (_cliEntries != null)
        {
            if (id == _root.getId())
            {
                return _root;
            }
            MutableConfigurationEntry entry = _cliEntries.get(id);
            if (entry != null && !entry.isDisabled())
            {
                return entry.toConfigurationEntry();
            }
        }
        return _store.getEntry(id);
    }

    @Override
    public synchronized void save(ConfigurationEntry... entries)
    {
        if (_root == null)
        {
            _store.save(entries);
        }
        else
        {
            List<ConfigurationEntry> storeEntries = new ArrayList<ConfigurationEntry>();
            List<ConfigurationEntry> nonStoreEntries = new ArrayList<ConfigurationEntry>();
            for (ConfigurationEntry entry : entries)
            {
                if (entry.getId() == _root.getId())
                {
                    // remove command line ports from broker children
                    Set<UUID> childrenIds = new HashSet<UUID>(entry.getChildrenIds());
                    for (MutableConfigurationEntry substitutedEntry : _cliEntries.values())
                    {
                        ConfigurationEntry original = substitutedEntry.getOriginal();
                        if (original == null)
                        {
                            childrenIds.remove(substitutedEntry.getId());
                        }
                        else
                        {
                            childrenIds.add(substitutedEntry.getId());
                        }
                    }
                    entry = new ConfigurationEntry(_root.getId(), _root.getType(), _root.getAttributes(), childrenIds, _store);
                    storeEntries.add(entry);
                }
                MutableConfigurationEntry override = _cliEntries.get(entry.getId());
                if (override == null)
                {
                    storeEntries.add(entry);
                }
                if (override != null)
                {
                    if (override.isDisabled())
                    {
                        throw new IllegalConfigurationException("Cannot store entry which was overridden by command line options: "  + entry);
                    }
                    nonStoreEntries.add(entry);
                }
            }
            _store.save(storeEntries.toArray(new ConfigurationEntry[storeEntries.size()]));
            for (ConfigurationEntry entry : nonStoreEntries)
            {
                MutableConfigurationEntry override = _cliEntries.get(entry.getId());
                override.setAttributes(entry.getAttributes());
            }
        }
    }

    @Override
    public synchronized UUID[] remove(UUID... entryIds)
    {
        if (_root == null)
        {
            return _store.remove(entryIds);
        }
        else
        {
            Set<UUID> deleted = new HashSet<UUID>();
            List<UUID> storeEntries = new ArrayList<UUID>();
            List<UUID> nonStoreEntries = new ArrayList<UUID>();
            for (UUID entryId : entryIds)
            {
                if (entryId == _root.getId())
                {
                    throw new IllegalConfigurationException("Cannot remove root entry");
                }
                MutableConfigurationEntry override = _cliEntries.get(entryId);
                if (override == null || override.isDisabled())
                {
                    storeEntries.add(entryId);
                }
                if (override != null)
                {
                    nonStoreEntries.add(entryId);
                }
            }
            UUID[] result = _store.remove(storeEntries.toArray(new UUID[storeEntries.size()]));
            if (result != null && result.length > 0)
            {
                deleted.addAll(Arrays.asList(result));
            }
            for (UUID entryId : nonStoreEntries)
            {
                MutableConfigurationEntry entry = _cliEntries.remove(entryId);
                if (entry != null)
                {
                    deleted.add(entryId);
                }
            }
            return deleted.toArray(new UUID[deleted.size()]);
        }
    }

    public static class MutableConfigurationEntry
    {
        private final UUID _id;
        private final String _type;
        private final Map<String, Object> _attributes;
        private final ConfigurationEntryStore _store;
        private final ConfigurationEntry _original;
        private boolean _disabled;

        private MutableConfigurationEntry(ConfigurationEntry original, UUID id, String type, ConfigurationEntryStore store)
        {
            super();
            _original = original;
            _attributes = new HashMap<String, Object>();
            _id = id;
            _type = type;
            _store = store;
            if (original != null)
            {
                Map<String, Object> originalAttributes = original.getAttributes();
                if (originalAttributes != null)
                {
                    _attributes.putAll(originalAttributes);
                }
            }
        }

        public MutableConfigurationEntry(ConfigurationEntry original)
        {
            this(original, original.getId(), original.getType(), original.getStore());
        }

        public MutableConfigurationEntry(UUID id, String type, ConfigurationEntryStore store)
        {
            this(null, id, type, store);
        }

        public ConfigurationEntry getOriginal()
        {
            return _original;
        }

        public void setAttribute(String name, Object value)
        {
            _attributes.put(name, value);
        }

        public void setAttributes(Map<String, Object> attributes)
        {
            for (Map.Entry<String, Object> attribute : attributes.entrySet())
            {
                _attributes.put(attribute.getKey(), attribute.getValue());
            }
        }

        public Map<String, Object> getAttributes()
        {
            return _attributes;
        }

        public ConfigurationEntry toConfigurationEntry()
        {
            if (_original == null)
            {
                return new ConfigurationEntry(_id, _type, _attributes, Collections.<UUID> emptySet(), _store);
            }
            return new ConfigurationEntry(_original.getId(), _original.getType(), _attributes, _original.getChildrenIds(),
                    _original.getStore());
        }

        public String getType()
        {
            return _type;
        }

        public void disable()
        {
            _disabled = true;
        }

        public boolean isDisabled()
        {
            return _disabled;
        }

        public UUID getId()
        {
            return _id;
        }

        @Override
        public String toString()
        {
            return "MutableConfigurationEntry [_id=" + _id + ", _type=" + _type + ", _attributes=" + _attributes + ", _disabled="
                    + _disabled + ", _store=" + _store + ", _original=" + _original + "]";
        }


    }

    public static class PortMutableConfigurationEntry extends MutableConfigurationEntry
    {
        public PortMutableConfigurationEntry(ConfigurationEntry original)
        {
            super(original);
            if (!PORT_TYPE_NAME.equals(original.getType()))
            {
                throw new IllegalConfigurationException("Not a valid port entry");
            }
        }

        public PortMutableConfigurationEntry(UUID id, String type, ConfigurationEntryStore store)
        {
            super(id, type, store);
            if (!PORT_TYPE_NAME.equals(type))
            {
                throw new IllegalConfigurationException("Not a valid port entry");
            }
        }

        public int getPortAttribute()
        {
            Map<String, Object> attributes = getAttributes();
            Object portAttribute = attributes.get(Port.PORT);
            if (portAttribute == null || "".equals(portAttribute))
            {
                throw new IllegalConfigurationException("Port attribute is not set for port entry " + attributes);
            }
            int port = 0;
            if (portAttribute instanceof String)
            {
                try
                {
                    port = Integer.parseInt((String) portAttribute);
                }
                catch (Exception e)
                {
                    throw new IllegalConfigurationException("Port attribute is not an integer: " + portAttribute);
                }
            }
            else if (portAttribute instanceof Number)
            {
                port = ((Number) portAttribute).intValue();
            }
            return port;
        }

        public Set<Protocol> getProtocolsAttribute()
        {
            Map<String, Object> attributes = getAttributes();
            Set<Protocol> protocols = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
            if (protocols == null || protocols.isEmpty())
            {
                throw new IllegalConfigurationException("Protocols attribute is not set for port entry " + attributes);
            }
            return protocols;
        }

        public Set<Transport> getTransportsAttribute()
        {
            Map<String, Object> attributes = getAttributes();
            Set<Transport> transports = MapValueConverter.getEnumSetAttribute(Port.TRANSPORTS, attributes, Transport.class);
            if (transports == null || transports.isEmpty())
            {
                throw new IllegalConfigurationException("Transports attribute is not set for port entry " + attributes);
            }
            return transports;
        }

        public void setProtocolsAttribute(Set<Protocol> protocols)
        {
            setAttribute(Port.PROTOCOLS, protocols);
        }
    }

    public interface ConfigurationAction
    {
        Collection<MutableConfigurationEntry> apply(Collection<MutableConfigurationEntry> entries);
    }

    public static abstract class PortConfigurationAction implements ConfigurationAction
    {
        @Override
        public Collection<MutableConfigurationEntry> apply(Collection<MutableConfigurationEntry> entries)
        {
            for (MutableConfigurationEntry configurationEntry : entries)
            {
                if (!configurationEntry.isDisabled() && configurationEntry instanceof PortMutableConfigurationEntry)
                {
                    onPortConfigurationEntry((PortMutableConfigurationEntry)configurationEntry);
                }
            }
            return entries;
        }

        public abstract void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry);

    }

    public static class BindingAddressAction extends PortConfigurationAction
    {
        private String _bindingAddress;
        private String _bindAddressOverride;

        public BindingAddressAction(String bindingAddress)
        {
            _bindingAddress = bindingAddress;
            if (WILDCARD_ADDRESS.equals(bindingAddress))
            {
                _bindAddressOverride = null;
            }
            else
            {
                _bindAddressOverride = _bindingAddress;
            }
        }

        @Override
        public void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry)
        {
            Set<Protocol> protocols = configurationEntry.getProtocolsAttribute();
            if (protocols.size() > 0)
            {
                Protocol protocol = protocols.iterator().next();
                if (protocol.isAMQP())
                {
                    configurationEntry.setAttribute(Port.BINDING_ADDRESS, _bindAddressOverride);
                }
            }
        }
    }

    public static class ProtocolExcludeAction extends PortConfigurationAction
    {
        private Protocol _protocol;
        private Collection<Integer> _excludedPorts;

        public ProtocolExcludeAction(Protocol protocol, Collection<Integer> excludedPorts)
        {
            super();
            _protocol = protocol;
            _excludedPorts = excludedPorts;
        }

        @Override
        public void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry)
        {
            int port = configurationEntry.getPortAttribute();
            if (_excludedPorts.contains(port))
            {
                Set<Protocol> protocols = configurationEntry.getProtocolsAttribute();
                if (protocols.contains(_protocol))
                {
                    protocols.remove(_protocol);
                    configurationEntry.setProtocolsAttribute(protocols);
                }
            }
        }
    }

    public static class ProtocolIncludeAction extends PortConfigurationAction
    {
        private Protocol _protocol;
        private Collection<Integer> _includedPorts;

        public ProtocolIncludeAction(Protocol protocol, Collection<Integer> includedPorts)
        {
            super();
            _protocol = protocol;
            _includedPorts = includedPorts;
        }

        @Override
        public void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry)
        {
            if (!configurationEntry.isDisabled())
            {
                int port = configurationEntry.getPortAttribute();
                if (_includedPorts.contains(port))
                {
                    Set<Protocol> protocols = configurationEntry.getProtocolsAttribute();
                    if (!protocols.contains(_protocol))
                    {
                        protocols.add(_protocol);
                        configurationEntry.setProtocolsAttribute(protocols);
                    }
                }
            }
        }
    }

    public static class AddAmqpPortAction extends PortConfigurationAction
    {
        private int _port;
        private Transport _transport;
        private Set<Protocol> _protocols;
        private ConfigurationEntryStore _store;

        public AddAmqpPortAction(int port, Transport transport, Set<Protocol> protocols,
                ConfigurationEntryStore store)
        {
            super();
            _port = port;
            _transport = transport;
            _protocols = protocols;
            _store = store;
        }

        @Override
        public Collection<MutableConfigurationEntry> apply(Collection<MutableConfigurationEntry> entries)
        {
            MutableConfigurationEntry entry = findPortEntryWithTheSamePort(entries, _port);
            if (entry == null)
            {
                // disable all store port entries with the same protocol type
                // and transport
                super.apply(entries);
            }
            else
            {
                entry.disable();
            }
            String portName = getPortName(_port);
            UUID id = UUIDGenerator.generateBrokerChildUUID(PORT_TYPE_NAME, portName);
            PortMutableConfigurationEntry newEntry = new PortMutableConfigurationEntry(id, PORT_TYPE_NAME, _store);
            if (entry != null)
            {
                newEntry.setAttributes(entry.getAttributes());
            }
            newEntry.setAttribute(Port.NAME, portName);
            newEntry.setAttribute(Port.TRANSPORTS, Collections.singleton(_transport));
            newEntry.setAttribute(Port.PROTOCOLS, _protocols);
            newEntry.setAttribute(Port.PORT, _port);
            List<MutableConfigurationEntry> newEntries = new ArrayList<MutableConfigurationEntry>(entries);
            newEntries.add(newEntry);
            return newEntries;
        }

        private MutableConfigurationEntry findPortEntryWithTheSamePort(Collection<MutableConfigurationEntry> entries, int port)
        {
            MutableConfigurationEntry entry = null;
            for (MutableConfigurationEntry configurationEntry : entries)
            {
                if (configurationEntry instanceof PortMutableConfigurationEntry)
                {
                    int entryPort = ((PortMutableConfigurationEntry)configurationEntry).getPortAttribute();
                    if (port == entryPort)
                    {
                        entry = configurationEntry;
                        break;
                    }
                }
            }
            return entry;
        }

        private String getPortName(Integer amqpPort)
        {
            return "cliAmqpPort" + amqpPort;
        }

        @Override
        public void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry)
        {
            // disable only configuration entry if it has original attached
            if (!configurationEntry.isDisabled() && configurationEntry.getOriginal() != null)
            {
                Set<Transport> transports = configurationEntry.getTransportsAttribute();

                // disable only configuration entry with the same transports
                if (transports.contains(_transport))
                {
                    Set<Protocol> protocols = configurationEntry.getProtocolsAttribute();
                    for (Protocol protocol : protocols)
                    {
                        if (protocol.getProtocolType() == ProtocolType.AMQP)
                        {
                            // disable only configuration entry with the same protocol type
                            configurationEntry.disable();
                            break;
                        }
                    }
                }
            }
        }
    }

    public static class AddJmxPortAction extends PortConfigurationAction
    {
        private int _port;
        private ConfigurationEntryStore _store;
        private Protocol _protocol;

        public AddJmxPortAction(int port, Protocol protocol, ConfigurationEntryStore store)
        {
            super();
            _port = port;
            _protocol = protocol;
            _store = store;
        }

        @Override
        public Collection<MutableConfigurationEntry> apply(Collection<MutableConfigurationEntry> entries)
        {
            Collection<MutableConfigurationEntry> sameProtocolEntries = findPortEntriesWithTheSameProtocol(entries, _protocol);
            super.apply(sameProtocolEntries);
            String portName = getPortName(_port);
            UUID id = UUIDGenerator.generateBrokerChildUUID(PORT_TYPE_NAME, portName);
            PortMutableConfigurationEntry newEntry = new PortMutableConfigurationEntry(id, PORT_TYPE_NAME, _store);
            newEntry.setAttribute(Port.NAME, portName);
            newEntry.setAttribute(Port.TRANSPORTS, Collections.singleton(Transport.TCP));
            newEntry.setAttribute(Port.PROTOCOLS, Collections.singleton(_protocol));
            newEntry.setAttribute(Port.PORT, _port);
            List<MutableConfigurationEntry> newEntries = new ArrayList<MutableConfigurationEntry>(entries);
            newEntries.add(newEntry);
            return newEntries;
        }

        private Collection<MutableConfigurationEntry> findPortEntriesWithTheSameProtocol(Collection<MutableConfigurationEntry> entries, Protocol protocol)
        {
            List<MutableConfigurationEntry> foundEntries = new ArrayList<MutableConfigurationEntry>();
            for (MutableConfigurationEntry configurationEntry : entries)
            {
                if (configurationEntry instanceof PortMutableConfigurationEntry)
                {
                    Set<Protocol> protocols = ((PortMutableConfigurationEntry)configurationEntry).getProtocolsAttribute();
                    if (protocols.contains(protocol))
                    {
                        foundEntries.add(configurationEntry);
                    }
                }
            }
            return foundEntries;
        }

        private String getPortName(Integer amqpPort)
        {
            return "cliJmxPort" + amqpPort;
        }

        @Override
        public void onPortConfigurationEntry(PortMutableConfigurationEntry configurationEntry)
        {
            if (!configurationEntry.isDisabled())
            {
                configurationEntry.disable();
            }
        }
    }
}
