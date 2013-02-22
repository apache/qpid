package org.apache.qpid.server.configuration.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerConfigurationStoreCreator;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class ManagementModeStoreHandlerTest extends QpidTestCase
{
    private ManagementModeStoreHandler _handler;
    private BrokerOptions _options;
    private ConfigurationEntryStore _store;
    private ConfigurationEntry _root;
    private ConfigurationEntry _portEntry;
    private UUID _rootId, _portEntryId;

    protected void setUp() throws Exception
    {
        super.setUp();
        _rootId = UUID.randomUUID();
        _portEntryId = UUID.randomUUID();
        _store = mock(ConfigurationEntryStore.class);
        _root = mock(ConfigurationEntry.class);
        _portEntry = mock(ConfigurationEntry.class);
        when(_store.getRootEntry()).thenReturn(_root);
        when(_root.getId()).thenReturn(_rootId);
        when(_portEntry.getId()).thenReturn(_portEntryId);
        when(_store.getEntry(_portEntryId)).thenReturn(_portEntry);
        when(_store.getEntry(_rootId)).thenReturn(_root);
        when(_root.getChildrenIds()).thenReturn(Collections.singleton(_portEntryId));
        when(_portEntry.getType()).thenReturn(Port.class.getSimpleName());
        _options = new BrokerOptions();
        _handler = new ManagementModeStoreHandler(_store, _options);
    }

    public void testOpenString()
    {
        try
        {
            _handler.open(TMP_FOLDER + File.separator + getTestName());
            fail("Exception should be thrown on attempt to call open method on a handler");
        }
        catch (IllegalStateException e)
        {
            // pass
        }
    }

    public void testOpenStringString()
    {
        try
        {
            _handler.open(TMP_FOLDER + File.separator + getTestName(),
                    BrokerConfigurationStoreCreator.DEFAULT_INITIAL_STORE_LOCATION);
            fail("Exception should be thrown on attempt to call open method on a handler");
        }
        catch (IllegalStateException e)
        {
            // pass
        }
    }

    public void testOpenStringConfigurationEntryStore()
    {
        try
        {
            _handler.open(TMP_FOLDER + File.separator + getTestName(), _store);
            fail("Exception should be thrown on attempt to call open method on a handler");
        }
        catch (IllegalStateException e)
        {
            // pass
        }
    }

    public void testGetRootEntryWithEmptyOptions()
    {
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        assertEquals("Unexpected children", Collections.singleton(_portEntryId), root.getChildrenIds());
    }

    public void testGetRootEntryWithHttpPortOverriden()
    {
        _options.setManagementModeHttpPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = root.getChildrenIds();
        assertEquals("Unexpected children size", 2, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithRmiPortOverriden()
    {
        _options.setManagementModeRmiPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = root.getChildrenIds();
        assertEquals("Unexpected children size", 3, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithConnectorPortOverriden()
    {
        _options.setManagementModeConnectorPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = root.getChildrenIds();
        assertEquals("Unexpected children size", 2, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetRootEntryWithManagementPortsOverriden()
    {
        _options.setManagementModeHttpPort(1000);
        _options.setManagementModeRmiPort(2000);
        _options.setManagementModeConnectorPort(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = root.getChildrenIds();
        assertEquals("Unexpected children size", 4, childrenIds.size());
        assertTrue("Store port entry id is not found", childrenIds.contains(_portEntryId));
    }

    public void testGetEntryByRootId()
    {
        ConfigurationEntry root = _handler.getEntry(_rootId);
        assertEquals("Unexpected root id", _rootId, root.getId());
        assertEquals("Unexpected children", Collections.singleton(_portEntryId), root.getChildrenIds());
    }

    public void testGetEntryByPortId()
    {
        ConfigurationEntry portEntry = _handler.getEntry(_portEntryId);
        assertEquals("Unexpected entry id", _portEntryId, portEntry.getId());
        assertTrue("Unexpected children", portEntry.getChildrenIds().isEmpty());
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testGetEntryByCLIConnectorPortId()
    {
        _options.setManagementModeConnectorPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);

        UUID optionsPort = getOptionsPortId();
        ConfigurationEntry portEntry = _handler.getEntry(optionsPort);
        assertCLIPortEntry(portEntry, optionsPort, Protocol.JMX_RMI);
    }

    public void testGetEntryByCLIHttpPortId()
    {
        _options.setManagementModeHttpPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);

        UUID optionsPort = getOptionsPortId();
        ConfigurationEntry portEntry = _handler.getEntry(optionsPort);
        assertCLIPortEntry(portEntry, optionsPort, Protocol.HTTP);
    }

    public void testHttpPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.HTTP));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeHttpPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);

        ConfigurationEntry portEntry = _handler.getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testRmiPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.RMI));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeRmiPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);

        ConfigurationEntry portEntry = _handler.getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testConnectorPortEntryIsQuiesced()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.JMX_RMI));
        when(_portEntry.getAttributes()).thenReturn(attributes);
        _options.setManagementModeRmiPort(9090);
        _handler = new ManagementModeStoreHandler(_store, _options);

        ConfigurationEntry portEntry = _handler.getEntry(_portEntryId);
        assertEquals("Unexpected state", State.QUIESCED, portEntry.getAttributes().get(Port.STATE));
    }

    public void testVirtualHostEntryIsQuiesced()
    {
        UUID virtualHostId = UUID.randomUUID();
        ConfigurationEntry virtualHost = mock(ConfigurationEntry.class);
        when(virtualHost.getId()).thenReturn(virtualHostId);
        when(virtualHost.getType()).thenReturn(VirtualHost.class.getSimpleName());
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.CONFIG_PATH, "/path/to/host.xml");
        when(virtualHost.getAttributes()).thenReturn(attributes);
        when(_store.getEntry(virtualHostId)).thenReturn(virtualHost);
        when(_root.getChildrenIds()).thenReturn(new HashSet<UUID>(Arrays.asList(_portEntryId, virtualHostId)));

        _handler = new ManagementModeStoreHandler(_store, _options);

        ConfigurationEntry hostEntry = _handler.getEntry(virtualHostId);
        Map<String, Object> hostAttributes = hostEntry.getAttributes();
        assertEquals("Unexpected state", State.QUIESCED, hostAttributes.get(VirtualHost.STATE));
        hostAttributes.remove(VirtualHost.STATE);
        assertEquals("Unexpected attributes", attributes, hostAttributes);
    }

    @SuppressWarnings("unchecked")
    private void assertCLIPortEntry(ConfigurationEntry portEntry, UUID optionsPort, Protocol protocol)
    {
        assertEquals("Unexpected entry id", optionsPort, portEntry.getId());
        assertTrue("Unexpected children", portEntry.getChildrenIds().isEmpty());
        Map<String, Object> attributes = portEntry.getAttributes();
        assertEquals("Unexpected name", "MANAGEMENT-MODE-PORT-" + protocol.name(), attributes.get(Port.NAME));
        assertEquals("Unexpected protocol", Collections.singleton(protocol), new HashSet<Protocol>(
                (Collection<Protocol>) attributes.get(Port.PROTOCOLS)));
    }

    public void testSavePort()
    {
        _options.setManagementModeHttpPort(1000);
        _options.setManagementModeRmiPort(2000);
        _options.setManagementModeConnectorPort(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfigurationEntry configurationEntry = new ConfigurationEntry(_portEntryId, Port.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), null);
        _handler.save(configurationEntry);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testSaveRoot()
    {
        _options.setManagementModeHttpPort(1000);
        _options.setManagementModeRmiPort(2000);
        _options.setManagementModeConnectorPort(3000);
        _handler = new ManagementModeStoreHandler(_store, _options);

        ConfigurationEntry root = _handler.getRootEntry();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Broker.NAME, "TEST");
        ConfigurationEntry configurationEntry = new ConfigurationEntry(_rootId, Broker.class.getSimpleName(), attributes,
                root.getChildrenIds(), null);
        _handler.save(configurationEntry);
        verify(_store).save(any(ConfigurationEntry.class));
    }

    public void testSaveCLIHttpPort()
    {
        _options.setManagementModeHttpPort(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);

        UUID portId = getOptionsPortId();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.NAME, "TEST");
        ConfigurationEntry configurationEntry = new ConfigurationEntry(portId, Port.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), null);
        try
        {
            _handler.save(configurationEntry);
            fail("Exception should be thrown on trying to save CLI port");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    public void testRemove()
    {
        _options.setManagementModeHttpPort(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);

        _handler.remove(_portEntryId);
        verify(_store).remove(_portEntryId);
    }

    public void testRemoveCLIPort()
    {
        _options.setManagementModeHttpPort(1000);
        _handler = new ManagementModeStoreHandler(_store, _options);
        UUID portId = getOptionsPortId();
        try
        {
            _handler.remove(portId);
            fail("Exception should be thrown on trying to remove CLI port");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }

    private UUID getOptionsPortId()
    {
        ConfigurationEntry root = _handler.getRootEntry();
        assertEquals("Unexpected root id", _rootId, root.getId());
        Collection<UUID> childrenIds = root.getChildrenIds();

        childrenIds.remove(_portEntryId);
        UUID optionsPort = childrenIds.iterator().next();
        return optionsPort;
    }

}
