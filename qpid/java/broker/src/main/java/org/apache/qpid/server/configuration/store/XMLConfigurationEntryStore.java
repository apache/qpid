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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.apache.log4j.Logger;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectType;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.group.FileGroupManagerFactory;

public class XMLConfigurationEntryStore implements ConfigurationEntryStore
{
    private static final Logger _logger = Logger.getLogger(XMLConfigurationEntryStore.class);

    private UUID _rootId;
    private HierarchicalConfiguration _configuration;
    private Map<UUID, ConfigurationEntry> _rootChildren;
    private ServerConfiguration _serverConfiguration;

    private PortConfigurationHelper _portConfigurationHelper;

    public XMLConfigurationEntryStore(ServerConfiguration config, BrokerOptions options)
    {
        _serverConfiguration = config;
        _configuration = ConfigurationUtils.convertToHierarchical(config.getConfig());
        _rootId = UUID.randomUUID();
        _rootChildren = new HashMap<UUID, ConfigurationEntry>();

        _portConfigurationHelper = new PortConfigurationHelper(this);

        updateManagementPorts(_serverConfiguration, options);

        createGroupProviderConfig(_configuration, _rootChildren);
        createAuthenticationProviderConfig(_configuration, _rootChildren);
        createAmqpPortConfig(_serverConfiguration, _rootChildren, options);
        createManagementPortConfig(_serverConfiguration, _rootChildren, options);
        createVirtualHostConfig(_serverConfiguration, _rootChildren);

        // In order to avoid plugin recoverer failures for broker tests we are checking whether plugins classes are present in classpath
        Iterable<PluginFactory> factories= new QpidServiceLoader().instancesOf(PluginFactory.class);
        if (factories.iterator().hasNext())
        {
            createHttpManagementConfig(_serverConfiguration, _rootChildren);
            createJmxManagementConfig(_serverConfiguration, _rootChildren);
        }
        _logger.warn("Root children are: " + _rootChildren);
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        // XXX include all broker attributes
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.DEFAULT_AUTHENTICATION_PROVIDER, _serverConfiguration.getDefaultAuthenticationManager());
        ConfigurationEntry rootEntry = new ConfigurationEntry(_rootId, ConfiguredObjectType.BROKER, brokerAttributes,
                Collections.unmodifiableSet(_rootChildren.keySet()), this);

        _logger.warn("Returning root entry: " + rootEntry);

        return rootEntry;
    }

    private void createAuthenticationProviderConfig(Configuration configuration, Map<UUID, ConfigurationEntry> rootChildren)
    {
        HierarchicalConfiguration securityConfiguration = ConfigurationUtils.convertToHierarchical(
                configuration.subset("security"));

        Collection<ConfigurationNode> nodes = securityConfiguration.getRootNode().getChildren();
        for (ConfigurationNode configurationNode : nodes)
        {
            String name = configurationNode.getName();
            if (name.contains("auth-manager") && !"default-auth-manager".equals(name))
            {
                Map<String, Object> attributes = new HashMap<String, Object>();
                attributes.put(AuthenticationProvider.TYPE, name);
                Configuration config = configuration.subset("security." + name);
                Iterator<String> keysIterator = config.getKeys();
                while (keysIterator.hasNext())
                {
                    String key = keysIterator.next();
                    if (!"".equals(key))
                    {
                        List<Object> object = configuration.getList("security." + name + "." + key);
                        int size = object.size();
                        if (size == 0)
                        {
                            attributes.put(key, null);
                        }
                        else if (size == 1)
                        {
                            attributes.put(key, object.get(0));
                        }
                        else
                        {
                            attributes.put(key, object);
                        }
                    }
                }
                ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(),
                        ConfiguredObjectType.AUTHENTICATION_PROVIDER, attributes, null, this);
                rootChildren.put(entry.getId(), entry);
            }
        }

    }

    /** hard-coded values match those in {@link FileGroupManagerFactory} */
    private void createGroupProviderConfig(Configuration configuration, Map<UUID, ConfigurationEntry> rootChildren)
    {
        Configuration fileGroupManagerConfig = configuration.subset("security.file-group-manager");
        if(fileGroupManagerConfig != null && !fileGroupManagerConfig.isEmpty())
        {
            String file = fileGroupManagerConfig.getString("attributes.attribute.value");
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(GroupProvider.TYPE, "file-group-manager");
            attributes.put("file", file);
            ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.GROUP_PROVIDER, attributes, null, this);
            rootChildren.put(entry.getId(), entry);
        }
    }

    private void createAmqpPortConfig(ServerConfiguration serverConfig, Map<UUID, ConfigurationEntry> rootChildren,
            BrokerOptions options)
    {
        Map<UUID, ConfigurationEntry> amqpPortConfiguration = _portConfigurationHelper.getPortConfiguration(serverConfig,
                options);
        rootChildren.putAll(amqpPortConfiguration);

    }

    private void createManagementPortConfig(ServerConfiguration serverConfiguration, Map<UUID, ConfigurationEntry> rootChildren,
            BrokerOptions options)
    {
        if (serverConfiguration.getHTTPManagementEnabled())
        {
            ConfigurationEntry entry = createManagementHttpPort(serverConfiguration.getHTTPManagementPort(), Protocol.HTTP,
                    Transport.TCP);
            rootChildren.put(entry.getId(), entry);
        }
        if (serverConfiguration.getHTTPSManagementEnabled())
        {
            ConfigurationEntry entry = createManagementHttpPort(serverConfiguration.getHTTPSManagementPort(),
                    Protocol.HTTPS, Transport.SSL);
            rootChildren.put(entry.getId(), entry);
        }
        if (serverConfiguration.getJMXManagementEnabled())
        {
            // XXX: change JMX port to not rely on names
            ConfigurationEntry entryRegistry = createManagementJmxPort(serverConfiguration.getJMXPortRegistryServer(),
                    "registry", Protocol.JMX_RMI, Transport.TCP);
            rootChildren.put(entryRegistry.getId(), entryRegistry);
            Transport connectorTransport = serverConfiguration.getManagementSSLEnabled() ? Transport.SSL : Transport.TCP;
            ConfigurationEntry entryConnector = createManagementJmxPort(serverConfiguration.getJMXConnectorServerPort(),
                    "connector", Protocol.JMX_RMI, connectorTransport);
            rootChildren.put(entryConnector.getId(), entryConnector);
        }
    }

    /**
     * Update the configuration data with the management port.
     */
    private void updateManagementPorts(ServerConfiguration configuration, BrokerOptions options)
    {
        Integer registryServerPort = options.getJmxPortRegistryServer();
        Integer connectorServerPort = options.getJmxPortConnectorServer();

        if (registryServerPort != null)
        {
            try
            {
                configuration.setJMXPortRegistryServer(registryServerPort);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalConfigurationException("Invalid management (registry server) port: " + registryServerPort,
                        null);
            }
        }
        if (connectorServerPort != null)
        {
            try
            {
                configuration.setJMXPortConnectorServer(connectorServerPort);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalConfigurationException(
                        "Invalid management (connector server) port: " + connectorServerPort, null);
            }
        }
    }

    private ConfigurationEntry createManagementHttpPort(int port, final Protocol protocol, final Transport transport)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(protocol));
        attributes.put(Port.TRANSPORTS, Collections.singleton(transport));
        attributes.put(Port.PORT, port);
        attributes.put(Port.BINDING_ADDRESS, null);
        return new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.PORT, attributes, null, this);
    }

    private ConfigurationEntry createManagementJmxPort(int port, String name, final Protocol protocol,
            final Transport transport)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        // We current use the a special port name to distinguish between the
        // connection/registry servers. We need another 'type' attribute so we can determine its
        // role.
        attributes.put(Port.NAME, name);
        attributes.put(Port.PROTOCOLS, Collections.singleton(protocol));
        attributes.put(Port.TRANSPORTS, Collections.singleton(transport));
        attributes.put(Port.PORT, port);
        attributes.put(Port.BINDING_ADDRESS, null);
        return new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.PORT, attributes, null, this);
    }

    private void createVirtualHostConfig(ServerConfiguration serverConfiguration, Map<UUID, ConfigurationEntry> rootChildren)
    {
        for (String name : serverConfiguration.getVirtualHostsNames())
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(org.apache.qpid.server.model.VirtualHost.NAME, name);
            ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.VIRTUAL_HOST,
                    attributes, null, this);
            rootChildren.put(entry.getId(), entry);
        }
    }

    protected ConfigurationEntry createPortConfigurationEntry(ServerConfiguration serverConfiguration, Integer portNumber,
            Transport transport)
    {
        final Set<Protocol> supported = new HashSet<Protocol>();
        if (serverConfiguration.isAmqp010enabled())
        {
            supported.add(Protocol.AMQP_0_10);
        }
        if (serverConfiguration.isAmqp091enabled())
        {
            supported.add(Protocol.AMQP_0_9_1);
        }
        if (serverConfiguration.isAmqp09enabled())
        {
            supported.add(Protocol.AMQP_0_9);
        }
        if (serverConfiguration.isAmqp08enabled())
        {
            supported.add(Protocol.AMQP_0_8);
        }
        if (serverConfiguration.isAmqp10enabled())
        {
            supported.add(Protocol.AMQP_1_0);
        }

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, supported);
        attributes.put(Port.TRANSPORTS, Collections.singleton(transport));
        attributes.put(Port.PORT, portNumber);
        attributes.put(Port.BINDING_ADDRESS, getBindAddress(serverConfiguration));
        attributes.put(Port.TCP_NO_DELAY, serverConfiguration.getTcpNoDelay());
        attributes.put(Port.RECEIVE_BUFFER_SIZE, serverConfiguration.getReceiveBufferSize());
        attributes.put(Port.SEND_BUFFER_SIZE, serverConfiguration.getWriteBufferSize());
        attributes.put(Port.NEED_CLIENT_AUTH, serverConfiguration.needClientAuth());
        attributes.put(Port.WANT_CLIENT_AUTH, serverConfiguration.wantClientAuth());
        attributes.put(Port.AUTHENTICATION_MANAGER, serverConfiguration.getPortAuthenticationMappings().get(portNumber));

        ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.PORT, attributes, null,
                this);
        return entry;
    }

    private void createHttpManagementConfig(ServerConfiguration serverConfiguration, Map<UUID, ConfigurationEntry> rootChildren)
    {
        if(serverConfiguration.getHTTPManagementEnabled() || serverConfiguration.getHTTPSManagementEnabled())
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(PluginFactory.PLUGIN_TYPE, "MANAGEMENT-HTTP");
            attributes.put("keyStorePath", serverConfiguration.getManagementKeyStorePath());
            attributes.put("keyStorePassword", serverConfiguration.getManagementKeyStorePassword());
            attributes.put("sessionTimeout", serverConfiguration.getHTTPManagementSessionTimeout());

            ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.PLUGIN, attributes, null, this);
            rootChildren.put(entry.getId(), entry);
        }
    }

    private void createJmxManagementConfig(ServerConfiguration serverConfiguration, Map<UUID, ConfigurationEntry> rootChildren)
    {
        if(serverConfiguration.getJMXManagementEnabled())
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(PluginFactory.PLUGIN_TYPE, "MANAGEMENT-JMX");
            attributes.put("keyStorePath", serverConfiguration.getManagementKeyStorePath());
            attributes.put("keyStorePassword", serverConfiguration.getManagementKeyStorePassword());
            attributes.put("useCustomRMISocketFactory", serverConfiguration.getUseCustomRMISocketFactory());
            attributes.put("usePlatformMBeanServer", serverConfiguration.getPlatformMbeanserver());

            ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), ConfiguredObjectType.PLUGIN, attributes, null, this);
            rootChildren.put(entry.getId(), entry);
        }
    }

    private String getBindAddress(ServerConfiguration serverConfig)
    {
        String bindAddr = serverConfig.getBind();

        String bindAddress;
        if (bindAddr.equals(WILDCARD_ADDRESS))
        {
            bindAddress = null;
        }
        else
        {
            bindAddress = bindAddr;
        }
        return bindAddress;
    }

    /** note that this only works if the id is an immediate child of root */
    @Override
    public ConfigurationEntry getEntry(UUID id)
    {
        return _rootChildren.get(id);
    }

    @Override
    public void save(ConfigurationEntry... entries)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void remove(UUID... entryIds)
    {
        // TODO Auto-generated method stub

    }

}
