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
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.VirtualHostRecoverer;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.StandardVirtualHostFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

public class VirtualHostTest extends QpidTestCase
{

    private Broker _broker;
    private StatisticsGatherer _statisticsGatherer;
    private RecovererProvider _recovererProvider;
    private File _configFile;
    private File _bdbStorePath;
    private VirtualHost _host;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        _broker = BrokerTestHelper.createBrokerMock();
        TaskExecutor taslExecutor = mock(TaskExecutor.class);
        when(taslExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_broker.getTaskExecutor()).thenReturn(taslExecutor);

        _recovererProvider = mock(RecovererProvider.class);
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
                _host.setDesiredState(_host.getActualState(), State.STOPPED);
            }
        }
        finally
        {
            if (_configFile != null)
            {
                _configFile.delete();
            }
            if (_bdbStorePath != null)
            {
                FileUtils.delete(_bdbStorePath, true);
            }
            super.tearDown();
            CurrentActor.remove();
        }
    }

    public void testCreateBdbVirtualHostFromConfigurationFile()
    {
        String hostName = getName();
        long logFileMax = 2000000;
        _host = createHostFromConfiguration(hostName, logFileMax);
        _host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected host name", hostName, _host.getName());
        assertEquals("Unexpected host type", StandardVirtualHostFactory.TYPE, _host.getType());
        assertEquals("Unexpected store type", BDBMessageStore.TYPE, _host.getAttribute(VirtualHost.STORE_TYPE));
        assertEquals("Unexpected store path", _bdbStorePath.getAbsolutePath(), _host.getAttribute(VirtualHost.STORE_PATH));

        BDBMessageStore messageStore = (BDBMessageStore) _host.getMessageStore();
        EnvironmentConfig envConfig = messageStore.getEnvironmentFacade().getEnvironment().getConfig();
        assertEquals("Unexpected JE log file max", String.valueOf(logFileMax), envConfig.getConfigParam(EnvironmentConfig.LOG_FILE_MAX));

    }

    public void testCreateBdbHaVirtualHostFromConfigurationFile()
    {
        String hostName = getName();

        String repStreamTimeout = "2 h";
        String nodeName = "node";
        String groupName = "group";
        String nodeHostPort = "localhost:" + findFreePort();
        String helperHostPort = nodeHostPort;
        String durability = "NO_SYNC,SYNC,NONE";
        _host = createHaHostFromConfiguration(hostName, groupName, nodeName, nodeHostPort, helperHostPort, durability, repStreamTimeout);
        _host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected host name", hostName, _host.getName());
        assertEquals("Unexpected host type", BDBHAVirtualHostFactory.TYPE, _host.getType());
        assertEquals("Unexpected store type", ReplicatedEnvironmentFacade.TYPE, _host.getAttribute(VirtualHost.STORE_TYPE));
        assertEquals("Unexpected store path", _bdbStorePath.getAbsolutePath(), _host.getAttribute(VirtualHost.STORE_PATH));

        ReplicationNode localNode = _host.getChildren(ReplicationNode.class).iterator().next();

        assertEquals(nodeName, localNode.getName());
        assertEquals(groupName, localNode.getAttribute(ReplicationNode.GROUP_NAME));
        assertEquals(nodeHostPort, localNode.getAttribute(ReplicationNode.HOST_PORT));
        assertEquals(helperHostPort, localNode.getAttribute(ReplicationNode.HELPER_HOST_PORT));
        assertEquals(durability, localNode.getAttribute(ReplicationNode.DURABILITY));

        BDBMessageStore messageStore = (BDBMessageStore) _host.getMessageStore();
        ReplicatedEnvironment environment = (ReplicatedEnvironment) messageStore.getEnvironmentFacade().getEnvironment();
        ReplicationConfig envConfig = environment.getRepConfig();
        assertEquals("Unexpected JE replication stream timeout", repStreamTimeout, envConfig.getConfigParam(ReplicationConfig.REP_STREAM_TIMEOUT));
    }

    private VirtualHost createHost(Map<String, Object> attributes)
    {
        ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), null);

        return new VirtualHostRecoverer(_statisticsGatherer).create(_recovererProvider, entry, _broker);
    }

    private VirtualHost createHostFromConfiguration(String hostName, long logFileMax)
    {
        String content = "<virtualhosts><virtualhost><name>" + hostName + "</name><" + hostName + ">"
                        + "<store><class>" + BDBMessageStore.class.getName() + "</class>"
                        + "<environment-path>" + _bdbStorePath.getAbsolutePath() + "</environment-path>"
                        + "<envConfig><name>" + EnvironmentConfig.LOG_FILE_MAX + "</name><value>" + logFileMax + "</value></envConfig>"
                        + "</store>"
                        + "</" + hostName + "></virtualhost></virtualhosts>";
        Map<String, Object> attributes = writeConfigAndGenerateAttributes(content);
        return createHost(attributes);
    }

    private VirtualHost createHaHostFromConfiguration(String hostName, String groupName, String nodeName, String nodeHostPort, String helperHostPort, String durability, String repStreamTimeout)
    {
        String content = "<virtualhosts><virtualhost><name>" + hostName + "</name><" + hostName + ">"
                        + "<type>" + BDBHAVirtualHostFactory.TYPE + "</type>"
                        + "<store><class>" + BDBMessageStore.class.getName() + "</class>"
                        + "<environment-path>" + _bdbStorePath.getAbsolutePath() + "</environment-path>"
                        + "<highAvailability>"
                        + "<groupName>" + groupName + "</groupName>"
                        + "<nodeName>" + nodeName + "</nodeName>"
                        + "<nodeHostPort>" + nodeHostPort + "</nodeHostPort>"
                        + "<helperHostPort>" + helperHostPort + "</helperHostPort>"
                        + "<durability>" + durability.replaceAll(",", "\\\\,") + "</durability>"
                        + "</highAvailability>"
                        + "<repConfig><name>" + ReplicationConfig.REP_STREAM_TIMEOUT + "</name><value>" + repStreamTimeout + "</value></repConfig>"
                        + "</store>"
                        + "</" + hostName + "></virtualhost></virtualhosts>";
        Map<String, Object> attributes = writeConfigAndGenerateAttributes(content);
        return createHost(attributes);
    }

    private Map<String, Object> writeConfigAndGenerateAttributes(String content)
    {
        _configFile = TestFileUtils.createTempFile(this, ".virtualhost.xml", content);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.CONFIG_PATH, _configFile.getAbsolutePath());
        return attributes;
    }
}

    