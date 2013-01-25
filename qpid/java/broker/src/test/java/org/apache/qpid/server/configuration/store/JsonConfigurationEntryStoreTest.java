package org.apache.qpid.server.configuration.store;

import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.TestFileUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class JsonConfigurationEntryStoreTest extends ConfigurationEntryStoreTestCase
{
    private File _storeFile;
    private ObjectMapper _objectMapper;

    public void tearDown() throws Exception
    {
        _storeFile.delete();
        super.tearDown();
    }

    @Override
    protected ConfigurationEntryStore createStore(UUID brokerId, Map<String, Object> brokerAttributes) throws Exception
    {
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);

        Map<String, Object> brokerObjectMap = new HashMap<String, Object>();
        brokerObjectMap.put(Broker.ID, brokerId);
        brokerObjectMap.put("@type", Broker.class.getSimpleName());
        brokerObjectMap.putAll(brokerAttributes);

        StringWriter sw = new StringWriter();
        _objectMapper.writeValue(sw, brokerObjectMap);

        String brokerJson = sw.toString();

        _storeFile = TestFileUtils.createTempFile(this, ".json", brokerJson);

        JsonConfigurationEntryStore store = new JsonConfigurationEntryStore();
        store.open(_storeFile.getAbsolutePath());
        return store;
    }

    @Override
    protected void addConfiguration(UUID id, String type, Map<String, Object> attributes)
    {
        ConfigurationEntryStore store = getStore();
        store.save(new ConfigurationEntry(id, type, attributes, Collections.<UUID> emptySet(), store));
    }

    public void testAttributeIsResolvedFromSystemProperties()
    {
        String aclLocation = "path/to/acl/" + getTestName();
        setTestSystemProperty("my.test.property", aclLocation);

        ConfigurationEntryStore store = getStore();
        ConfigurationEntry brokerConfigEntry = store.getRootEntry();
        Map<String, Object> attributes = new HashMap<String, Object>(brokerConfigEntry.getAttributes());
        attributes.put(Broker.ACL_FILE, "${my.test.property}");
        ConfigurationEntry updatedBrokerEntry = new ConfigurationEntry(brokerConfigEntry.getId(), Broker.class.getSimpleName(),
                attributes, brokerConfigEntry.getChildrenIds(), store);
        store.save(updatedBrokerEntry);

        JsonConfigurationEntryStore store2 = new JsonConfigurationEntryStore();
        store2.open(_storeFile.getAbsolutePath());

        assertEquals("Unresolved ACL value", aclLocation, store2.getRootEntry().getAttributes().get(Broker.ACL_FILE));
    }

}
