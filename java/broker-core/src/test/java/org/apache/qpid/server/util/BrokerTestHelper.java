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
package org.apache.qpid.server.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.StandardVirtualHostFactory;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerTestHelper
{

    protected static final String BROKER_STORE_CLASS_NAME_KEY = "brokerstore.class.name";
    protected static final String JSON_BROKER_STORE_CLASS_NAME = JsonConfigurationEntryStore.class.getName();

    public static Broker createBrokerMock()
    {
        SubjectCreator subjectCreator = mock(SubjectCreator.class);
        when(subjectCreator.getMechanisms()).thenReturn("");
        Broker broker = mock(Broker.class);
        when(broker.getAttribute(Broker.CONNECTION_SESSION_COUNT_LIMIT)).thenReturn(1);
        when(broker.getAttribute(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE)).thenReturn(false);
        when(broker.getAttribute(Broker.VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD)).thenReturn(10000l);
        when(broker.getId()).thenReturn(UUID.randomUUID());
        when(broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(subjectCreator);
        RootMessageLogger rootMessageLogger = CurrentActor.get().getRootMessageLogger();
        when(broker.getRootMessageLogger()).thenReturn(rootMessageLogger);
        when(broker.getVirtualHostRegistry()).thenReturn(new VirtualHostRegistry());
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(mock(Broker.class), false));
        GenericActor.setDefaultMessageLogger(rootMessageLogger);
        return broker;
    }

    public static void setUp()
    {
       CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));
    }

    public static void tearDown()
    {
        CurrentActor.remove();
    }

    public static VirtualHost createVirtualHost(VirtualHostConfiguration virtualHostConfiguration, VirtualHostRegistry virtualHostRegistry)
            throws Exception
    {
        return createVirtualHost(virtualHostConfiguration, virtualHostRegistry, mock(org.apache.qpid.server.model.VirtualHost.class));
    }

    public static VirtualHost createVirtualHost(VirtualHostConfiguration virtualHostConfiguration, VirtualHostRegistry virtualHostRegistry, org.apache.qpid.server.model.VirtualHost modelVHost)
            throws Exception
    {
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        final VirtualHostFactory factory =
                        virtualHostConfiguration == null ? new StandardVirtualHostFactory()
                                                         : VirtualHostFactory.FACTORIES.get(virtualHostConfiguration.getType());
        VirtualHost host = factory.createVirtualHost(virtualHostRegistry,
                statisticsGatherer,
                new SecurityManager(mock(Broker.class), false),
                virtualHostConfiguration,
                modelVHost);
        if(virtualHostRegistry != null)
        {
            virtualHostRegistry.registerVirtualHost(host);
        }
        return host;
    }

    public static VirtualHost createVirtualHost(VirtualHostConfiguration virtualHostConfiguration) throws Exception
    {
        return createVirtualHost(virtualHostConfiguration, null);
    }

    public static VirtualHost createVirtualHost(String name, VirtualHostRegistry virtualHostRegistry) throws Exception
    {
        VirtualHostConfiguration vhostConfig = createVirtualHostConfiguration(name);
        return createVirtualHost(vhostConfig, virtualHostRegistry);
    }

    public static VirtualHost createVirtualHost(String name) throws Exception
    {
        VirtualHostConfiguration configuration = createVirtualHostConfiguration(name);
        return createVirtualHost(configuration);
    }

    private static VirtualHostConfiguration createVirtualHostConfiguration(String name) throws ConfigurationException
    {
        VirtualHostConfiguration vhostConfig = new VirtualHostConfiguration(name, new PropertiesConfiguration(), createBrokerMock());
        vhostConfig.setMessageStoreClass(TestableMemoryMessageStore.class.getName());
        return vhostConfig;
    }

    public static AMQSessionModel createSession(int channelId, AMQConnectionModel connection) throws AMQException
    {
        AMQSessionModel session = mock(AMQSessionModel.class);
        when(session.getConnectionModel()).thenReturn(connection);
        when(session.getChannelId()).thenReturn(channelId);
        return session;
    }

    public static AMQSessionModel createSession(int channelId) throws Exception
    {
        AMQConnectionModel session = createConnection();
        return createSession(channelId, session);
    }

    public static AMQSessionModel createSession() throws Exception
    {
        return createSession(1);
    }

    public static AMQConnectionModel createConnection() throws Exception
    {
        return createConnection("test");
    }

    public static AMQConnectionModel createConnection(String hostName) throws Exception
    {
        VirtualHost virtualHost = createVirtualHost(hostName);
        AMQConnectionModel connection = mock(AMQConnectionModel.class);
        return connection;
    }

    public static Exchange createExchange(String hostName) throws Exception
    {
        SecurityManager securityManager = new SecurityManager(mock(Broker.class), false);
        VirtualHost virtualHost = mock(VirtualHost.class);
        when(virtualHost.getName()).thenReturn(hostName);
        when(virtualHost.getSecurityManager()).thenReturn(securityManager);
        DefaultExchangeFactory factory = new DefaultExchangeFactory(virtualHost);
        return factory.createExchange("amp.direct", "direct", false, false);
    }

    public static AMQQueue createQueue(String queueName, VirtualHost virtualHost) throws AMQException
    {
        AMQQueue queue = virtualHost.createQueue(UUIDGenerator.generateRandomUUID(), queueName, false, null,
                false, false, false, Collections.<String, Object>emptyMap());
        return queue;
    }


}
