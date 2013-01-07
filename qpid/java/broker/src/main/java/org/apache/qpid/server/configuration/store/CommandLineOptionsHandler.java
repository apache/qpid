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
    private final Map<UUID, ConfigurationEntry> _cliEntries;
    private final ConfigurationEntry _root;
    private final Set<UUID> _originalStorePortIds;

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
            Collection<ConfigurationEntry> mutableConfigurationEntries = new ArrayList<ConfigurationEntry>();
            _originalStorePortIds = new HashSet<UUID>();
            for (ConfigurationEntry configurationEntry : storePorts)
            {
                mutableConfigurationEntries.add(configurationEntry.clone());
                rootChildren.remove(configurationEntry.getId());
                _originalStorePortIds.add(configurationEntry.getId());
            }
            for (ConfigurationAction configurationAction : configActions)
            {
                mutableConfigurationEntries = configurationAction.apply(mutableConfigurationEntries);
            }
            _cliEntries = new HashMap<UUID, ConfigurationEntry>();
            for (ConfigurationEntry mutableConfigurationEntry : mutableConfigurationEntries)
            {
                _cliEntries.put(mutableConfigurationEntry.getId(), mutableConfigurationEntry);
                rootChildren.add(mutableConfigurationEntry.getId());
            }
            _root = new ConfigurationEntry(root.getId(), root.getType(), root.getAttributes(), rootChildren, this);
        }
        else
        {
            _root = null;
            _cliEntries = null;
            _originalStorePortIds = null;
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
            ConfigurationEntry entry = _cliEntries.get(id);
            if (entry != null)
            {
                return entry;
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
                    for (ConfigurationEntry substitutedEntry : _cliEntries.values())
                    {
                        ConfigurationEntryStore entryStore = substitutedEntry.getStore();
                        if (entryStore == this)
                        {
                            childrenIds.remove(substitutedEntry.getId());
                        }
                    }
                    childrenIds.addAll(_originalStorePortIds);
                    entry = new ConfigurationEntry(_root.getId(), _root.getType(), _root.getAttributes(), childrenIds, _store);
                    storeEntries.add(entry);
                }
                ConfigurationEntry override = _cliEntries.get(entry.getId());
                if (override == null)
                {
                    storeEntries.add(entry);
                }
                if  (override != null)
                {
                    if (override.getStore() == _store)
                    {
                        throw new IllegalConfigurationException("Cannot store entry which was overridden by command line options: "  + entry);
                    }
                    nonStoreEntries.add(entry);
                }
            }
            _store.save(storeEntries.toArray(new ConfigurationEntry[storeEntries.size()]));
            for (ConfigurationEntry entry : nonStoreEntries)
            {
                ConfigurationEntry override = _cliEntries.get(entry.getId());
                Map<String, Object> attributes = entry.getAttributes();
                for (Map.Entry<String, Object> entryAttribute : attributes.entrySet())
                {
                    override.setAttribute(entryAttribute.getKey(), entryAttribute.getValue());
                }
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
                ConfigurationEntry override = _cliEntries.get(entryId);
                if (override == null || override.getStore() == _store)
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
                ConfigurationEntry entry = _cliEntries.remove(entryId);
                if (entry != null)
                {
                    deleted.add(entryId);
                }
            }
            for (UUID entryId : storeEntries)
            {
                _originalStorePortIds.remove(entryId);
            }
            return deleted.toArray(new UUID[deleted.size()]);
        }
    }

    public static class ConfigurationEntryHelper
    {
        public static int getPortAttribute(Map<String, Object> attributes)
        {
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

        public static Set<Protocol> getProtocolsAttribute(Map<String, Object> attributes)
        {
            Set<Protocol> protocols = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
            if (protocols == null || protocols.isEmpty())
            {
                throw new IllegalConfigurationException("Protocols attribute is not set for port entry " + attributes);
            }
            return protocols;
        }

        public static Set<Transport> getTransportsAttribute(Map<String, Object> attributes)
        {
            Set<Transport> transports = MapValueConverter.getEnumSetAttribute(Port.TRANSPORTS, attributes, Transport.class);
            if (transports == null || transports.isEmpty())
            {
                throw new IllegalConfigurationException("Transports attribute is not set for port entry " + attributes);
            }
            return transports;
        }

    }

    public interface ConfigurationAction
    {
        Collection<ConfigurationEntry> apply(Collection<ConfigurationEntry> entries);
    }

    public static abstract class PortConfigurationAction implements ConfigurationAction
    {
        @Override
        public Collection<ConfigurationEntry> apply(Collection<ConfigurationEntry> entries)
        {
            for (ConfigurationEntry configurationEntry : entries)
            {
                if (PORT_TYPE_NAME.equals(configurationEntry.getType()))
                {
                    onConfigurationEntry((ConfigurationEntry) configurationEntry);
                }
            }
            return entries;
        }

        public abstract void onConfigurationEntry(ConfigurationEntry configurationEntry);

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
        public void onConfigurationEntry(ConfigurationEntry configurationEntry)
        {
            Map<String, Object> attributes = configurationEntry.getAttributes();
            Set<Protocol> protocols = ConfigurationEntryHelper.getProtocolsAttribute(attributes);
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
        public void onConfigurationEntry(ConfigurationEntry configurationEntry)
        {
            Map<String, Object> attributes = configurationEntry.getAttributes();
            int port = ConfigurationEntryHelper.getPortAttribute(attributes);
            if (_excludedPorts.contains(port))
            {
                Set<Protocol> protocols = ConfigurationEntryHelper.getProtocolsAttribute(attributes);
                if (protocols.contains(_protocol))
                {
                    Set<Protocol> newProtocols = new HashSet<Protocol>(protocols);
                    newProtocols.remove(_protocol);
                    configurationEntry.setAttribute(Port.PROTOCOLS, newProtocols);
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
        public void onConfigurationEntry(ConfigurationEntry configurationEntry)
        {
            Map<String, Object> attributes = configurationEntry.getAttributes();
            int port = ConfigurationEntryHelper.getPortAttribute(attributes);
            if (_includedPorts.contains(port))
            {
                Set<Protocol> protocols = ConfigurationEntryHelper.getProtocolsAttribute(attributes);
                if (!protocols.contains(_protocol))
                {
                    Set<Protocol> newProtocols = new HashSet<Protocol>(protocols);
                    newProtocols.add(_protocol);
                    configurationEntry.setAttribute(Port.PROTOCOLS, newProtocols);
                }
            }
        }
    }

    public static class AddAmqpPortAction implements ConfigurationAction
    {
        private int _port;
        private Transport _transport;
        private Set<Protocol> _protocols;
        private ConfigurationEntryStore _store;

        public AddAmqpPortAction(int port, Transport transport, Set<Protocol> protocols, ConfigurationEntryStore store)
        {
            super();
            _port = port;
            _transport = transport;
            _protocols = protocols;
            _store = store;
        }

        @Override
        public Collection<ConfigurationEntry> apply(Collection<ConfigurationEntry> entries)
        {
            ConfigurationEntry entry = findPortEntryWithTheSamePort(entries, _port);
            String portName = getPortName(_port);
            UUID id = UUIDGenerator.generateBrokerChildUUID(PORT_TYPE_NAME, portName);
            Map<String, Object> attributes = new HashMap<String, Object>();
            if (entry != null)
            {
                attributes.putAll(entry.getAttributes());
            }
            attributes.put(Port.NAME, portName);
            attributes.put(Port.TRANSPORTS, Collections.singleton(_transport));
            attributes.put(Port.PROTOCOLS, _protocols);
            attributes.put(Port.PORT, _port);
            ConfigurationEntry newEntry = new ConfigurationEntry(id, PORT_TYPE_NAME, attributes, Collections.<UUID> emptySet(), _store);
            List<ConfigurationEntry> newEntries = new ArrayList<ConfigurationEntry>();
            newEntries.add(newEntry);

            for (ConfigurationEntry configurationEntry : entries)
            {
                boolean isEntryFromOriginalStoreAndHasTheSameTransportAndProtocolType = false;
                if (PORT_TYPE_NAME.equals(configurationEntry.getType()) && configurationEntry.getStore() != _store)
                {
                    Map<String, Object> entryAttributes = configurationEntry.getAttributes();
                    Set<Transport> transports = ConfigurationEntryHelper.getTransportsAttribute(entryAttributes);
                    if (transports.contains(_transport))
                    {
                        Set<Protocol> protocols = ConfigurationEntryHelper.getProtocolsAttribute(entryAttributes);
                        for (Protocol protocol : protocols)
                        {
                            if (protocol.getProtocolType() == ProtocolType.AMQP)
                            {
                                isEntryFromOriginalStoreAndHasTheSameTransportAndProtocolType = true;
                                break;
                            }
                        }
                    }

                }
                if (!isEntryFromOriginalStoreAndHasTheSameTransportAndProtocolType)
                {
                    newEntries.add(configurationEntry);
                }
            }
            return newEntries;
        }

        private ConfigurationEntry findPortEntryWithTheSamePort(Collection<ConfigurationEntry> entries, int port)
        {
            ConfigurationEntry entry = null;
            for (ConfigurationEntry configurationEntry : entries)
            {
                if (PORT_TYPE_NAME.equals(configurationEntry.getType()))
                {
                    int entryPort = ConfigurationEntryHelper.getPortAttribute(configurationEntry.getAttributes());
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

    }

    public static class AddJmxPortAction implements ConfigurationAction
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
        public Collection<ConfigurationEntry> apply(Collection<ConfigurationEntry> entries)
        {
            String portName = getPortName(_port);
            UUID id = UUIDGenerator.generateBrokerChildUUID(PORT_TYPE_NAME, portName);
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(Port.NAME, portName);
            attributes.put(Port.TRANSPORTS, Collections.singleton(Transport.TCP));
            attributes.put(Port.PROTOCOLS, Collections.singleton(_protocol));
            attributes.put(Port.PORT, _port);
            ConfigurationEntry newEntry = new ConfigurationEntry(id, PORT_TYPE_NAME, attributes,
                    Collections.<UUID> emptySet(), _store);
            List<ConfigurationEntry> newEntries = new ArrayList<ConfigurationEntry>();
            newEntries.add(newEntry);
            for (ConfigurationEntry configurationEntry : entries)
            {
                boolean isEntryFromOriginalStoreAndHasTheSameProtocol = false;
                if (PORT_TYPE_NAME.equals(configurationEntry.getType()) && configurationEntry.getStore() != _store)
                {
                    Set<Protocol> protocols = ConfigurationEntryHelper.getProtocolsAttribute(configurationEntry.getAttributes());
                    if (protocols.contains(_protocol))
                    {
                        isEntryFromOriginalStoreAndHasTheSameProtocol = true;
                    }
                }
                if (!isEntryFromOriginalStoreAndHasTheSameProtocol)
                {
                    newEntries.add(configurationEntry);
                }
            }
            return newEntries;
        }

        private String getPortName(Integer amqpPort)
        {
            return "cliJmxPort" + amqpPort;
        }

    }
}
