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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class JsonFileConfigStoreTest extends QpidTestCase
{
    private JsonFileConfigStore _store;
    private JsonVirtualHostNode<?> _parent;
    private File _storeLocation;
    private ConfiguredObjectRecordHandler _handler;


    private static final UUID ANY_UUID = UUID.randomUUID();
    private static final Map<String, Object> ANY_MAP = new HashMap<String, Object>();
    private static final String VIRTUAL_HOST_TYPE = "VirtualHost";
    private ConfiguredObjectRecord _rootRecord;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        ConfiguredObjectFactory factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());

        _parent = mock(JsonVirtualHostNode.class);
        when(_parent.getName()).thenReturn(getName());
        when(_parent.getObjectFactory()).thenReturn(factory);
        when(_parent.getModel()).thenReturn(factory.getModel());
        _storeLocation = TestFileUtils.createTestDirectory("json", true);
        when(_parent.getStorePath()).thenReturn(_storeLocation.getAbsolutePath());

        _store = new JsonFileConfigStore(VirtualHost.class);

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
        when(_parent.getStorePath()).thenReturn(null);

        try
        {
            _store.openConfigurationStore(_parent, false);
            fail("Store should not successfully configure if there is no path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }


    public void testInvalidStorePath() throws Exception
    {
        when(_parent.getStorePath()).thenReturn(System.getProperty("file.separator"));
        try
        {
            _store.openConfigurationStore(_parent, false);
            fail("Store should not successfully configure if there is an invalid path set");
        }
        catch (ServerScopedRuntimeException e)
        {
            // pass
        }
    }

    public void testVisitEmptyStore()
    {
        _store.openConfigurationStore(_parent, false);
        _store.visitConfiguredObjectRecords(_handler);

        InOrder inorder = inOrder(_handler);
        inorder.verify(_handler).begin();
        inorder.verify(_handler,times(0)).handle(any(ConfiguredObjectRecord.class));
        inorder.verify(_handler).end();

        _store.closeConfigurationStore();
    }

    public void testInsertAndUpdateTopLevelObject() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();
        _store.closeConfigurationStore();

        _store.openConfigurationStore(_parent, false);
        Map<String, Object> newAttributes = new HashMap<String, Object>(_rootRecord.getAttributes());
        newAttributes.put("attributeName", "attributeValue");
        _store.update(false, new ConfiguredObjectRecordImpl(_rootRecord.getId(), _rootRecord.getType(), newAttributes));
        _store.closeConfigurationStore();

        _store.openConfigurationStore(_parent, false);

        _store.visitConfiguredObjectRecords(_handler);

        Map<String, Object> expectedAttributes = new HashMap<String, Object>(newAttributes);
        verify(_handler, times(1)).handle(matchesRecord(_rootRecord.getId(), _rootRecord.getType(), expectedAttributes));
        _store.closeConfigurationStore();
    }

    public void testCreateObject() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        final Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr, getRootAsParentMap()));
        _store.closeConfigurationStore();

        _store.openConfigurationStore(_parent, false);

        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler).handle(matchesRecord(queueId, queueType, queueAttr));
        verify(_handler).handle(matchesRecord(ANY_UUID, VIRTUAL_HOST_TYPE, ANY_MAP));
        _store.closeConfigurationStore();
    }

    public void testCreateAndUpdateObject() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr, getRootAsParentMap()));

        queueAttr = new HashMap<String,Object>(queueAttr);
        queueAttr.put("owner", "theowner");
        _store.update(false, new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr, getRootAsParentMap()));

        _store.closeConfigurationStore();

        _store.openConfigurationStore(_parent, false);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler, times(1)).handle(matchesRecord(queueId, queueType, queueAttr));
        _store.closeConfigurationStore();
    }


    public void testCreateAndRemoveObject() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        final ConfiguredObjectRecordImpl record = new ConfiguredObjectRecordImpl(queueId, queueType, queueAttr, getRootAsParentMap());
        _store.create(record);


        _store.remove(record);

        _store.closeConfigurationStore();

        _store.openConfigurationStore(_parent, false);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler, times(1)).handle(matchesRecord(ANY_UUID, VIRTUAL_HOST_TYPE, ANY_MAP));
        _store.closeConfigurationStore();
    }

    public void testCreateUnknownObjectType() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        try
        {
            _store.create(new ConfiguredObjectRecordImpl(UUID.randomUUID(), "wibble", Collections.<String, Object>emptyMap(), getRootAsParentMap()));
            fail("Should not be able to create instance of type wibble");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testTwoObjectsWithSameId() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID id = UUID.randomUUID();
        _store.create(new ConfiguredObjectRecordImpl(id, "Queue",
                                                     Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "queue"),
                                                     getRootAsParentMap()));
        try
        {
            _store.create(new ConfiguredObjectRecordImpl(id, "Exchange",
                                                         Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "exchange"),
                                                         getRootAsParentMap()));
            fail("Should not be able to create two objects with same id");
        }
        catch (StoreException e)
        {
            // pass
        }
    }


    public void testObjectWithoutName() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID id = UUID.randomUUID();
        try
        {
            _store.create(new ConfiguredObjectRecordImpl(id, "Exchange",
                                                         Collections.<String, Object>emptyMap(),
                                                         getRootAsParentMap()));
            fail("Should not be able to create an object without a name");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testObjectWithNonStringName() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID id = UUID.randomUUID();
        try
        {
            _store.update(true, new ConfiguredObjectRecordImpl(id, "Exchange",
                                                         Collections.<String, Object>singletonMap(ConfiguredObject.NAME, 3),
                                                         getRootAsParentMap()));
            fail("Should not be able to create an object without a name");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testChangeTypeOfObject() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID id = UUID.randomUUID();
        _store.create(new ConfiguredObjectRecordImpl(id, "Queue",
                                                     Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "queue"),
                                                     getRootAsParentMap()));
        _store.closeConfigurationStore();
        _store.openConfigurationStore(_parent, false);

        try
        {
            _store.update(false, new ConfiguredObjectRecordImpl(id, "Exchange",
                                                                Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "exchange"),
                                                                getRootAsParentMap()));
            fail("Should not be able to update object to different type");
        }
        catch (StoreException e)
        {
            // pass
        }
    }

    public void testLockFileGuaranteesExclusiveAccess() throws Exception
    {
        _store.openConfigurationStore(_parent, false);

        JsonFileConfigStore secondStore = new JsonFileConfigStore(VirtualHost.class);

        try
        {
            secondStore.openConfigurationStore(_parent, false);
            fail("Should not be able to open a second store with the same path");
        }
        catch(ServerScopedRuntimeException e)
        {
            // pass
        }
        _store.closeConfigurationStore();
        secondStore.openConfigurationStore(_parent, false);
    }

    public void testStoreFileLifecycle()
    {
        File expectedJsonFile = new File(_storeLocation, _parent.getName() + ".json");
        File expectedJsonFileBak = new File(_storeLocation, _parent.getName() + ".bak");

        assertFalse("JSON store should not exist", expectedJsonFile.exists());
        assertFalse("JSON backup should not exist", expectedJsonFileBak.exists());

        _store.openConfigurationStore(_parent, false);
        assertTrue("JSON store should exist after open", expectedJsonFile.exists());
        assertFalse("JSON backup should not exist after open", expectedJsonFileBak.exists());

        _store.closeConfigurationStore();
        assertTrue("JSON store should exist after close", expectedJsonFile.exists());

        _store.onDelete();
        assertFalse("JSON store should not exist after delete", expectedJsonFile.exists());

    }

    public void testCreatedNestedObjects() throws Exception
    {
        _store.openConfigurationStore(_parent, false);
        createRootRecord();

        final UUID queueId = new UUID(0, 1);
        final UUID queue2Id = new UUID(1, 1);

        final UUID exchangeId = new UUID(0, 2);

        final UUID bindingId = new UUID(0, 3);
        final UUID binding2Id = new UUID(1, 3);

        Map<String, UUID> parents = getRootAsParentMap();
        Map<String, Object> queueAttr = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "queue");
        final ConfiguredObjectRecordImpl queueRecord =
                new ConfiguredObjectRecordImpl(queueId, "Queue",
                                               queueAttr,
                                               parents);
        _store.create(queueRecord);
        Map<String, Object> queue2Attr = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "queue2");
        final ConfiguredObjectRecordImpl queue2Record =
                new ConfiguredObjectRecordImpl(queue2Id, "Queue",
                                               queue2Attr,
                                               parents);
        _store.create(queue2Record);
        Map<String, Object> exchangeAttr = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "exchange");
        final ConfiguredObjectRecordImpl exchangeRecord =
                new ConfiguredObjectRecordImpl(exchangeId, "Exchange",
                                               exchangeAttr,
                                               parents);
        _store.create(exchangeRecord);
        Map<String,UUID> bindingParents = new HashMap();
        bindingParents.put("Exchange", exchangeRecord.getId());
        bindingParents.put("Queue", queueRecord.getId());
        Map<String, Object> bindingAttr = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "binding");
        final ConfiguredObjectRecordImpl bindingRecord =
                new ConfiguredObjectRecordImpl(bindingId, "Binding",
                                               bindingAttr,
                                               bindingParents);


        Map<String,UUID> binding2Parents = new HashMap();
        binding2Parents.put("Exchange", exchangeRecord.getId());
        binding2Parents.put("Queue", queue2Record.getId());
        Map<String, Object> binding2Attr = Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "binding2");
        final ConfiguredObjectRecordImpl binding2Record =
                new ConfiguredObjectRecordImpl(binding2Id, "Binding",
                                               binding2Attr,
                                               binding2Parents);
        _store.update(true, bindingRecord, binding2Record);
        _store.closeConfigurationStore();
        _store.openConfigurationStore(_parent, false);
        _store.visitConfiguredObjectRecords(_handler);
        verify(_handler).handle(matchesRecord(queueId, "Queue", queueAttr));
        verify(_handler).handle(matchesRecord(queue2Id, "Queue", queue2Attr));
        verify(_handler).handle(matchesRecord(exchangeId, "Exchange", exchangeAttr));
        verify(_handler).handle(matchesRecord(bindingId, "Binding", bindingAttr));
        verify(_handler).handle(matchesRecord(binding2Id, "Binding", binding2Attr));
        _store.closeConfigurationStore();

    }


    private void createRootRecord()
    {
        UUID rootRecordId = UUID.randomUUID();
        _rootRecord =
                new ConfiguredObjectRecordImpl(rootRecordId,
                                               VIRTUAL_HOST_TYPE,
                                               Collections.<String, Object>singletonMap(ConfiguredObject.NAME, "root"));
        _store.create(_rootRecord);
    }

    private Map<String, UUID> getRootAsParentMap()
    {
        return Collections.singletonMap(VIRTUAL_HOST_TYPE, _rootRecord.getId());
    }

    private ConfiguredObjectRecord matchesRecord(UUID id, String type, Map<String, Object> attributes)
    {
        return argThat(new ConfiguredObjectMatcher(id, type, attributes));
    }


    private static class ConfiguredObjectMatcher extends ArgumentMatcher<ConfiguredObjectRecord>
    {
        private final Map<String,Object> _expectedAttributes;
        private final UUID _expectedId;
        private final String _expectedType;

        private ConfiguredObjectMatcher(final UUID id, final String type, final Map<String, Object> matchingMap)
        {
            _expectedId = id;
            _expectedType = type;
            _expectedAttributes = matchingMap;
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
                return (_expectedId == ANY_UUID || _expectedId.equals(binding.getId()))
                       && _expectedType.equals(binding.getType())
                       && (_expectedAttributes == ANY_MAP || arg.equals(_expectedAttributes));

            }
            return false;
        }
    }

}
