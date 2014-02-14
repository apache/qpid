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
import org.mockito.InOrder;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
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
        inorder.verify(_recoveryHandler,never()).configuredObject(any(UUID.class),anyString(),anyMap());
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

        _store.create(queueId, queueType, queueAttr);
        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(eq(queueId), eq(queueType), eq(queueAttr));
        _store.close();
    }

    public void testCreateAndUpdateObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(queueId, queueType, queueAttr);


        queueAttr = new HashMap<String,Object>(queueAttr);
        queueAttr.put("owner", "theowner");
        _store.update(queueId, queueType, queueAttr);

        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(eq(queueId), eq(queueType), eq(queueAttr));
        _store.close();
    }


    public void testCreateAndRemoveObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID queueId = new UUID(0, 1);
        final String queueType = Queue.class.getSimpleName();
        Map<String,Object> queueAttr = Collections.singletonMap("name", (Object) "q1");

        _store.create(queueId, queueType, queueAttr);


        _store.remove(queueId, queueType);

        _store.close();

        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler, never()).configuredObject(any(UUID.class), anyString(), anyMap());
        _store.close();
    }

    public void testCreateUnknownObjectType() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        try
        {
            _store.create(UUID.randomUUID(), "wibble", Collections.<String, Object>emptyMap());
            fail("Should not be able to create instance of type wibble");
        }
        catch (AMQStoreException e)
        {
            // pass
        }
    }

    public void testTwoObjectsWithSameId() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID id = UUID.randomUUID();
        _store.create(id, "Queue", Collections.<String, Object>emptyMap());
        try
        {
            _store.create(id, "Exchange", Collections.<String, Object>emptyMap());
            fail("Should not be able to create two objects with same id");
        }
        catch (AMQStoreException e)
        {
            // pass
        }
    }


    public void testChangeTypeOfObject() throws Exception
    {
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        final UUID id = UUID.randomUUID();
        _store.create(id, "Queue", Collections.<String, Object>emptyMap());
        _store.close();
        _store.configureConfigStore(_virtualHost, _recoveryHandler);

        try
        {
            _store.update(id, "Exchange", Collections.<String, Object>emptyMap());
            fail("Should not be able to update object to different type");
        }
        catch (AMQStoreException e)
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
        final Map<String, Object> bindingAttributes = new HashMap<String, Object>();
        bindingAttributes.put(Binding.EXCHANGE, exchangeId);
        bindingAttributes.put(Binding.QUEUE, queueId);
        final Map<String, Object> binding2Attributes = new HashMap<String, Object>();
        binding2Attributes.put(Binding.EXCHANGE, exchangeId);
        binding2Attributes.put(Binding.QUEUE, queue2Id);

        final UUID bindingId = new UUID(0, 3);
        final UUID binding2Id = new UUID(1, 3);

        _store.create(queueId, "Queue", EMPTY_ATTR);
        _store.create(queue2Id, "Queue", EMPTY_ATTR);
        _store.create(exchangeId, "Exchange", EMPTY_ATTR);
        _store.update(true,
                new ConfiguredObjectRecord(bindingId, "Binding", bindingAttributes),
                new ConfiguredObjectRecord(binding2Id, "Binding", binding2Attributes));
        _store.close();
        _store.configureConfigStore(_virtualHost, _recoveryHandler);
        verify(_recoveryHandler).configuredObject(eq(queueId), eq("Queue"), eq(EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(eq(queue2Id), eq("Queue"), eq(EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(eq(exchangeId), eq("Exchange"), eq(EMPTY_ATTR));
        verify(_recoveryHandler).configuredObject(eq(bindingId),eq("Binding"), eq(bindingAttributes));
        verify(_recoveryHandler).configuredObject(eq(binding2Id),eq("Binding"), eq(binding2Attributes));
        _store.close();

    }

}
