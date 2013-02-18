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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
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
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
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
        when(broker.getAttribute(Broker.SESSION_COUNT_LIMIT)).thenReturn(1);
        when(broker.getAttribute(Broker.HOUSEKEEPING_CHECK_PERIOD)).thenReturn(10000l);
        when(broker.getId()).thenReturn(UUID.randomUUID());
        when(broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(subjectCreator);
        RootMessageLogger rootMessageLogger = CurrentActor.get().getRootMessageLogger();
        when(broker.getRootMessageLogger()).thenReturn(rootMessageLogger);
        when(broker.getVirtualHostRegistry()).thenReturn(new VirtualHostRegistry());
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(null));
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
        StatisticsGatherer statisticsGatherer = mock(StatisticsGatherer.class);
        VirtualHost host = new VirtualHostImpl(virtualHostRegistry, statisticsGatherer, new SecurityManager(null), virtualHostConfiguration);
        virtualHostRegistry.registerVirtualHost(host);
        return host;
    }

    public static VirtualHost createVirtualHost(VirtualHostConfiguration virtualHostConfiguration) throws Exception
    {
        return new VirtualHostImpl(null, mock(StatisticsGatherer.class), new SecurityManager(null), virtualHostConfiguration);
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

    public static AMQChannel createChannel(int channelId, AMQProtocolSession session) throws AMQException
    {
        AMQChannel channel = new AMQChannel(session, channelId, session.getVirtualHost().getMessageStore());
        session.addChannel(channel);
        return channel;
    }

    public static AMQChannel createChannel(int channelId) throws Exception
    {
        InternalTestProtocolSession session = createSession();
        return createChannel(channelId, session);
    }

    public static AMQChannel createChannel() throws Exception
    {
        return createChannel(1);
    }

    public static InternalTestProtocolSession createSession() throws Exception
    {
        return createSession("test");
    }

    public static InternalTestProtocolSession createSession(String hostName) throws Exception
    {
        VirtualHost virtualHost = createVirtualHost(hostName);
        return new InternalTestProtocolSession(virtualHost, createBrokerMock());
    }

    public static Exchange createExchange(String hostName) throws Exception
    {
        SecurityManager securityManager = new SecurityManager(null);
        VirtualHost virtualHost = mock(VirtualHost.class);
        when(virtualHost.getName()).thenReturn(hostName);
        when(virtualHost.getSecurityManager()).thenReturn(securityManager);
        DefaultExchangeFactory factory = new DefaultExchangeFactory(virtualHost);
        return factory.createExchange("amp.direct", "direct", false, false);
    }

    public static void publishMessages(AMQChannel channel, int numberOfMessages, String queueName, String exchangeName) throws AMQException
    {
        AMQShortString rouningKey = new AMQShortString(queueName);
        AMQShortString exchangeNameAsShortString = new AMQShortString(exchangeName);
        MessagePublishInfo info = mock(MessagePublishInfo.class);
        when(info.getExchange()).thenReturn(exchangeNameAsShortString);
        when(info.getRoutingKey()).thenReturn(rouningKey);

        Exchange exchange = channel.getVirtualHost().getExchangeRegistry().getExchange(exchangeName);
        for (int count = 0; count < numberOfMessages; count++)
        {
            channel.setPublishFrame(info, exchange);

            // Set the body size
            ContentHeaderBody _headerBody = new ContentHeaderBody();
            _headerBody.setBodySize(0);

            // Set Minimum properties
            BasicContentHeaderProperties properties = new BasicContentHeaderProperties();

            properties.setExpiration(0L);
            properties.setTimestamp(System.currentTimeMillis());

            // Make Message Persistent
            properties.setDeliveryMode((byte) 2);

            _headerBody.setProperties(properties);

            channel.publishContentHeader(_headerBody);
        }
        channel.sync();
    }

    public static SimpleAMQQueue createQueue(String queueName, VirtualHost virtualHost) throws AMQException
    {
        SimpleAMQQueue queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, null,
                false, false, virtualHost, Collections.<String, Object>emptyMap());
        virtualHost.getQueueRegistry().registerQueue(queue);
        return queue;
    }


}
