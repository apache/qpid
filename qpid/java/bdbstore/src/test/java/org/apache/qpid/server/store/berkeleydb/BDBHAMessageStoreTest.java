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
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.replication.ReplicationGroupListener;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.ReplicationConfig;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class BDBHAMessageStoreTest extends QpidTestCase
{
    private static final String TEST_LOG_FILE_MAX = "1000000";
    private static final String TEST_ELECTION_RETRIES = "1000";
    private static final String TEST_NUMBER_OF_THREADS = "10";
    private static final String TEST_ENV_CONSISTENCY_TIMEOUT = "9999999";
    private String _groupName;
    private String _workDir;
    private int _masterPort;
    private String _host;
    private XMLConfiguration _configXml;
    private VirtualHost _virtualHost;
    private org.apache.qpid.server.model.VirtualHost _modelVhost;

    public void setUp() throws Exception
    {
        super.setUp();

        _workDir = TMP_FOLDER + File.separator + getName();
        _host = InetAddress.getByName("localhost").getHostAddress();
        _groupName = "group" + getName();
        _masterPort = -1;

        FileUtils.delete(new File(_workDir), true);
        _configXml = new XMLConfiguration();
        _modelVhost = mock(TestVirtualHost.class);


        BrokerTestHelper.setUp();
     }

    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
            FileUtils.delete(new File(_workDir), true);
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testSetSystemConfiguration() throws Exception
    {
        // create virtual host configuration, registry and host instance
        addVirtualHostConfiguration();
        String vhostName = "test" + _masterPort;
        VirtualHostConfiguration configuration = new VirtualHostConfiguration(vhostName, _configXml.subset("virtualhosts.virtualhost." + vhostName), BrokerTestHelper.createBrokerMock());

        _virtualHost = BrokerTestHelper.createVirtualHost(configuration, null, _modelVhost);
        BDBMessageStore store = (BDBMessageStore) _virtualHost.getMessageStore();

        // test whether JVM system settings were applied
        EnvironmentFacade env = store.getEnvironmentFacade();
        assertEquals("Unexpected number of cleaner threads", TEST_NUMBER_OF_THREADS, env.getEnvironment().getConfig().getConfigParam(EnvironmentConfig.CLEANER_THREADS));
        assertEquals("Unexpected log file max", TEST_LOG_FILE_MAX, env.getEnvironment().getConfig().getConfigParam(EnvironmentConfig.LOG_FILE_MAX));

        ReplicatedEnvironmentFacade repEnv = (ReplicatedEnvironmentFacade)store.getEnvironmentFacade();
        assertEquals("Unexpected number of elections primary retries", TEST_ELECTION_RETRIES,
                repEnv.getEnvironment().getConfig().getConfigParam(ReplicationConfig.ELECTIONS_PRIMARY_RETRIES));
        assertEquals("Unexpected number of elections primary retries", TEST_ENV_CONSISTENCY_TIMEOUT,
                repEnv.getEnvironment().getConfig().getConfigParam(ReplicationConfig.ENV_CONSISTENCY_TIMEOUT));
    }

    private void addVirtualHostConfiguration() throws Exception
    {
        int port = findFreePort();
        if (_masterPort == -1)
        {
            _masterPort = port;
        }
        String nodeName = getNodeNameForNodeAt(port);

        String vhostName = "test" + port;
        String vhostPrefix = "virtualhosts.virtualhost." + vhostName;

        _configXml.addProperty("virtualhosts.virtualhost.name", vhostName);
        _configXml.addProperty(vhostPrefix + ".type", BDBHAVirtualHostFactory.TYPE);

        when(_modelVhost.getAttribute(eq(org.apache.qpid.server.model.VirtualHost.STORE_PATH))).thenReturn(_workDir + File.separator
                                                                                        + port);
        when(_modelVhost.getAttribute(eq("haGroupName"))).thenReturn(_groupName);
        when(_modelVhost.getAttribute(eq("haNodeName"))).thenReturn(nodeName);
        when(_modelVhost.getAttribute(eq("haNodeAddress"))).thenReturn(getNodeHostPortForNodeAt(port));
        when(_modelVhost.getAttribute(eq("haHelperAddress"))).thenReturn(getHelperHostPort());

        Map<String,String> bdbEnvConfig = new HashMap<String,String>();
        bdbEnvConfig.put(EnvironmentConfig.CLEANER_THREADS, TEST_NUMBER_OF_THREADS);
        bdbEnvConfig.put(EnvironmentConfig.LOG_FILE_MAX, TEST_LOG_FILE_MAX);

        when(_modelVhost.getAttribute(eq("bdbEnvironmentConfig"))).thenReturn(bdbEnvConfig);

        Map<String,String> repConfig = new HashMap<String,String>();
        repConfig.put(ReplicationConfig.ELECTIONS_PRIMARY_RETRIES, TEST_ELECTION_RETRIES);
        repConfig.put(ReplicationConfig.ENV_CONSISTENCY_TIMEOUT, TEST_ENV_CONSISTENCY_TIMEOUT);
        when(_modelVhost.getAttribute(eq("haReplicationConfig"))).thenReturn(repConfig);

    }

    private String getNodeNameForNodeAt(final int bdbPort)
    {
        return "node" + getName() + bdbPort;
    }

    private String getNodeHostPortForNodeAt(final int bdbPort)
    {
        return _host + ":" + bdbPort;
    }

    private String getHelperHostPort()
    {
        if (_masterPort == -1)
        {
            throw new IllegalStateException("Helper port not yet assigned.");
        }
        return _host + ":" + _masterPort;
    }

    // TODO: a temporary work around against issue with casting VirtualHost model object to ReplicationGroupListener in the BDBHAVH
    public static interface TestVirtualHost extends org.apache.qpid.server.model.VirtualHost, ReplicationGroupListener
    {
    }
}
