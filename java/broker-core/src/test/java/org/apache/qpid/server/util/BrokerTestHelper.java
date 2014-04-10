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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.StandardVirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerTestHelper
{

    protected static final String BROKER_STORE_CLASS_NAME_KEY = "brokerstore.class.name";
    protected static final String JSON_BROKER_STORE_CLASS_NAME = JsonConfigurationEntryStore.class.getName();

    private static final TaskExecutor TASK_EXECUTOR = new TaskExecutor();
    static
    {
        TASK_EXECUTOR.start();
    }

    public static Broker createBrokerMock()
    {
        SubjectCreator subjectCreator = mock(SubjectCreator.class);
        when(subjectCreator.getMechanisms()).thenReturn("");
        Broker broker = mock(Broker.class);
        when(broker.getConnection_sessionCountLimit()).thenReturn(1);
        when(broker.getConnection_closeWhenNoRoute()).thenReturn(false);
        when(broker.getId()).thenReturn(UUID.randomUUID());
        when(broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(subjectCreator);
        when(broker.getVirtualHostRegistry()).thenReturn(new VirtualHostRegistry(new EventLogger()));
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(mock(Broker.class), false));
        when(broker.getEventLogger()).thenReturn(new EventLogger());
        return broker;
    }

    public static void setUp()
    {
    }

    public static void tearDown()
    {
    }

    public static VirtualHostImpl createVirtualHost(VirtualHostRegistry virtualHostRegistry, Map<String,Object> attributes)
            throws Exception
    {

        //VirtualHostFactory factory = new PluggableFactoryLoader<VirtualHostFactory>(VirtualHostFactory.class).get(hostType);

        Broker broker = mock(Broker.class);
        when(broker.getVirtualHostRegistry()).thenReturn(virtualHostRegistry);
        when(broker.getTaskExecutor()).thenReturn(TASK_EXECUTOR);
        SecurityManager securityManager = new SecurityManager(broker, false);
        when(broker.getSecurityManager()).thenReturn(securityManager);

        ConfiguredObjectFactory objectFactory = new ConfiguredObjectFactory();
        ConfiguredObjectTypeFactory factory = objectFactory.getConfiguredObjectTypeFactory(org.apache.qpid.server.model.VirtualHost.class,
                                                                      attributes);

        AbstractVirtualHost host = (AbstractVirtualHost) factory.create(attributes, broker);

        host.setDesiredState(host.getState(), State.ACTIVE);

        return host;
    }

    public static VirtualHostImpl createVirtualHost(String name) throws Exception
    {
        return createVirtualHost(name, new VirtualHostRegistry(new EventLogger()));
    }

    public static VirtualHostImpl createVirtualHost(String name, VirtualHostRegistry virtualHostRegistry) throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.VirtualHost.TYPE, StandardVirtualHost.TYPE);

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_TYPE, TestMemoryMessageStore.TYPE);

        attributes.put(org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);
        attributes.put(org.apache.qpid.server.model.VirtualHost.NAME, name);

        return createVirtualHost(virtualHostRegistry, attributes);
    }

    public static AMQSessionModel createSession(int channelId, AMQConnectionModel connection)
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
        VirtualHostImpl virtualHost = createVirtualHost(hostName);
        AMQConnectionModel connection = mock(AMQConnectionModel.class);
        return connection;
    }

    public static ExchangeImpl createExchange(String hostName, final boolean durable, final EventLogger eventLogger) throws Exception
    {
        SecurityManager securityManager = new SecurityManager(mock(Broker.class), false);
        VirtualHostImpl virtualHost = mock(VirtualHostImpl.class);
        when(virtualHost.getName()).thenReturn(hostName);
        when(virtualHost.getSecurityManager()).thenReturn(securityManager);
        when(virtualHost.getEventLogger()).thenReturn(eventLogger);
        DefaultExchangeFactory factory = new DefaultExchangeFactory(virtualHost);
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.Exchange.ID, UUIDGenerator.generateExchangeUUID("amp.direct", virtualHost.getName()));
        attributes.put(org.apache.qpid.server.model.Exchange.NAME, "amq.direct");
        attributes.put(org.apache.qpid.server.model.Exchange.TYPE, "direct");
        attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
        return factory.createExchange(attributes);
    }

    public static AMQQueue createQueue(String queueName, VirtualHostImpl virtualHost)
            throws QueueExistsException
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        attributes.put(Queue.NAME, queueName);
        AMQQueue queue = virtualHost.createQueue(attributes);
        return queue;
    }

}
