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

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JsonFileConfigStoreTest extends QpidTestCase
{
    private final ConfigurationRecoveryHandler _recoveryHandler = mock(ConfigurationRecoveryHandler.class);
    private VirtualHost _virtualHost;
    private JsonFileConfigStore _store;


    private static final UUID ANY_UUID = UUID.randomUUID();
    private static final Map<String, Object> ANY_MAP = new HashMap<String, Object>();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        removeStoreFile();
        _virtualHost = mock(VirtualHost.class);
        when(_virtualHost.getName()).thenReturn(getName());
        when(_virtualHost.getAttribute(VirtualHost.CONFIG_STORE_PATH)).thenReturn(TMP_FOLDER);
        _store = new JsonFileConfigStore();
    }

    @Override
    public void tearDown() throws Exception
    {
        removeStoreFile();
    }

    private void removeStoreFile()
    {
        File file = new File(TMP_FOLDER, getName() + ".json");
        if(file.exists())
        {
            file.delete();
        }
    }

    public void testNoStorePath() throws Exception
    {
        when(_virtualHost.getAttribute(VirtualHost.CONFIG_STORE_PATH)).thenReturn(null);
        try
        {
            _store.configureConfigStore(_virtualHost, _recoveryHandler);
            fail("Store should not successfully configure if there is no path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }


    public void testInvalidStorePath() throws Exception
    {
        when(_virtualHost.getAttribute(VirtualHost.CONFIG_STORE_PATH)).thenReturn(System.getProperty("file.separator"));
        try
        {
            _store.configureConfigStore(_virtualHost, _recoveryHandler);
            fail("Store should not successfully configure if there is an invalid path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }

    public void testStartFromNoStore() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        InOrder inorder = inOrder(_recoveryHandler);
        inorder.verify(_recoveryHandler).beginConfigurationRecovery(eq(_store), eq(0));
        inorder.verify(_recoveryHandler,never()).configuredObject(any(ConfiguredObjectRecord.class));
        inorder.verify(_recoveryHandler).completeConfigurationRecovery();
        _store.close();
    }

    public void testUpdatedConfigVersionIsRetained() throws Exception
    {
        final int NEW_CONFIG_VERSION = 42;
        when(_recoveryHandler.completeConfigurationRecovery()).thenReturn(NEW_CONFIG_VERSION);

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        InOrder inorder = inOrder(_recoveryHandler);

        // first time the config version should be the initial version - 0
        inorder.verify(_recoveryHandler).beginConfigurationRecovery(eq(_store), eq(0));

        // second time the config version should be the updated version
        inorder.verify(_recoveryHandler).beginConfigurationRecovery(eq(_store), eq(NEW_CONFIG_VERSION));

        _store.close();
    }

    public void testCreateObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        final Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));
        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(matchesRecord(queueId, queueType, queueAttr));
        _store.close();
    }

    public void testCreateAndUpdateObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));


        queueAttr = new HashMap<String,Object>(queueAttr);
        queueAttr.put("owner", "theowner");
        _store.update(false, new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr));

        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(matchesRecord(queueId, queueType, queueAttr));
        _store.close();
    }


    public void testCreateAndRemoveObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        final ConfiguredObjectRecordImpl record = new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr);
        _store.create(record);


        _store.remove(record);

        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler, never()).configuredObject(any(ConfiguredObjectRecord.class));
        _store.close();
    }

    public void testCreateUnknownObjectType() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
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
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
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
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID id = UUID.randomUUID();
        _store.create(new ConfiguredObjectRecordImpl(id, "Queue", Collections.<String, Object>emptyMap()));
        _store.close();
        _store.configureConfigStore(_virtualHost, _recoveryHandler);

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
        _store.configureConfigStore(_virtualHost, _recoveryHandler);

        JsonFileConfigStore secondStore = new JsonFileConfigStore();

        try
        {
            secondStore.configureConfigStore(_virtualHost, _recoveryHandler);
            fail("Should not be able to open a second store with the same path");
        }
        catch(ServerScopedRuntimeException e)
        {
            // pass
        }
        _store.close();
        secondStore.configureConfigStore(_virtualHost, _recoveryHandler);


    }

    public void testCreatedNestedObjects() throws Exception
    {

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
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
        _store.close();
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(matchesRecord(queueId, "Queue", EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(matchesRecord(queue2Id, "Queue", EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(matchesRecord(exchangeId, "Exchange", EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(matchesRecord(bindingId, "Binding", EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(matchesRecord(binding2Id, "Binding", EMPTY_ATTR));
        _store.close();

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
