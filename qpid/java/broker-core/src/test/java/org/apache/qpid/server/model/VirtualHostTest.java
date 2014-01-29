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
package org.apache.qpid.server.model;

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
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.StandardVirtualHostFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class VirtualHostTest extends QpidTestCase
{

    private Broker _broker;
    private StatisticsGatherer _statisticsGatherer;
    private RecovererProvider _recovererProvider;
    private File _configFile;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        _broker = BrokerTestHelper.createBrokerMock();
        TaskExecutor taskExecutor = mock(TaskExecutor.class);
        when(taskExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_broker.getTaskExecutor()).thenReturn(taskExecutor);

        _recovererProvider = mock(RecovererProvider.class);
        _statisticsGatherer = mock(StatisticsGatherer.class);
    }

    @Override
    protected void tearDown() throws Exception
    {
        if (_configFile != null)
        {
            _configFile.delete();
        }
        super.tearDown();
        CurrentActor.remove();
    }

    public void testInitialisingState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));
    }

    public void testActiveState()
    {
        VirtualHost host = createHost();

        host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));
    }

    public void testQuiescedState()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.TYPE, StandardVirtualHostFactory.TYPE);
        attributes.put(VirtualHost.STORE_TYPE, TestMemoryMessageStore.TYPE);
        attributes.put(VirtualHost.STATE, State.QUIESCED);

        VirtualHost host = createHost(attributes);

        assertEquals("Unexpected state", State.QUIESCED, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.QUIESCED, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));
    }

    public void testStoppedState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.ACTIVE, State.STOPPED);
        assertEquals("Unexpected state", State.STOPPED, host.getAttribute(VirtualHost.STATE));
    }

    public void testDeletedState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.INITIALISING, State.DELETED);
        assertEquals("Unexpected state", State.DELETED, host.getAttribute(VirtualHost.STATE));
    }

    public void testCreateQueueChildHavingMessageGroupingAttributes()
    {
        VirtualHost host = createHost();
        host.setDesiredState(State.INITIALISING, State.ACTIVE);

        String queueName = getTestName();
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(Queue.MESSAGE_GROUP_KEY, "mykey");
        arguments.put(Queue.MESSAGE_GROUP_SHARED_GROUPS, true);
        arguments.put(Queue.NAME, queueName);

        host.createChild(Queue.class, arguments);

        Queue queue = ConfiguredObjectFinder.findConfiguredObjectByName(host.getQueues(), queueName);
        Object messageGroupKey = queue.getAttribute(Queue.MESSAGE_GROUP_KEY);
        assertEquals("Unexpected message group key attribute", "mykey", messageGroupKey);

        Object sharedGroups = queue.getAttribute(Queue.MESSAGE_GROUP_SHARED_GROUPS);
        assertEquals("Unexpected shared groups attribute", true, sharedGroups);

    }

    public void testCreateVirtualHostFromConfigurationFile()
    {
        String hostName = getName();
        int maximuMessageAge = 123;
        VirtualHost host = createHostFromConfiguration(hostName, maximuMessageAge);
        host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected host name", hostName, host.getName());
        assertEquals("Unexpected host type", StandardVirtualHostFactory.TYPE, host.getType());
        assertEquals("Unexpected store type", TestMemoryMessageStore.TYPE, host.getAttribute(VirtualHost.STORE_TYPE));
        assertEquals("Unexpected maximum message age alert", maximuMessageAge, host.getAttribute(VirtualHost.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE));
    }

    private VirtualHost createHost()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.TYPE, StandardVirtualHostFactory.TYPE);
        attributes.put(VirtualHost.STORE_TYPE, TestMemoryMessageStore.TYPE);

        VirtualHost host = createHost(attributes);
        return host;
    }

    private VirtualHost createHost(Map<String, Object> attributes)
    {
        ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), null);

        return new VirtualHostRecoverer(_statisticsGatherer).create(_recovererProvider, entry, _broker);
    }

    private VirtualHost createHostFromConfiguration(String hostName, long maximuMessageAge)
    {
        String content = "<virtualhosts><virtualhost><name>" + hostName + "</name><" + hostName + ">"
                        + "<queues><maximumMessageAge>" + maximuMessageAge + "</maximumMessageAge></queues>"
                        + "<store><class>" + TestMemoryMessageStore.class.getName() + "</class></store>"
                        + "</" + hostName + "></virtualhost></virtualhosts>";
        _configFile = TestFileUtils.createTempFile(this, ".virtualhost.xml", content);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.CONFIG_PATH, _configFile.getAbsolutePath());
        return createHost(attributes);
    }
}

    