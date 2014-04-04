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

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

public class JsonFileConfigStoreTest extends QpidTestCase
{
    private JsonFileConfigStore _store;
    private HashMap<String, Object> _configurationStoreSettings;
    private ConfiguredObject<?> _virtualHost;
    private File _storeLocation;
    private ConfiguredObjectRecordHandler _handler;


    private static final UUID ANY_UUID = UUID.randomUUID();
    private static final Map<String, Object> ANY_MAP = new HashMap<String, Object>();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _virtualHost = mock(ConfiguredObject.class);
        when(_virtualHost.getName()).thenReturn(getName());
        _storeLocation = TestFileUtils.createTestDirectory("json", true);
        _configurationStoreSettings = new HashMap<String, Object>();
        _configurationStoreSettings.put(JsonFileConfigStore.STORE_TYPE, JsonFileConfigStore.TYPE);
        _configurationStoreSettings.put(JsonFileConfigStore.STORE_PATH, _storeLocation.getAbsolutePath());
        _store = new JsonFileConfigStore();

        _handler = mock(ConfiguredObjectRecordHandler.class);
        when(_handler.handle(any(ConfiguredObjectRecord.class))).thenReturn(true);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            FileUtils.delete(_storeLocation, true);
        }
    }

    public void testNoStorePath() throws Exception
    {
        _configurationStoreSettings.put(JsonFileConfigStore.STORE_PATH, null);
        try
        {
            _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
            fail("Store should not successfully configure if there is no path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }


    public void testInvalidStorePath() throws Exception
    {
        _configurationStoreSettings.put(JsonFileConfigStore.STORE_PATH, System.getProperty("file.separator"));
        try
        {
            _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
            fail("Store should not successfully configure if there is an invalid path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }

    public void testVisitEmptyStore()
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        InOrder inorder = inOrder(_handler);
        inorder.verify(_handler).begin(eq(0));
        inorder.verify(_handler,never()).handle(any(ConfiguredObjectRecord.class));
        inorder.verify(_handler).end();
        _store.closeConfigurationStore();
    }

    public void testUpdatedConfigVersionIsRetained() throws Exception
    {
        final int NEW_CONFIG_VERSION = 42;
        when(_handler.end()).thenReturn(NEW_CONFIG_VERSION);

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        _store.closeConfigurationStore();

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        InOrder inorder = inOrder(_handler);

        // first time the config version should be the initial version - 0
        inorder.verify(_handler).begin(eq(0));

        // second time the config version should be the updated version
        inorder.verify(_handler).begin(eq(NEW_CONFIG_VERSION));

        _store.closeConfigurationStore();
    }

    public void testCreateObject() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        final Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));
        _store.closeConfigurationStore();

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);

        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler, times(1)).handle(matchesRecord(queueId, queueType, queueAttr));
        _store.closeConfigurationStore();
    }

    public void testCreateAndUpdateObject() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));


        queueAttr = new HashMap<String,Object>(queueAttr);
        queueAttr.put("owner", "theowner");
        _store.update(false, new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));

        _store.closeConfigurationStore();

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler, times(1)).handle(matchesRecord(queueId, queueType, queueAttr));
        _store.closeConfigurationStore();
    }


    public void testCreateAndRemoveObject() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        final ConfiguredObjectRecordImpl record = new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr);
        _store.create(record);


        _store.remove(record);

        _store.closeConfigurationStore();

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler, never()).handle(any(ConfiguredObjectRecord.class));
        _store.closeConfigurationStore();
    }

    public void testCreateUnknownObjectType() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        try
        {
            _store.create(new ConfiguredObjectRecordImpl(UUID.randomUUID(), "wibble", Collections.<String, Object>emptyMap()));
            fail("Should not be able to create instance of type wibble");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testTwoObjectsWithSameId() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID id = UUID.randomUUID();
        _store.create(new ConfiguredObjectRecordImpl(id, "Queue", Collections.<String, Object>emptyMap()));
        try
        {
            _store.create(new ConfiguredObjectRecordImpl(id, "Exchange", Collections.<String, Object>emptyMap()));
            fail("Should not be able to create two objects with same id");
        }
        catch (StoreException e)
        {
            // pass
        }
    }


    public void testChangeTypeOfObject() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID id = UUID.randomUUID();
        _store.create(new ConfiguredObjectRecordImpl(id, "Queue", Collections.<String, Object>emptyMap()));
        _store.closeConfigurationStore();
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);

        try
        {
            _store.update(false, new ConfiguredObjectRecordImpl(id, "Exchange", Collections.<String, Object>emptyMap()));
            fail("Should not be able to update object to different type");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testLockFileGuaranteesExclusiveAccess() throws Exception
    {
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);

        JsonFileConfigStore secondStore = new JsonFileConfigStore();

        try
        {
            secondStore.openConfigurationStore(_virtualHost, _configurationStoreSettings);
            fail("Should not be able to open a second store with the same path");
        }
        catch(ServerScopedRuntimeException e)
        {
            // pass
        }
        _store.closeConfigurationStore();
        secondStore.openConfigurationStore(_virtualHost, _configurationStoreSettings);


    }

    public void testCreatedNestedObjects() throws Exception
    {

        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        final UUID queueId = new UUID(0, 1);
        final UUID queue2Id = new UUID(1, 1);

        final Map<String, Object> EMPTY_ATTR = Collections.emptyMap();
        final UUID exchangeId = new UUID(0, 2);

        final UUID bindingId = new UUID(0, 3);
        final UUID binding2Id = new UUID(1, 3);

        final ConfiguredObjectRecordImpl queueRecord = new ConfiguredObjectRecordImpl(queueId, "Queue", EMPTY_ATTR);
        _store.create(queueRecord);
        final ConfiguredObjectRecordImpl queue2Record = new ConfiguredObjectRecordImpl(queue2Id, "Queue", EMPTY_ATTR);
        _store.create(queue2Record);
        final ConfiguredObjectRecordImpl exchangeRecord = new ConfiguredObjectRecordImpl(exchangeId, "Exchange", EMPTY_ATTR);
        _store.create(exchangeRecord);
        Map<String,ConfiguredObjectRecord> bindingParents = new HashMap<String, ConfiguredObjectRecord>();
        bindingParents.put("Exchange", exchangeRecord);
        bindingParents.put("Queue", queueRecord);
        final ConfiguredObjectRecordImpl bindingRecord =
                new ConfiguredObjectRecordImpl(bindingId, "Binding", EMPTY_ATTR, bindingParents);


        Map<String,ConfiguredObjectRecord> binding2Parents = new HashMap<String, ConfiguredObjectRecord>();
        binding2Parents.put("Exchange", exchangeRecord);
        binding2Parents.put("Queue", queue2Record);
        final ConfiguredObjectRecordImpl binding2Record =
                new ConfiguredObjectRecordImpl(binding2Id, "Binding", EMPTY_ATTR, binding2Parents);
        _store.update(true, bindingRecord, binding2Record);
        _store.closeConfigurationStore();
        _store.openConfigurationStore(_virtualHost, _configurationStoreSettings);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler).handle(matchesRecord(queueId, "Queue", EMPTY_ATTR));
        verify(_handler).handle(matchesRecord(queue2Id, "Queue", EMPTY_ATTR));
        verify(_handler).handle(matchesRecord(exchangeId, "Exchange", EMPTY_ATTR));
        verify(_handler).handle(matchesRecord(bindingId, "Binding", EMPTY_ATTR));
        verify(_handler).handle(matchesRecord(binding2Id, "Binding", EMPTY_ATTR));
        _store.closeConfigurationStore();

    }

    private ConfiguredObjectRecord matchesRecord(UUID id, String type, Map<String, Object> attributes)
    {
        return argThat(new ConfiguredObjectMatcher(id, type, attributes));
    }

    private static class ConfiguredObjectMatcher extends ArgumentMatcher<ConfiguredObjectRecord>
    {
        private final Map<String,Object> _matchingMap;
        private final UUID _id;
        private final String _name;

        private ConfiguredObjectMatcher(final UUID id, final String type, final Map<String, Object> matchingMap)
        {
            _id = id;
            _name = type;
            _matchingMap = matchingMap;
        }

        @Override
        public boolean matches(final Object argument)
        {
            if(argument instanceof ConfiguredObjectRecord)
            {
                ConfiguredObjectRecord binding = (ConfiguredObjectRecord) argument;

                Map<String,Object> arg = new HashMap<String, Object>(binding.getAttributes());
                arg.remove("createdBy");
                arg.remove("createdTime");
                return (_id == ANY_UUID || _id.equals(binding.getId()))
                       && _name.equals(binding.getType())
                       && (_matchingMap == ANY_MAP || arg.equals(_matchingMap));

            }
            return false;
        }
    }

}
