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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.VirtualHostRecoverer;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

public class VirtualHostTest extends QpidTestCase
{

    private Broker<?> _broker;
    private StatisticsGatherer _statisticsGatherer;
    private RecovererProvider _recovererProvider;
    private File _bdbStorePath;
    private VirtualHost<?> _host;
    private ConfigurationEntryStore _store;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _store = mock(ConfigurationEntryStore.class);
        _broker = BrokerTestHelper.createBrokerMock();
        TaskExecutor taslExecutor = mock(TaskExecutor.class);
        when(taslExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_broker.getTaskExecutor()).thenReturn(taslExecutor);

        _statisticsGatherer = mock(StatisticsGatherer.class);

        _bdbStorePath = new File(TMP_FOLDER, getTestName() + "." + System.currentTimeMillis());
        _bdbStorePath.deleteOnExit();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            if (_host != null)
            {
                _host.setDesiredState(_host.getState(), State.STOPPED);
            }
        }
        finally
        {
            if (_bdbStorePath != null)
            {
                FileUtils.delete(_bdbStorePath, true);
            }
            super.tearDown();
        }
    }

    public void testCreateBdbHaVirtualHostFromConfigurationEntry()
    {
        String repStreamTimeout = "2 h";
        String nodeName = "node";
        String groupName = "group";
        String nodeHostPort = "localhost:" + findFreePort();
        String helperHostPort = nodeHostPort;
        String durability = "NO_SYNC,SYNC,NONE";
        String hostName = getName();

        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put("haNodeName", nodeName);
        virtualHostAttributes.put("haGroupName", groupName);
        virtualHostAttributes.put("haNodeAddress", nodeHostPort);
        virtualHostAttributes.put("haHelperAddress", helperHostPort);
        virtualHostAttributes.put("haDurability", durability);
        virtualHostAttributes.put(VirtualHost.STORE_PATH, _bdbStorePath.getAbsolutePath());
        virtualHostAttributes.put("haReplicationConfig",
                Collections.singletonMap(ReplicationConfig.REP_STREAM_TIMEOUT, repStreamTimeout));
        virtualHostAttributes.put(VirtualHost.NAME, hostName);
        virtualHostAttributes.put(VirtualHost.TYPE, BDBHAVirtualHostFactory.TYPE);

        _host = createHost(virtualHostAttributes);
        _host.setDesiredState(State.INITIALISING, State.ACTIVE);

        assertEquals("Unexpected host name", hostName, _host.getName());
        assertEquals("Unexpected host type", BDBHAVirtualHostFactory.TYPE, _host.getType());
        assertEquals("Unexpected store type", ReplicatedEnvironmentFacade.TYPE, _host.getAttribute(VirtualHost.STORE_TYPE));

        assertEquals(nodeName, _host.getAttribute("haNodeName"));
        assertEquals(groupName, _host.getAttribute("haGroupName"));
        assertEquals(nodeHostPort, _host.getAttribute("haNodeAddress"));
        assertEquals(helperHostPort, _host.getAttribute("haHelperAddress"));
        assertEquals(durability, _host.getAttribute("haDurability"));
        assertEquals("Unexpected store path", _bdbStorePath.getAbsolutePath(), _host.getAttribute(VirtualHost.STORE_PATH));

        BDBMessageStore messageStore = (BDBMessageStore) _host.getMessageStore();
        ReplicatedEnvironment environment = (ReplicatedEnvironment) messageStore.getEnvironmentFacade().getEnvironment();
        ReplicationConfig replicationConfig = environment.getRepConfig();

        assertEquals(nodeName, environment.getNodeName());
        assertEquals(groupName, environment.getGroup().getName());
        assertEquals(nodeHostPort, replicationConfig.getNodeHostPort());
        assertEquals(helperHostPort, replicationConfig.getHelperHosts());
        assertEquals(durability, environment.getConfig().getDurability().toString());
        assertEquals("Unexpected JE replication stream timeout", repStreamTimeout, replicationConfig.getConfigParam(ReplicationConfig.REP_STREAM_TIMEOUT));

    }


    private VirtualHost<?> createHost(Map<String, Object> attributes)
    {
        ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(), attributes,
                Collections.<UUID>emptySet(), _store);

        return new VirtualHostRecoverer(_statisticsGatherer).create(_recovererProvider, entry, _broker);
    }

}

    