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

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class VirtualHostTest extends QpidTestCase
{

    private Broker<?> _broker;
    private StatisticsGatherer _statisticsGatherer;
    private RecovererProvider _recovererProvider;
    private File _bdbStorePath;
    private VirtualHost<?,?,?> _host;
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
        String virtualHostName = getName();

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.NODE_NAME, nodeName);
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.GROUP_NAME, groupName);
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.NODE_ADDRESS, nodeHostPort);
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.HELPER_ADDRESS, helperHostPort);
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.DURABILITY, durability);

        messageStoreSettings.put(MessageStore.STORE_PATH, _bdbStorePath.getAbsolutePath());
        messageStoreSettings.put(ReplicatedEnvironmentFacadeFactory.REPLICATION_CONFIG,
                Collections.singletonMap(ReplicationConfig.REP_STREAM_TIMEOUT, repStreamTimeout));

        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
        virtualHostAttributes.put(VirtualHost.NAME, virtualHostName);
        virtualHostAttributes.put(VirtualHost.TYPE, BDBHAVirtualHost.TYPE);
        virtualHostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);

        _host = createHost(virtualHostAttributes);
        _host.setDesiredState(State.INITIALISING, State.ACTIVE);

        assertEquals("Unexpected virtual host name", virtualHostName, _host.getName());
        assertEquals("Unexpected host type", BDBHAVirtualHost.TYPE, _host.getType());

        assertEquals(messageStoreSettings, _host.getMessageStoreSettings());

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


    private VirtualHost<?,?,?> createHost(Map<String, Object> attributes)
    {
        ConfiguredObjectFactory factory = new ConfiguredObjectFactory();
        ConfiguredObjectTypeFactory vhostFactory =
                factory.getConfiguredObjectTypeFactory(VirtualHost.class, attributes);
        attributes = new HashMap<String, Object>(attributes);
        attributes.put(ConfiguredObject.ID, UUID.randomUUID());
        return (VirtualHost<?,?,?>) vhostFactory.create(attributes,_broker);
    }

}

    
