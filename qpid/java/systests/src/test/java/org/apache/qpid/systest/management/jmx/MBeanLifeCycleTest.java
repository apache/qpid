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
package org.apache.qpid.systest.management.jmx;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.virtualhost.ProvidedStoreVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.memory.MemoryVirtualHostNode;
import org.apache.qpid.systest.rest.QpidRestTestCase;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class MBeanLifeCycleTest extends QpidRestTestCase
{
    private final static String TEST_VIRTUAL_HOST_MBEAN_SEARCH_QUERY = "org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost="
            + ObjectName.quote(TEST2_VIRTUALHOST);
    private JMXTestUtils _jmxUtils;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.open();
    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        TestBrokerConfiguration config = getBrokerConfiguration();
        config.addHttpManagementConfiguration();
        config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.PORT, getRestTestHelper().getHttpPort());

        // set password authentication provider on http port for the tests
        config.setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        config.setObjectAttribute(Plugin.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, true);
        getBrokerConfiguration().addJmxManagementConfiguration();
    }

    @Override
    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    public void testVirtualHostMBeanIsRegisteredOnVirtualHostCreation() throws Exception
    {
        String nodeName = "ntmp";
        String hostName = "htmp";

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put(VirtualHostNode.NAME, nodeName);
        nodeData.put(VirtualHostNode.TYPE, MemoryVirtualHostNode.VIRTUAL_HOST_NODE_TYPE);
        getRestTestHelper().submitRequest("virtualhostnode/" + nodeName, "PUT", nodeData, HttpServletResponse.SC_CREATED);

        Map<String, Object> virtualhostData = new HashMap<>();
        virtualhostData.put(VirtualHost.NAME, nodeName);
        virtualhostData.put(VirtualHost.TYPE, ProvidedStoreVirtualHostImpl.VIRTUAL_HOST_TYPE);
        getRestTestHelper().submitRequest("virtualhost/" + nodeName + "/" + hostName,
                                          "PUT",
                                          virtualhostData,
                                          HttpServletResponse.SC_CREATED);


        ManagedBroker managedBroker = _jmxUtils.getManagedBroker(hostName);
        assertNotNull("Host mBean is not created", managedBroker);
    }

    public void testVirtualHostMBeanIsUnregisteredOnVirtualHostDeletion() throws Exception
    {
        boolean mBeanExists =_jmxUtils.doesManagedObjectExist(TEST_VIRTUAL_HOST_MBEAN_SEARCH_QUERY);
        assertTrue("Host mBean is not registered", mBeanExists);

        getRestTestHelper().submitRequest("virtualhostnode/" + TEST2_VIRTUALHOST, "DELETE", HttpServletResponse.SC_OK);

        mBeanExists =_jmxUtils.doesManagedObjectExist(TEST_VIRTUAL_HOST_MBEAN_SEARCH_QUERY);
        assertFalse("Host mBean is not unregistered", mBeanExists);
    }

    public void testVirtualHostMBeanIsUnregisteredOnVirtualHostNodeStop() throws Exception
    {
        boolean mBeanExists =_jmxUtils.doesManagedObjectExist(TEST_VIRTUAL_HOST_MBEAN_SEARCH_QUERY);
        assertTrue("Host mBean is not registered", mBeanExists);

        ManagedBroker managedBroker = _jmxUtils.getManagedBroker(TEST2_VIRTUALHOST);
        assertNotNull("Host mBean is not created", managedBroker);

        Map<String, Object> nodeData = new HashMap<String, Object>();
        nodeData.put(VirtualHostNode.NAME, TEST2_VIRTUALHOST);
        nodeData.put(VirtualHostNode.DESIRED_STATE, State.STOPPED.name());

        int status = getRestTestHelper().submitRequest("virtualhostnode/" + TEST2_VIRTUALHOST, "PUT", nodeData);
        assertEquals("Unexpected code", 200, status);

        mBeanExists =_jmxUtils.doesManagedObjectExist(TEST_VIRTUAL_HOST_MBEAN_SEARCH_QUERY);
        assertFalse("Host mBean is not unregistered", mBeanExists);
    }
}
