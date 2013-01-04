package org.apache.qpid.server.configuration.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.ConnectionSettings;

import com.sun.corba.se.pept.broker.Broker;

public class CommandLineOptionsHandlerTest extends QpidTestCase
{
    private ConfigurationEntryStore _originalStore;
    private Map<UUID, ConfigurationEntry> _originalStoreEntries;

    private UUID _brokerId;
    private UUID _amqpPortId;
    private UUID _amqpSslPortId;
    private UUID _registryPortId;
    private UUID _connectorPortId;
    private UUID _keyStoreId;
    private Set<UUID> _brokerOriginalChildren;
    private HashSet<String> _amqpPortProtocols;

    protected void setUp() throws Exception
    {
        super.setUp();
        _originalStore = mock(ConfigurationEntryStore.class);
        _originalStoreEntries = new HashMap<UUID, ConfigurationEntry>();
        _brokerId = UUID.randomUUID();
        _amqpPortId = UUID.randomUUID();
        _amqpSslPortId = UUID.randomUUID();
        _registryPortId = UUID.randomUUID();
        _connectorPortId = UUID.randomUUID();
        _keyStoreId = UUID.randomUUID();

        _brokerOriginalChildren = new HashSet<UUID>(Arrays.asList(_amqpPortId, _amqpSslPortId, _amqpSslPortId, _registryPortId,
                _connectorPortId, _keyStoreId));

        ConfigurationEntry broker = new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(),
                new HashMap<String, Object>(), _brokerOriginalChildren, _originalStore);
        _originalStoreEntries.put(_brokerId, broker);

        HashMap<String, Object> amqpPortAttributes = new HashMap<String, Object>();
        amqpPortAttributes.put(Port.NAME, "myAmqpPort");
        amqpPortAttributes.put(Port.PORT, "1");
        amqpPortAttributes.put(Port.TRANSPORTS, new HashSet<String>(Arrays.asList(Transport.TCP.name())));
        _amqpPortProtocols = new HashSet<String>(Arrays.asList(Protocol.AMQP_1_0.name(), Protocol.AMQP_0_10.name(),
                Protocol.AMQP_0_9_1.name()));
        amqpPortAttributes.put(Port.PROTOCOLS, _amqpPortProtocols);
        amqpPortAttributes.put(Port.RECEIVE_BUFFER_SIZE, "5");
        ConfigurationEntry amqpPort = new ConfigurationEntry(_amqpPortId, Port.class.getSimpleName(), amqpPortAttributes,
                Collections.<UUID> emptySet(), _originalStore);
        _originalStoreEntries.put(_amqpPortId, amqpPort);

        HashMap<String, Object> amqpSslPortAttributes = new HashMap<String, Object>();
        amqpSslPortAttributes.put(Port.NAME, "myAmqpSslPort");
        amqpSslPortAttributes.put(Port.PORT, "2");
        amqpSslPortAttributes.put(Port.TRANSPORTS, new HashSet<String>(Arrays.asList(Transport.SSL.name())));
        HashSet<String> amqpSsslPortProtocols = new HashSet<String>(Arrays.asList(Protocol.AMQP_0_8.name(),
                Protocol.AMQP_0_9.name(), Protocol.AMQP_0_9_1.name()));
        amqpSslPortAttributes.put(Port.PROTOCOLS, amqpSsslPortProtocols);
        amqpSslPortAttributes.put(Port.SEND_BUFFER_SIZE, "6");
        amqpSslPortAttributes.put("KEY_STORE", "myKeyStore");
        ConfigurationEntry amqpSslPort = new ConfigurationEntry(_amqpSslPortId, Port.class.getSimpleName(),
                amqpSslPortAttributes, Collections.<UUID> emptySet(), _originalStore);
        _originalStoreEntries.put(_amqpSslPortId, amqpSslPort);

        HashMap<String, Object> registryPortAttributes = new HashMap<String, Object>();
        registryPortAttributes.put(Port.NAME, "myRegistryPort");
        registryPortAttributes.put(Port.PORT, "3");
        registryPortAttributes.put(Port.TRANSPORTS, new HashSet<String>(Arrays.asList(Transport.TCP.name())));
        registryPortAttributes.put(Port.PROTOCOLS, new HashSet<String>(Arrays.asList(Protocol.RMI.name())));
        ConfigurationEntry registryPort = new ConfigurationEntry(_registryPortId, Port.class.getSimpleName(),
                registryPortAttributes, Collections.<UUID> emptySet(), _originalStore);
        _originalStoreEntries.put(_registryPortId, registryPort);

        HashMap<String, Object> connectorPortAttributes = new HashMap<String, Object>();
        connectorPortAttributes.put(Port.NAME, "myConnectorPort");
        connectorPortAttributes.put(Port.PORT, "4");
        connectorPortAttributes.put(Port.TRANSPORTS, new HashSet<String>(Arrays.asList(Transport.TCP.name())));
        connectorPortAttributes.put(Port.PROTOCOLS, new HashSet<String>(Arrays.asList(Protocol.JMX_RMI.name())));
        connectorPortAttributes.put("KEY_STORE", "myKeyStore");
        ConfigurationEntry connectorPort = new ConfigurationEntry(_connectorPortId, Port.class.getSimpleName(),
                connectorPortAttributes, Collections.<UUID> emptySet(), _originalStore);
        _originalStoreEntries.put(_connectorPortId, connectorPort);

        HashMap<String, Object> keyStoreAttributes = new HashMap<String, Object>();
        keyStoreAttributes.put(KeyStore.NAME, "myKeyStore");
        keyStoreAttributes.put(KeyStore.PATH, "path/to/file");
        keyStoreAttributes.put(KeyStore.PASSWORD, "secret");
        ConfigurationEntry keyStore = new ConfigurationEntry(_keyStoreId, KeyStore.class.getSimpleName(), keyStoreAttributes,
                Collections.<UUID> emptySet(), _originalStore);
        _originalStoreEntries.put(_keyStoreId, keyStore);

        when(_originalStore.getRootEntry()).thenReturn(_originalStoreEntries.get(_brokerId));
        for (Map.Entry<UUID, ConfigurationEntry> entry : _originalStoreEntries.entrySet())
        {
            when(_originalStore.getEntry(entry.getKey())).thenReturn(entry.getValue());
        }
    }

    public void testBindAddressOverride()
    {
        BrokerOptions options = new BrokerOptions();
        options.setBind("127.0.0.1");
        Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
        assertEquals("Unexpected port number", 4, ports.size());

        ConfigurationEntry amqpPort = findById(_amqpPortId, ports);
        assertNotNull("Store amqp port is not found", amqpPort);

        ConfigurationEntry originalPortEntry = _originalStoreEntries.get(_amqpPortId);
        Map<String, Object> amqpPortAttributes = amqpPort.getAttributes();
        Map<String, Object> originalAmqpPortAttributes = originalPortEntry.getAttributes();
        assertEquals("Unexpected amqp port", "1", amqpPortAttributes.get(Port.PORT));
        assertEquals("Unexpected amqp port binding address", "127.0.0.1", amqpPortAttributes.get(Port.BINDING_ADDRESS));
        assertEquals("Unexpected amqp port transports", originalAmqpPortAttributes.get(Port.NAME),
                amqpPortAttributes.get(Port.NAME));
        assertEquals("Unexpected amqp port protocols", originalAmqpPortAttributes.get(Port.PROTOCOLS),
                amqpPortAttributes.get(Port.PROTOCOLS));
        assertEquals("Unexpected amqp port send buffer size", originalAmqpPortAttributes.get(Port.SEND_BUFFER_SIZE),
                amqpPortAttributes.get(Port.SEND_BUFFER_SIZE));
        assertEquals("Unexpected amqp port receive buffer size", originalAmqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE),
                amqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE));

        ConfigurationEntry amqpSslPort = findById(_amqpSslPortId, ports);
        assertNotNull("Store amqp ssl port is not found", amqpSslPort);

        ConfigurationEntry originalSslPortEntry = _originalStoreEntries.get(_amqpSslPortId);
        Map<String, Object> amqpSslPortAttributes = amqpSslPort.getAttributes();
        Map<String, Object> originalSslPortAttributes = originalSslPortEntry.getAttributes();
        assertEquals("Unexpected amqp port", "2", amqpSslPortAttributes.get(Port.PORT));
        assertEquals("Unexpected amqp port binding address", "127.0.0.1", amqpSslPortAttributes.get(Port.BINDING_ADDRESS));
        assertEquals("Unexpected amqp port name", originalSslPortAttributes.get(Port.NAME), amqpSslPortAttributes.get(Port.NAME));
        assertEquals("Unexpected amqp port transports", originalSslPortAttributes.get(Port.TRANSPORTS),
                amqpSslPortAttributes.get(Port.TRANSPORTS));
        assertEquals("Unexpected amqp port protocols", originalSslPortAttributes.get(Port.PROTOCOLS),
                amqpSslPortAttributes.get(Port.PROTOCOLS));
        assertEquals("Unexpected amqp port send buffer size", originalSslPortAttributes.get(Port.SEND_BUFFER_SIZE),
                amqpSslPortAttributes.get(Port.SEND_BUFFER_SIZE));
        assertEquals("Unexpected amqp port receive buffer size", originalSslPortAttributes.get(Port.RECEIVE_BUFFER_SIZE),
                amqpSslPortAttributes.get(Port.RECEIVE_BUFFER_SIZE));

        ConfigurationEntry connectorPort = findById(_connectorPortId, ports);
        assertNotNull("Store connector port is not found", connectorPort);
        ConfigurationEntry originalConnectorEntry = _originalStoreEntries.get(_connectorPortId);
        Map<String, Object> connectorPortAttributes = connectorPort.getAttributes();
        Map<String, Object> originalConnectorPortAttributes = originalConnectorEntry.getAttributes();
        assertEquals("Unexpected connector port attributes", originalConnectorPortAttributes, connectorPortAttributes);

        ConfigurationEntry registryPort = findById(_registryPortId, ports);
        assertNotNull("Store registry port is not found", registryPort);
        ConfigurationEntry originalRegistryEntry = _originalStoreEntries.get(_registryPortId);
        Map<String, Object> registryPortAttributes = registryPort.getAttributes();
        Map<String, Object> originalRegistryPortAttributes = originalRegistryEntry.getAttributes();
        assertEquals("Unexpected registry port attributes", originalRegistryPortAttributes, registryPortAttributes);
    }

    public void testPortProtocolExcludeOverride()
    {
        Set<ProtocolExclusion> exclusions = EnumSet.allOf(ProtocolExclusion.class);
        for (ProtocolExclusion protocolExclusion : exclusions)
        {
            BrokerOptions options = new BrokerOptions();
            options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
            options.addExcludedPort(protocolExclusion, 1);

            Set<Protocol> protocols = getExpectedProtocols(protocolExclusion);

            Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
            assertEquals("Unexpected port number", 4, ports.size());

            ConfigurationEntry amqpPort = findById(_amqpPortId, ports);
            assertNotNull("Store amqp port is not found", amqpPort);

            ConfigurationEntry originalPortEntry = _originalStoreEntries.get(_amqpPortId);
            Map<String, Object> amqpPortAttributes = amqpPort.getAttributes();
            Map<String, Object> originalAmqpPortAttributes = originalPortEntry.getAttributes();
            assertEquals("Unexpected amqp port", "1", amqpPortAttributes.get(Port.PORT));
            assertEquals("Unexpected amqp port binding address", null, amqpPortAttributes.get(Port.BINDING_ADDRESS));
            assertEquals("Unexpected amqp port transports", originalAmqpPortAttributes.get(Port.NAME),
                    amqpPortAttributes.get(Port.NAME));

            assertEquals("Unexpected amqp port protocols when excluded " + protocolExclusion, protocols,
                    MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, amqpPortAttributes, Protocol.class));
            assertEquals("Unexpected amqp port send buffer size", originalAmqpPortAttributes.get(Port.SEND_BUFFER_SIZE),
                    amqpPortAttributes.get(Port.SEND_BUFFER_SIZE));
            assertEquals("Unexpected amqp port receive buffer size", originalAmqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE),
                    amqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE));

            ConfigurationEntry amqpSslPort = findById(_amqpSslPortId, ports);
            assertNotNull("Store amqp ssl port is not found", amqpSslPort);

            ConfigurationEntry originalSslPortEntry = _originalStoreEntries.get(_amqpSslPortId);
            Map<String, Object> amqpSslPortAttributes = amqpSslPort.getAttributes();
            Map<String, Object> originalSslPortAttributes = new HashMap<String, Object>(originalSslPortEntry.getAttributes());
            originalSslPortAttributes.put(Port.BINDING_ADDRESS, null);
            assertEquals("Unexpected amqp ssl port attributes", originalSslPortAttributes, amqpSslPortAttributes);

            assertPortsUnchanged(ports, _connectorPortId, _registryPortId);
        }
    }

    public void testPortProtocolIncludeOverride()
    {
        Set<ProtocolInclusion> inclusions = EnumSet.allOf(ProtocolInclusion.class);
        for (ProtocolInclusion protocolInclusion : inclusions)
        {
            BrokerOptions options = new BrokerOptions();
            options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
            options.addIncludedPort(protocolInclusion, 1);

            Set<Protocol> protocols = getExpectedProtocols(protocolInclusion);

            Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
            assertEquals("Unexpected port number", 4, ports.size());

            ConfigurationEntry amqpPort = findById(_amqpPortId, ports);
            assertNotNull("Store amqp port is not found", amqpPort);

            ConfigurationEntry originalPortEntry = _originalStoreEntries.get(_amqpPortId);
            Map<String, Object> amqpPortAttributes = amqpPort.getAttributes();
            Map<String, Object> originalAmqpPortAttributes = originalPortEntry.getAttributes();
            assertEquals("Unexpected amqp port", "1", amqpPortAttributes.get(Port.PORT));
            assertEquals("Unexpected amqp port binding address", null, amqpPortAttributes.get(Port.BINDING_ADDRESS));
            assertEquals("Unexpected amqp port transports", originalAmqpPortAttributes.get(Port.NAME),
                    amqpPortAttributes.get(Port.NAME));

            assertEquals("Unexpected amqp port protocols when included " + protocolInclusion, protocols,
                    MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, amqpPortAttributes, Protocol.class));
            assertEquals("Unexpected amqp port send buffer size", originalAmqpPortAttributes.get(Port.SEND_BUFFER_SIZE),
                    amqpPortAttributes.get(Port.SEND_BUFFER_SIZE));
            assertEquals("Unexpected amqp port receive buffer size", originalAmqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE),
                    amqpPortAttributes.get(Port.RECEIVE_BUFFER_SIZE));

            ConfigurationEntry amqpSslPort = findById(_amqpSslPortId, ports);
            assertNotNull("Store amqp ssl port is not found", amqpSslPort);

            ConfigurationEntry originalSslPortEntry = _originalStoreEntries.get(_amqpSslPortId);
            Map<String, Object> amqpSslPortAttributes = amqpSslPort.getAttributes();
            Map<String, Object> originalSslPortAttributes = new HashMap<String, Object>(originalSslPortEntry.getAttributes());
            originalSslPortAttributes.put(Port.BINDING_ADDRESS, null);
            assertEquals("Unexpected amqp ssl port attributes", originalSslPortAttributes, amqpSslPortAttributes);

            assertPortsUnchanged(ports, _connectorPortId, _registryPortId);
        }
    }

    public void testJMXConnectorPortOverride()
    {
        BrokerOptions options = new BrokerOptions();
        options.setJmxPortConnectorServer(40);

        Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
        assertEquals("Unexpected port number", 4, ports.size());

        ConfigurationEntry oldConnectorPort = findById(_connectorPortId, ports);
        assertNull("Store connector port is found", oldConnectorPort);

        Collection<ConfigurationEntry> connectorPorts = findByProtocol(Protocol.JMX_RMI, ports);
        assertEquals("Unexpected number of connector ports", 1, connectorPorts.size());
        ConfigurationEntry connectorPort = connectorPorts.iterator().next();
        assertNotNull("CLI connector port is not found", connectorPort);
        Map<String, Object> connectorPortAttributes = connectorPort.getAttributes();
        assertEquals("Unexpected connector port value", 40, connectorPortAttributes.get(Port.PORT));
        assertEquals("Unexpected connector port name", "cliJmxPort40", connectorPortAttributes.get(Port.NAME));
        assertEquals("Unexpected connector port transports", Collections.singleton(Transport.TCP),
                MapValueConverter.getEnumSetAttribute(Port.TRANSPORTS, connectorPortAttributes, Transport.class));
        assertEquals("Unexpected connector port protocols", Collections.singleton(Protocol.JMX_RMI),
                MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, connectorPortAttributes, Protocol.class));

        assertPortsUnchanged(ports, _amqpPortId, _amqpSslPortId, _registryPortId);
    }

    public void testJMXRegistryPortOverride()
    {
        BrokerOptions options = new BrokerOptions();
        options.setJmxPortRegistryServer(30);

        Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
        assertEquals("Unexpected port number", 4, ports.size());

        ConfigurationEntry oldRegistryPort = findById(_registryPortId, ports);
        assertNull("Store registry port is found", oldRegistryPort);

        Collection<ConfigurationEntry> registryPorts = findByProtocol(Protocol.RMI, ports);
        assertEquals("Unexpected number of registry ports", 1, registryPorts.size());
        ConfigurationEntry registryPort = registryPorts.iterator().next();
        assertNotNull("CLI connector port is not found", registryPort);
        Map<String, Object> registryPortAttributes = registryPort.getAttributes();
        assertEquals("Unexpected connector port value", 30, registryPortAttributes.get(Port.PORT));
        assertEquals("Unexpected connector port name", "cliJmxPort30", registryPortAttributes.get(Port.NAME));
        assertEquals("Unexpected connector port transports", Collections.singleton(Transport.TCP),
                MapValueConverter.getEnumSetAttribute(Port.TRANSPORTS, registryPortAttributes, Transport.class));
        assertEquals("Unexpected connector port protocols", Collections.singleton(Protocol.RMI),
                MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, registryPortAttributes, Protocol.class));

        assertPortsUnchanged(ports, _amqpPortId, _amqpSslPortId, _connectorPortId);
    }

    public void testAmqpPortOverride()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
        assertEquals("Unexpected port number", 4, ports.size());

        ConfigurationEntry storeAmqpPort = findById(_amqpPortId, ports);
        assertNull("Store amqp port is found", storeAmqpPort);

        UUID cliPortId = UUIDGenerator.generateBrokerChildUUID(Port.class.getSimpleName(), "cliAmqpPort10");
        ConfigurationEntry amqpPort = findById(cliPortId, ports);
        assertNotNull("CLI amqp port is not found", amqpPort);
        Map<String, Object> amqpPortAttributes = amqpPort.getAttributes();
        assertEquals("Unexpected amqp port", 10, amqpPortAttributes.get(Port.PORT));
        assertEquals("Unexpected amqp port binding address", null, amqpPortAttributes.get(Port.BINDING_ADDRESS));
        assertEquals("Unexpected amqp port name", "cliAmqpPort10", amqpPortAttributes.get(Port.NAME));
        assertEquals("Unexpected amqp port protocols",
                EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1, Protocol.AMQP_0_10, Protocol.AMQP_1_0),
                amqpPortAttributes.get(Port.PROTOCOLS));
        assertEquals("Unexpected amqp port transports", EnumSet.of(Transport.TCP), amqpPortAttributes.get(Port.TRANSPORTS));

        assertPortsUnchanged(ports, _amqpSslPortId, _registryPortId, _connectorPortId);
    }

    public void testAmqpSslPortOverride()
    {
        BrokerOptions options = new BrokerOptions();
        options.addSSLPort(20);

        Collection<ConfigurationEntry> ports = createStoreAndGetPorts(options);
        assertEquals("Unexpected port number", 4, ports.size());

        ConfigurationEntry storeAmqpSslPort = findById(_amqpSslPortId, ports);
        assertNull("Store amqp port is  found", storeAmqpSslPort);

        UUID cliPortId = UUIDGenerator.generateBrokerChildUUID(Port.class.getSimpleName(), "cliAmqpPort20");
        ConfigurationEntry amqpSslPort = findById(cliPortId, ports);
        assertNotNull("CLI amqp ssl port is not found", amqpSslPort);
        Map<String, Object> amqpSslPortAttributes = amqpSslPort.getAttributes();
        assertEquals("Unexpected amqp ssl port value", 20, amqpSslPortAttributes.get(Port.PORT));
        assertEquals("Unexpected amqp ssl port binding address", null, amqpSslPortAttributes.get(Port.BINDING_ADDRESS));
        assertEquals("Unexpected amqp ssl port name", "cliAmqpPort20", amqpSslPortAttributes.get(Port.NAME));
        assertEquals("Unexpected amqp ssl port protocols",
                EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1, Protocol.AMQP_0_10, Protocol.AMQP_1_0),
                amqpSslPortAttributes.get(Port.PROTOCOLS));
        assertEquals("Unexpected amqp port transports", EnumSet.of(Transport.SSL), amqpSslPortAttributes.get(Port.TRANSPORTS));

        assertPortsUnchanged(ports, _amqpPortId, _registryPortId, _connectorPortId);
    }

    public void testCommandLineOptionsHandlerReturnsOriginalStoreRootOnEmptyCommandLineOptions()
    {
        BrokerOptions options = new BrokerOptions();
        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);
        ConfigurationEntry root = cloStore.getRootEntry();
        ConfigurationEntry originalRoot = _originalStore.getRootEntry();
        assertEquals("Unexpected root entry", originalRoot, root);
    }

    public void testGetEntryReturnsCLIEntry()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);
        ConfigurationEntry root = cloStore.getRootEntry();
        assertEquals("Unexpected root id", _brokerId, root.getId());
        Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
        Collection<ConfigurationEntry> ports = children.get(Port.class.getSimpleName());

        assertEquals("Unexpected port number", 4, ports.size());

        for (ConfigurationEntry configurationEntry : ports)
        {
            ConfigurationEntry entry = cloStore.getEntry(configurationEntry.getId());
            assertEquals(configurationEntry, entry);
        }
    }

    public void testSaveNotCLIEntry()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);

        ConfigurationEntry keyStore = cloStore.getEntry(_keyStoreId);

        Map<String, Object> attributes = new HashMap<String, Object>(keyStore.getAttributes());
        attributes.put(KeyStore.PATH, "path/to/new/file");
        ConfigurationEntry newKeyStore = new ConfigurationEntry(keyStore.getId(), keyStore.getType(), attributes,
                keyStore.getChildrenIds(), keyStore.getStore());

        cloStore.save(newKeyStore);

        verify(_originalStore).save(newKeyStore);
    }

    public void testRemoveNotCLIEntry()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);

        cloStore.remove(_keyStoreId);

        verify(_originalStore).remove(_keyStoreId);
    }

    public void testSaveCLIEntry()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);

        UUID cliPortId = UUIDGenerator.generateBrokerChildUUID(Port.class.getSimpleName(), "cliAmqpPort10");

        ConfigurationEntry portEntry = cloStore.getEntry(cliPortId);

        Map<String, Object> attributes = new HashMap<String, Object>(portEntry.getAttributes());
        attributes.put(Port.RECEIVE_BUFFER_SIZE, 1000);
        ConfigurationEntry newPortEntry = new ConfigurationEntry(portEntry.getId(), portEntry.getType(), attributes,
                portEntry.getChildrenIds(), portEntry.getStore());
        cloStore.save(newPortEntry);

        portEntry = cloStore.getEntry(cliPortId);
        assertEquals("Unexpected port entry", newPortEntry, portEntry);

        verify(_originalStore, never()).save(newPortEntry);
    }

    public void testRemoveCLIEntry()
    {
        BrokerOptions options = new BrokerOptions();
        options.addPort(10);

        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);

        UUID cliPortId = UUIDGenerator.generateBrokerChildUUID(Port.class.getSimpleName(), "cliAmqpPort10");

        cloStore.remove(cliPortId);

        ConfigurationEntry portEntry = cloStore.getEntry(cliPortId);
        assertNull("Entry has not been deleted", portEntry);
        verify(_originalStore, never()).remove(_keyStoreId);
    }

    private void assertPortsUnchanged(Collection<ConfigurationEntry> allStorePorts, UUID... portIDs)
    {
        for (UUID portId : portIDs)
        {
            ConfigurationEntry registryPort = findById(portId, allStorePorts);
            assertNotNull("Store port is not found", registryPort);
            ConfigurationEntry originalPortEntry = _originalStoreEntries.get(portId);
            Map<String, Object> storePortAttributes = registryPort.getAttributes();
            Map<String, Object> originalPortAttributes = originalPortEntry.getAttributes();
            assertEquals("Unexpected port attributes", originalPortAttributes, storePortAttributes);
        }
    }

    private ConfigurationEntry findById(UUID id, Collection<ConfigurationEntry> ports)
    {
        for (ConfigurationEntry entry : ports)
        {
            if (id.equals(entry.getId()))
            {
                return entry;
            }
        }
        return null;
    }

    private Collection<ConfigurationEntry> createStoreAndGetPorts(BrokerOptions options)
    {
        ConfigurationEntryStore cloStore = new CommandLineOptionsHandler(options, _originalStore);
        ConfigurationEntry root = cloStore.getRootEntry();
        assertEquals("Unexpected root id", _brokerId, root.getId());
        Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
        Collection<ConfigurationEntry> ports = children.get(Port.class.getSimpleName());
        return ports;
    }

    private Collection<ConfigurationEntry> findByProtocol(Protocol protocol, Collection<ConfigurationEntry> ports)
    {
        List<ConfigurationEntry> foundEntries = new ArrayList<ConfigurationEntry>();
        for (ConfigurationEntry configurationEntry : ports)
        {
            Map<String, Object> attributes = configurationEntry.getAttributes();
            Set<Protocol> protocols = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
            if (protocols.contains(protocol))
            {
                foundEntries.add(configurationEntry);
            }
        }
        return foundEntries;
    }

    private Set<Protocol> getExpectedProtocols(ProtocolExclusion protocolExclusion)
    {
        String excludedProtocol = protocolExclusiontoProtocolName(protocolExclusion);
        Set<Protocol> protocols = new HashSet<Protocol>();
        for (String protocolName : _amqpPortProtocols)
        {
            if (!protocolName.equals(excludedProtocol))
            {
                protocols.add(Protocol.valueOf(protocolName));
            }
        }
        return protocols;
    }

    private Set<Protocol> getExpectedProtocols(ProtocolInclusion protocolInclusion)
    {
        String includeProtocol = protocolInclusionToProtocolName(protocolInclusion);
        Set<Protocol> protocols = new HashSet<Protocol>();
        for (String protocolName : _amqpPortProtocols)
        {
            protocols.add(Protocol.valueOf(protocolName));
        }
        protocols.add(Protocol.valueOf(includeProtocol));
        return protocols;
    }

    private String protocolInclusionToProtocolName(ProtocolInclusion protocolInclusion)
    {
        switch (protocolInclusion)
        {
        case v0_10:
            return Protocol.AMQP_0_10.name();
        case v0_9_1:
            return Protocol.AMQP_0_9_1.name();
        case v0_9:
            return Protocol.AMQP_0_9.name();
        case v0_8:
            return Protocol.AMQP_0_8.name();
        case v1_0:
            return Protocol.AMQP_1_0.name();
        default:
            throw new IllegalArgumentException("Unsupported inclusion " + protocolInclusion);
        }
    }

    private String protocolExclusiontoProtocolName(ProtocolExclusion protocolExclusion)
    {
        switch (protocolExclusion)
        {
        case v0_10:
            return Protocol.AMQP_0_10.name();
        case v0_9_1:
            return Protocol.AMQP_0_9_1.name();
        case v0_9:
            return Protocol.AMQP_0_9.name();
        case v0_8:
            return Protocol.AMQP_0_8.name();
        case v1_0:
            return Protocol.AMQP_1_0.name();
        default:
            throw new IllegalArgumentException("Unsupported exclusion " + protocolExclusion);
        }
    }

}
