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

package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProviderImpl;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

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
public class PreferencesProviderCreationTest extends QpidTestCase
{
    private AuthenticationProvider _authenticationProvider;
    private Broker _broker;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _authenticationProvider = mock(AuthenticationProvider.class);
        _broker = BrokerTestHelper.createBrokerMock();
        ConfiguredObjectFactory factory = _broker.getObjectFactory();
        when(_authenticationProvider.getParent(Broker.class)).thenReturn(_broker);
        when(_authenticationProvider.getObjectFactory()).thenReturn(factory);
        when(_authenticationProvider.getModel()).thenReturn(factory.getModel());
        TaskExecutor taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        when(_authenticationProvider.getTaskExecutor()).thenReturn(taskExecutor);
        when(_authenticationProvider.getChildExecutor()).thenReturn(taskExecutor);

    }

    public void tearDown() throws Exception
    {
        try
        {
            BrokerTestHelper.tearDown();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testRecoverFileSystemPreferencesProvider()
    {

        Map<String, Object> attributes = new HashMap<String, Object>();
        UUID id = UUID.randomUUID();
        attributes.put(PreferencesProvider.ID, id);
        attributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.PROVIDER_TYPE);
        attributes.put(PreferencesProvider.NAME, "test-provider");
        File file = TestFileUtils.createTempFile(this,
                                                 ".prefs.json",
                                                 "{\"test_user\":{\"pref1\": \"pref1Value\", \"pref2\": 1.0} }");
        try
        {
            attributes.put(FileSystemPreferencesProvider.PATH, file.getAbsolutePath());
            PreferencesProvider provider = new FileSystemPreferencesProviderImpl(attributes,_authenticationProvider);
            provider.open();
            assertNotNull("Preferences provider was not recovered", provider);
            assertEquals("Unexpected name", "test-provider", provider.getName());
            assertEquals("Unexpected id", id, provider.getId());
            assertEquals("Unexpected path", file.getAbsolutePath(), provider.getAttribute(
                    FileSystemPreferencesProvider.PATH));
        }
        finally
        {
            file.delete();
        }
    }

}
