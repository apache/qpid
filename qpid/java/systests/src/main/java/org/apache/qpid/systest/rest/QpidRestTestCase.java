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
package org.apache.qpid.systest.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class QpidRestTestCase extends QpidBrokerTestCase
{
    public static final String ANONYMOUS_AUTHENTICATION_PROVIDER = "testAnonymous";
    public static final String EXTERNAL_AUTHENTICATION_PROVIDER = "testExternal";

    public static final String TEST1_VIRTUALHOST = "test";
    public static final String TEST2_VIRTUALHOST = "test2";
    public static final String TEST3_VIRTUALHOST = "test3";

    public static final String[] EXPECTED_VIRTUALHOSTS = { TEST1_VIRTUALHOST, TEST2_VIRTUALHOST, TEST3_VIRTUALHOST};
    public static final String[] EXPECTED_EXCHANGES = { "amq.fanout", "amq.match", "amq.direct","amq.topic" };

    private RestTestHelper _restTestHelper = new RestTestHelper(findFreePort());

    @Override
    public void setUp() throws Exception
    {
        // use webadmin account to perform tests
        getRestTestHelper().setUsernameAndPassword("webadmin", "webadmin");

        //remove the normal 'test' vhost, we will configure the vhosts below
        getBrokerConfiguration(0).removeObjectConfiguration(VirtualHostNode.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);

        // Set up virtualhost config with queues and bindings to the amq.direct
        for (String virtualhost : EXPECTED_VIRTUALHOSTS)
        {
            createTestVirtualHostNode(0, virtualhost);
        }

        customizeConfiguration();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            getRestTestHelper().tearDown();
        }
    }

    protected void customizeConfiguration() throws IOException
    {
        TestBrokerConfiguration config = getBrokerConfiguration();
        config.addHttpManagementConfiguration();
        config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.PORT, _restTestHelper.getHttpPort());
        config.removeObjectConfiguration(Port.class, TestBrokerConfiguration.ENTRY_NAME_JMX_PORT);
        config.removeObjectConfiguration(Port.class, TestBrokerConfiguration.ENTRY_NAME_RMI_PORT);

        Map<String, Object> anonymousProviderAttributes = new HashMap<String, Object>();
        anonymousProviderAttributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManager.PROVIDER_TYPE);
        anonymousProviderAttributes.put(AuthenticationProvider.NAME, ANONYMOUS_AUTHENTICATION_PROVIDER);
        config.addObjectConfiguration(AuthenticationProvider.class, anonymousProviderAttributes);

        config.setObjectAttribute(AuthenticationProvider.class, TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER,
                                  "secureOnlyMechanisms",
                                  "{}");


        // set password authentication provider on http port for the tests
        config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        config.setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
    }

    public RestTestHelper getRestTestHelper()
    {
        return _restTestHelper;
    }

    public Map<String, Object> waitForAttributeChanged(String url, String attributeName, Object newValue) throws Exception
    {
        List<Map<String, Object>> nodeAttributes = getRestTestHelper().getJsonAsList(url);
        int timeout = 5000;
        long limit = System.currentTimeMillis() + timeout;
        while(System.currentTimeMillis() < limit && (nodeAttributes.size() == 0 || !newValue.equals(nodeAttributes.get(0).get(attributeName))))
        {
            Thread.sleep(100l);
            nodeAttributes = getRestTestHelper().getJsonAsList(url);
        }
        Map<String, Object> nodeData = nodeAttributes.get(0);
        assertEquals("Attribute " + attributeName + " did not reach expected value within permitted timeout "  + timeout + "ms.", newValue, nodeData.get(attributeName));
        return nodeData;
    }
}
