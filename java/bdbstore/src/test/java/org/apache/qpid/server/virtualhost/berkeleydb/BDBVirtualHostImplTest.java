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
package org.apache.qpid.server.virtualhost.berkeleydb;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class BDBVirtualHostImplTest extends QpidTestCase
{
    private File _storePath;
    private VirtualHostNode<?> _node;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        Broker broker = BrokerTestHelper.createBrokerMock();

        TaskExecutor taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        when(broker.getTaskExecutor()).thenReturn(taskExecutor);

        _storePath = TestFileUtils.createTestDirectory();

        _node = mock(VirtualHostNode.class);
        when(_node.getParent(Broker.class)).thenReturn(broker);
        when(_node.getModel()).thenReturn(BrokerModel.getInstance());
        when(_node.getTaskExecutor()).thenReturn(taskExecutor);
        when(_node.getConfigurationStore()).thenReturn(mock(DurableConfigurationStore.class));
        when(_node.getId()).thenReturn(UUID.randomUUID());
    }

    @Override
    public void  tearDown() throws Exception
    {
        try
        {
            if (_storePath != null)
            {
                FileUtils.delete(_storePath, true);
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testValidateOnCreateForInvalidStorePath() throws Exception
    {
        String hostName = getTestName();
        File file = new File(_storePath + File.separator + hostName);
        assertTrue("Empty file is not created", file.createNewFile());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(BDBVirtualHost.ID, UUID.randomUUID());
        attributes.put(BDBVirtualHost.TYPE, BDBVirtualHostImpl.VIRTUAL_HOST_TYPE);
        attributes.put(BDBVirtualHost.NAME, hostName);
        attributes.put(BDBVirtualHost.STORE_PATH, file.getAbsoluteFile());

        BDBVirtualHostImpl host = new BDBVirtualHostImpl(attributes, _node);
        try
        {
            host.create();
            fail("Cannot create DBD virtual host from existing empty file");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception " + e.getMessage(), e.getMessage().startsWith("Cannot open virtual host message store"));
        }
    }

}
