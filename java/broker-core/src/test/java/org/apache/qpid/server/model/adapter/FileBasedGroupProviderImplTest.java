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
package org.apache.qpid.server.model.adapter;

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
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class FileBasedGroupProviderImplTest extends QpidTestCase
{
    private TaskExecutor _taskExecutor;
    private Broker _broker;
    private File _groupFile;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();

        _broker = mock(Broker.class);
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getModel()).thenReturn(BrokerModel.getInstance());
        when(_broker.getId()).thenReturn(UUID.randomUUID());
        when(_broker.getSecurityManager()).thenReturn(new SecurityManager(_broker, false));
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_groupFile.exists())
            {
                _groupFile.delete();
            }
           _taskExecutor.stop();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testValidationOnCreateWithInvalidPath()
    {
        Map<String,Object> attributes = new HashMap<>();
        _groupFile = TestFileUtils.createTempFile(this, "groups");

        String groupsFile = _groupFile.getAbsolutePath() + File.separator + "groups";
        assertFalse("File should not exist", new File(groupsFile).exists());
        attributes.put(FileBasedGroupProvider.PATH, groupsFile);
        attributes.put(FileBasedGroupProvider.NAME, getTestName());

        FileBasedGroupProviderImpl groupsProvider = new FileBasedGroupProviderImpl(attributes, _broker);
        try
        {
            groupsProvider.create();
            fail("Exception is expected on validation of groups provider with invalid path");
        } catch (IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message:" + e.getMessage(), String.format("Cannot create groups file at '%s'", groupsFile), e.getMessage());
        }
    }

    public void testValidationOnCreateWithInvalidGroups()
    {
        _groupFile = TestFileUtils.createTempFile(this, "groups", "=blah");
        Map<String, Object> attributes = new HashMap<>();
        String groupsFile = _groupFile.getAbsolutePath();
        attributes.put(FileBasedGroupProvider.PATH, groupsFile);
        attributes.put(FileBasedGroupProvider.NAME, getTestName());

        FileBasedGroupProviderImpl groupsProvider = new FileBasedGroupProviderImpl(attributes, _broker);
        try
        {
            groupsProvider.create();
            fail("Exception is expected on validation of groups provider with invalid group file");
        }
        catch (IllegalConfigurationException e)
        {
            assertEquals("Unexpected exception message:" + e.getMessage(), String.format("Cannot load groups from '%s'", groupsFile), e.getMessage());
        }
    }

}
