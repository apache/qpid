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
package org.apache.qpid.server.security;

import static org.apache.qpid.server.security.access.ObjectType.BROKER;
import static org.apache.qpid.server.security.access.Operation.ACCESS_LOGS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.Collections;

import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueConsumer;
import org.apache.qpid.server.security.access.ObjectProperties;
import org.apache.qpid.server.security.access.ObjectProperties.Property;
import org.apache.qpid.server.security.access.ObjectType;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.access.OperationLoggingDetails;
import org.apache.qpid.test.utils.QpidTestCase;

public class SecurityManagerTest extends QpidTestCase
{
    private static final String TEST_EXCHANGE_TYPE = "testExchangeType";
    private static final String TEST_VIRTUAL_HOST = "testVirtualHost";
    private static final String TEST_EXCHANGE = "testExchange";
    private static final String TEST_QUEUE = "testQueue";

    private AccessControl _accessControl;
    private SecurityManager _securityManager;
    private VirtualHost<?,?,?> _virtualHost;
    private Broker _broker;
    private VirtualHostNode<?> _virtualHostNode;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _accessControl = mock(AccessControl.class);
        _virtualHost = mock(VirtualHost.class);

        AccessControlProvider<?> aclProvider = mock(AccessControlProvider.class);
        when(aclProvider.getAccessControl()).thenReturn(_accessControl);
        when(aclProvider.getState()).thenReturn(State.ACTIVE);

        when(_virtualHost.getName()).thenReturn(TEST_VIRTUAL_HOST);
        when(_virtualHost.getAttribute(VirtualHost.NAME)).thenReturn(TEST_VIRTUAL_HOST);

        _broker = mock(Broker.class);
        when(_broker.getAccessControlProviders()).thenReturn(Collections.singleton(aclProvider));
        when(_broker.getChildren(AccessControlProvider.class)).thenReturn(Collections.singleton(aclProvider));
        when(_broker.getCategoryClass()).thenReturn(Broker.class);
        when(_broker.getName()).thenReturn("My Broker");
        when(_broker.getAttribute(Broker.NAME)).thenReturn("My Broker");
        when(_broker.getModel()).thenReturn(BrokerModel.getInstance());

        _virtualHostNode = getMockVirtualHostNode();
        _securityManager = new SecurityManager(_broker, false);
    }

    public void testAuthoriseCreateBinding()
    {
        VirtualHost vh = getMockVirtualHost();

        Exchange exchange = mock(Exchange.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getAttribute(Exchange.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(vh);
        when(exchange.getModel()).thenReturn(BrokerModel.getInstance());

        Queue queue = mock(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(true);
        when(queue.getAttribute(Queue.LIFETIME_POLICY)).thenReturn(LifetimePolicy.PERMANENT);
        when(queue.getCategoryClass()).thenReturn(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(vh);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.QUEUE_NAME, TEST_QUEUE);
        properties.put(Property.ROUTING_KEY, "bindingKey");
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);

        Binding binding = mock(Binding.class);
        when(binding.getParent(Exchange.class)).thenReturn(exchange);
        when(binding.getParent(Queue.class)).thenReturn(queue);
        when(binding.getAttribute(Binding.NAME)).thenReturn("bindingKey");
        when(binding.getCategoryClass()).thenReturn(Binding.class);

        assertCreateAuthorization(binding, Operation.BIND, ObjectType.EXCHANGE, properties, exchange, queue);
    }


    public void testAuthoriseMethod()
    {
        ObjectProperties properties = new ObjectProperties("testMethod");
        properties.put(ObjectProperties.Property.COMPONENT, "testComponent");
        properties.put(ObjectProperties.Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);

         configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseMethod(Operation.UPDATE, "testComponent", "testMethod", TEST_VIRTUAL_HOST);
        verify(_accessControl).authorise(eq(Operation.UPDATE), eq(ObjectType.METHOD), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseMethod(Operation.UPDATE, "testComponent", "testMethod", TEST_VIRTUAL_HOST);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.UPDATE), eq(ObjectType.METHOD), eq(properties));
    }

    public void testAccessManagement()
    {
         configureAccessPlugin(Result.ALLOWED);
        _securityManager.accessManagement();
        verify(_accessControl).authorise(Operation.ACCESS, ObjectType.MANAGEMENT, ObjectProperties.EMPTY);

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.accessManagement();
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(Operation.ACCESS, ObjectType.MANAGEMENT, ObjectProperties.EMPTY);
    }

    public void testAuthoriseCreateConnection()
    {
        AMQConnectionModel<?,?> connection = mock(AMQConnectionModel.class);
        when(connection.getVirtualHostName()).thenReturn(TEST_VIRTUAL_HOST);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseCreateConnection(connection);
        verify(_accessControl).authorise(eq(Operation.ACCESS), eq(ObjectType.VIRTUALHOST), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseCreateConnection(connection);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.ACCESS), eq(ObjectType.VIRTUALHOST), eq(properties));
    }

    public void testAuthoriseCreateConsumer()
    {
        Queue queue = mock(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(true);
        when(queue.getAttribute(Queue.LIFETIME_POLICY)).thenReturn(LifetimePolicy.PERMANENT);
        when(queue.getAttribute(Queue.EXCLUSIVE)).thenReturn(ExclusivityPolicy.NONE);
        when(queue.getCategoryClass()).thenReturn(Queue.class);

        Session session = mock(Session.class);
        when(session.getCategoryClass()).thenReturn(Session.class);
        when(session.getAttribute(Session.NAME)).thenReturn("1");

        QueueConsumer consumer = mock(QueueConsumer.class);
        when(consumer.getAttribute(QueueConsumer.NAME)).thenReturn("1");
        when(consumer.getParent(Queue.class)).thenReturn(queue);
        when(consumer.getParent(Session.class)).thenReturn(session);
        when(consumer.getCategoryClass()).thenReturn(Consumer.class);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_QUEUE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.AUTO_DELETE, false);
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);
        properties.put(Property.EXCLUSIVE, false);

        assertAuthorization(Operation.CREATE, consumer, Operation.CONSUME, ObjectType.QUEUE, properties, queue, session);
    }

    public void testAuthoriseUserOperation()
    {
        ObjectProperties properties = new ObjectProperties("testUser");

        configureAccessPlugin(Result.ALLOWED);
       _securityManager.authoriseUserUpdate("testUser");
       verify(_accessControl).authorise(eq(Operation.UPDATE), eq(ObjectType.USER), eq(properties));

       configureAccessPlugin(Result.DENIED);
       try
       {
           _securityManager.authoriseUserUpdate("testUser");
           fail("AccessControlException is expected");
       }
       catch(AccessControlException e)
       {
           // pass
       }
       verify(_accessControl, times(2)).authorise(eq(Operation.UPDATE), eq(ObjectType.USER), eq(properties));
    }

    public void testAuthoriseCreateExchange()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedExchangeObjectProperties();

        Exchange exchange = mock(Exchange.class);
        when(exchange.getAttribute(ConfiguredObject.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getAttribute(ConfiguredObject.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(exchange.getAttribute(Exchange.DURABLE)).thenReturn(false);
        when(exchange.getAttribute(Exchange.TYPE)).thenReturn(TEST_EXCHANGE_TYPE);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(vh);

        assertCreateAuthorization( exchange, Operation.CREATE, ObjectType.EXCHANGE, expectedProperties, vh);
    }

    public void testAuthoriseCreateQueue()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedQueueObjectProperties();

        Queue queue = mock(Queue.class);
        when(queue.getAttribute(ConfiguredObject.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getAttribute(ConfiguredObject.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(queue.getAttribute(Queue.OWNER)).thenReturn(null);
        when(queue.getAttribute(Queue.EXCLUSIVE)).thenReturn(ExclusivityPolicy.NONE);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(false);
        when(queue.getAttribute(Queue.ALTERNATE_EXCHANGE)).thenReturn(null);
        when(queue.getCategoryClass()).thenReturn(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(vh);

        assertCreateAuthorization(queue, Operation.CREATE, ObjectType.QUEUE, expectedProperties, vh);
    }

    public void testAuthoriseDeleteQueue()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedQueueObjectProperties();

        Queue queueObject = mock(Queue.class);
        when(queueObject.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queueObject.getAttribute(ConfiguredObject.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(queueObject.getAttribute(Queue.OWNER)).thenReturn(null);
        when(queueObject.getAttribute(Queue.EXCLUSIVE)).thenReturn(ExclusivityPolicy.NONE);
        when(queueObject.getAttribute(Queue.DURABLE)).thenReturn(false);
        when(queueObject.getParent(VirtualHost.class)).thenReturn(vh);
        when(queueObject.getCategoryClass()).thenReturn(Queue.class);

        assertDeleteAuthorization(queueObject, Operation.DELETE, ObjectType.QUEUE, expectedProperties, vh);
    }

    public void testAuthoriseUpdateQueue()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedQueueObjectProperties();

        Queue queueObject = mock(Queue.class);
        when(queueObject.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queueObject.getAttribute(ConfiguredObject.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(queueObject.getAttribute(Queue.OWNER)).thenReturn(null);
        when(queueObject.getAttribute(Queue.EXCLUSIVE)).thenReturn(ExclusivityPolicy.NONE);
        when(queueObject.getAttribute(Queue.DURABLE)).thenReturn(false);
        when(queueObject.getParent(VirtualHost.class)).thenReturn(vh);
        when(queueObject.getCategoryClass()).thenReturn(Queue.class);

        assertUpdateAuthorization(queueObject, Operation.UPDATE, ObjectType.QUEUE, expectedProperties, vh);
    }

    public void testAuthoriseUpdateExchange()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedExchangeObjectProperties();

        Exchange exchange = mock(Exchange.class);
        when(exchange.getAttribute(Exchange.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getAttribute(Exchange.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(exchange.getAttribute(Exchange.DURABLE)).thenReturn(false);
        when(exchange.getAttribute(Exchange.TYPE)).thenReturn(TEST_EXCHANGE_TYPE);
        when(exchange.getParent(VirtualHost.class)).thenReturn(vh);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);

        assertUpdateAuthorization(exchange, Operation.UPDATE, ObjectType.EXCHANGE, expectedProperties, vh);
    }

    public void testAuthoriseDeleteExchange()
    {
        VirtualHost vh = getMockVirtualHost();
        ObjectProperties expectedProperties = createExpectedExchangeObjectProperties();

        Exchange exchange = mock(Exchange.class);
        when(exchange.getAttribute(Exchange.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getAttribute(Exchange.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);
        when(exchange.getAttribute(Exchange.DURABLE)).thenReturn(false);
        when(exchange.getAttribute(Exchange.TYPE)).thenReturn(TEST_EXCHANGE_TYPE);
        when(exchange.getParent(VirtualHost.class)).thenReturn(vh);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);

        assertDeleteAuthorization(exchange, Operation.DELETE, ObjectType.EXCHANGE, expectedProperties, vh);
    }

    public void testAuthorisePublish()
    {
        String routingKey = "routingKey";
        String exchangeName = "exchangeName";
        boolean immediate = true;
        ObjectProperties properties = new ObjectProperties(TEST_VIRTUAL_HOST, exchangeName, routingKey, immediate);

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authorisePublish(immediate, routingKey, exchangeName, TEST_VIRTUAL_HOST);
        verify(_accessControl).authorise(eq(Operation.PUBLISH), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authorisePublish(immediate, routingKey, exchangeName, TEST_VIRTUAL_HOST);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.PUBLISH), eq(ObjectType.EXCHANGE), eq(properties));
    }

    public void testAuthorisePurge()
    {
        Queue queue = mock(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getCategoryClass()).thenReturn(Queue.class);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(false);
        when(queue.getAttribute(Queue.EXCLUSIVE)).thenReturn(ExclusivityPolicy.NONE);
        when(queue.getAttribute(Queue.LIFETIME_POLICY)).thenReturn(LifetimePolicy.DELETE_ON_CONNECTION_CLOSE);

        ObjectProperties properties = createExpectedQueueObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authorisePurge(queue);
        verify(_accessControl).authorise(eq(Operation.PURGE), eq(ObjectType.QUEUE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authorisePurge(queue);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.PURGE), eq(ObjectType.QUEUE), eq(properties));
    }

    public void testAuthoriseUnbind()
    {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getAttribute(Exchange.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);

        Queue queue = mock(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(true);
        when(queue.getAttribute(Queue.LIFETIME_POLICY)).thenReturn(LifetimePolicy.PERMANENT);
        when(queue.getCategoryClass()).thenReturn(Queue.class);

        Binding binding = mock(Binding.class);
        when(binding.getParent(Exchange.class)).thenReturn(exchange);
        when(binding.getParent(Queue.class)).thenReturn(queue);
        when(binding.getAttribute(Binding.NAME)).thenReturn("bindingKey");
        when(binding.getCategoryClass()).thenReturn(Binding.class);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.QUEUE_NAME, TEST_QUEUE);
        properties.put(Property.ROUTING_KEY, "bindingKey");
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);

        assertDeleteAuthorization(binding, Operation.UNBIND, ObjectType.EXCHANGE, properties, exchange, queue);
    }

    public void testAuthoriseCreateVirtualHostNode()
    {
        VirtualHostNode vhn = getMockVirtualHostNode();
        assertCreateAuthorization(vhn, Operation.CREATE, ObjectType.VIRTUALHOSTNODE, new ObjectProperties("testVHN"), _broker);
    }

    public void testAuthoriseCreatePort()
    {
        Port port = mock(Port.class);
        when(port.getParent(Broker.class)).thenReturn(_broker);
        when(port.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(port.getCategoryClass()).thenReturn(Port.class);

        assertBrokerChildCreateAuthorization(port);
    }

    public void testAuthoriseCreateAuthenticationProvider()
    {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.getParent(Broker.class)).thenReturn(_broker);
        when(authenticationProvider.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(authenticationProvider.getCategoryClass()).thenReturn(AuthenticationProvider.class);

        assertBrokerChildCreateAuthorization(authenticationProvider);
    }

    public void testAuthoriseCreateGroupProvider()
    {
        GroupProvider groupProvider = mock(GroupProvider.class);
        when(groupProvider.getParent(Broker.class)).thenReturn(_broker);
        when(groupProvider.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(groupProvider.getCategoryClass()).thenReturn(GroupProvider.class);

        assertBrokerChildCreateAuthorization(groupProvider);
    }

    public void testAuthoriseCreateAccessControlProvider()
    {
        AccessControlProvider accessControlProvider = mock(AccessControlProvider.class);
        when(accessControlProvider.getParent(Broker.class)).thenReturn(_broker);
        when(accessControlProvider.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(accessControlProvider.getCategoryClass()).thenReturn(AccessControlProvider.class);

        assertBrokerChildCreateAuthorization(accessControlProvider);
    }

    public void testAuthoriseCreateKeyStore()
    {
        KeyStore keyStore = mock(KeyStore.class);
        when(keyStore.getParent(Broker.class)).thenReturn(_broker);
        when(keyStore.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(keyStore.getCategoryClass()).thenReturn(KeyStore.class);

        assertBrokerChildCreateAuthorization(keyStore);
    }

    public void testAuthoriseCreateTrustStore()
    {
        TrustStore trustStore = mock(TrustStore.class);
        when(trustStore.getParent(Broker.class)).thenReturn(_broker);
        when(trustStore.getAttribute(ConfiguredObject.NAME)).thenReturn("TEST");
        when(trustStore.getCategoryClass()).thenReturn(TrustStore.class);

        assertBrokerChildCreateAuthorization(trustStore);
    }

    public void testAuthoriseCreateGroup()
    {
        GroupProvider groupProvider = mock(GroupProvider.class);
        when(groupProvider.getCategoryClass()).thenReturn(GroupProvider.class);
        when(groupProvider.getAttribute(GroupProvider.NAME)).thenReturn("testGroupProvider");
        when(groupProvider.getModel()).thenReturn(BrokerModel.getInstance());

        Group group = mock(Group.class);
        when(group.getCategoryClass()).thenReturn(Group.class);
        when(group.getParent(GroupProvider.class)).thenReturn(groupProvider);
        when(group.getAttribute(Group.NAME)).thenReturn("test");

        assertCreateAuthorization(group, Operation.CREATE, ObjectType.GROUP, new ObjectProperties("test"), groupProvider);
    }

    public void testAuthoriseCreateGroupMember()
    {
        Group group = mock(Group.class);
        when(group.getCategoryClass()).thenReturn(Group.class);
        when(group.getAttribute(Group.NAME)).thenReturn("testGroup");
        when(group.getModel()).thenReturn(BrokerModel.getInstance());

        GroupMember groupMember = mock(GroupMember.class);
        when(groupMember.getCategoryClass()).thenReturn(GroupMember.class);
        when(groupMember.getParent(Group.class)).thenReturn(group);
        when(groupMember.getAttribute(Group.NAME)).thenReturn("test");

        assertCreateAuthorization(groupMember, Operation.UPDATE, ObjectType.GROUP, new ObjectProperties("test"), group);
    }

    public void testAuthoriseCreateUser()
    {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.getCategoryClass()).thenReturn(AuthenticationProvider.class);
        when(authenticationProvider.getAttribute(AuthenticationProvider.NAME)).thenReturn("testAuthenticationProvider");
        when(authenticationProvider.getModel()).thenReturn(BrokerModel.getInstance());

        User user = mock(User.class);
        when(user.getCategoryClass()).thenReturn(User.class);
        when(user.getAttribute(User.NAME)).thenReturn("test");
        when(user.getParent(AuthenticationProvider.class)).thenReturn(authenticationProvider);
        when(user.getModel()).thenReturn(BrokerModel.getInstance());

        assertCreateAuthorization(user, Operation.CREATE, ObjectType.USER, new ObjectProperties("test"), authenticationProvider);
    }

    public void testAuthoriseCreateVirtualHost()
    {
        VirtualHost vh = getMockVirtualHost();
        assertCreateAuthorization(vh, Operation.CREATE, ObjectType.VIRTUALHOST, new ObjectProperties(TEST_VIRTUAL_HOST), _virtualHostNode);
    }

    public void testAuthoriseUpdateVirtualHostNode()
    {
        VirtualHostNode vhn = getMockVirtualHostNode();
        assertUpdateAuthorization(vhn, Operation.UPDATE, ObjectType.VIRTUALHOSTNODE, new ObjectProperties(vhn.getName()), vhn);
    }

    public void testAuthoriseUpdatePort()
    {
        Port mock = mock(Port.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(Port.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateAuthenticationProvider()
    {
        AuthenticationProvider mock = mock(AuthenticationProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(AuthenticationProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateGroupProvider()
    {
        GroupProvider mock = mock(GroupProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(GroupProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateAccessControlProvider()
    {
        AccessControlProvider mock = mock(AccessControlProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(AccessControlProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateKeyStore()
    {
        KeyStore mock = mock(KeyStore.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(KeyStore.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateTrustStore()
    {
        TrustStore mock = mock(TrustStore.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(TrustStore.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildUpdateAuthorization(mock);
    }

    public void testAuthoriseUpdateGroup()
    {
        GroupProvider groupProvider = mock(GroupProvider.class);
        when(groupProvider.getCategoryClass()).thenReturn(GroupProvider.class);
        when(groupProvider.getName()).thenReturn("testGroupProvider");
        Group mock = mock(Group.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(Group.class);
        when(mock.getParent(GroupProvider.class)).thenReturn(groupProvider);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertUpdateAuthorization(mock, Operation.UPDATE, ObjectType.GROUP, properties, groupProvider);
    }

    public void testAuthoriseUpdateGroupMember()
    {
        Group group = mock(Group.class);
        when(group.getCategoryClass()).thenReturn(Group.class);
        when(group.getName()).thenReturn("testGroup");
        GroupMember mock = mock(GroupMember.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(GroupMember.class);
        when(mock.getParent(Group.class)).thenReturn(group);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertUpdateAuthorization(mock, Operation.UPDATE, ObjectType.GROUP, properties, group);
    }

    public void testAuthoriseUpdateUser()
    {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.getCategoryClass()).thenReturn(AuthenticationProvider.class);
        when(authenticationProvider.getName()).thenReturn("testAuthenticationProvider");
        User mock = mock(User.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(User.class);
        when(mock.getParent(AuthenticationProvider.class)).thenReturn(authenticationProvider);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertUpdateAuthorization(mock, Operation.UPDATE, ObjectType.USER, properties, authenticationProvider);
    }

    public void testAuthoriseUpdateVirtualHost()
    {
        VirtualHostNode vhn = getMockVirtualHostNode();

        VirtualHost mock = mock(VirtualHost.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(VirtualHost.class);
        when(mock.getParent(VirtualHostNode.class)).thenReturn(vhn);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertUpdateAuthorization(mock, Operation.UPDATE, ObjectType.VIRTUALHOST, properties, vhn);
    }

    public void testAuthoriseDeleteVirtualHostNode()
    {
        VirtualHostNode vhn = getMockVirtualHostNode();
        assertDeleteAuthorization(vhn, Operation.DELETE, ObjectType.VIRTUALHOSTNODE, new ObjectProperties(vhn.getName()), vhn);
    }

    public void testAuthoriseDeletePort()
    {
        Port mock = mock(Port.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(Port.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteAuthenticationProvider()
    {
        AuthenticationProvider mock = mock(AuthenticationProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(AuthenticationProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteGroupProvider()
    {
        GroupProvider mock = mock(GroupProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(GroupProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteAccessControlProvider()
    {
        AccessControlProvider mock = mock(AccessControlProvider.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(AccessControlProvider.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteKeyStore()
    {
        KeyStore mock = mock(KeyStore.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(KeyStore.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteTrustStore()
    {
        TrustStore mock = mock(TrustStore.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(TrustStore.class);
        when(mock.getParent(Broker.class)).thenReturn(_broker);
        assertBrokerChildDeleteAuthorization(mock);
    }

    public void testAuthoriseDeleteGroup()
    {
        GroupProvider groupProvider = mock(GroupProvider.class);
        when(groupProvider.getCategoryClass()).thenReturn(GroupProvider.class);
        when(groupProvider.getName()).thenReturn("testGroupProvider");
        Group mock = mock(Group.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(Group.class);
        when(mock.getParent(GroupProvider.class)).thenReturn(groupProvider);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertDeleteAuthorization(mock, Operation.DELETE, ObjectType.GROUP, properties, groupProvider);
    }

    public void testAuthoriseDeleteGroupMember()
    {
        Group group = mock(Group.class);
        when(group.getCategoryClass()).thenReturn(Group.class);
        when(group.getName()).thenReturn("testGroup");
        GroupMember mock = mock(GroupMember.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(GroupMember.class);
        when(mock.getParent(Group.class)).thenReturn(group);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertDeleteAuthorization(mock, Operation.UPDATE, ObjectType.GROUP, properties, group);
    }

    public void testAuthoriseDeleteUser()
    {
        AuthenticationProvider authenticationProvider = mock(AuthenticationProvider.class);
        when(authenticationProvider.getCategoryClass()).thenReturn(AuthenticationProvider.class);
        when(authenticationProvider.getName()).thenReturn("testAuthenticationProvider");
        User mock = mock(User.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(User.class);
        when(mock.getParent(AuthenticationProvider.class)).thenReturn(authenticationProvider);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertDeleteAuthorization(mock, Operation.DELETE, ObjectType.USER, properties, authenticationProvider);
    }

    public void testAuthoriseDeleteVirtualHost()
    {
        VirtualHostNode vhn = getMockVirtualHostNode();

        VirtualHost mock = mock(VirtualHost.class);
        when(mock.getAttribute(ConfiguredObject.NAME)).thenReturn("test");
        when(mock.getCategoryClass()).thenReturn(VirtualHost.class);
        when(mock.getParent(VirtualHostNode.class)).thenReturn(vhn);
        ObjectProperties properties = new ObjectProperties((String)mock.getAttribute(ConfiguredObject.NAME));
        assertDeleteAuthorization(mock, Operation.DELETE, ObjectType.VIRTUALHOST, properties, vhn);
    }

    public void testAuthoriseDeleteBinding()
    {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getAttribute(Exchange.NAME)).thenReturn(TEST_EXCHANGE);
        when(exchange.getCategoryClass()).thenReturn(Exchange.class);

        Queue queue = mock(Queue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getAttribute(Queue.NAME)).thenReturn(TEST_QUEUE);
        when(queue.getAttribute(Queue.DURABLE)).thenReturn(true);
        when(queue.getAttribute(Queue.LIFETIME_POLICY)).thenReturn(LifetimePolicy.PERMANENT);
        when(queue.getCategoryClass()).thenReturn(Queue.class);

        Binding binding = mock(Binding.class);
        when(binding.getParent(Exchange.class)).thenReturn(exchange);
        when(binding.getParent(Queue.class)).thenReturn(queue);
        when(binding.getAttribute(Binding.NAME)).thenReturn("bindingKey");
        when(binding.getCategoryClass()).thenReturn(Binding.class);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.QUEUE_NAME, TEST_QUEUE);
        properties.put(Property.ROUTING_KEY, "bindingKey");
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);

        assertDeleteAuthorization(binding, Operation.UNBIND, ObjectType.EXCHANGE, properties, exchange, queue);
    }

    private VirtualHost getMockVirtualHost()
    {
        VirtualHost vh = mock(VirtualHost.class);
        when(vh.getCategoryClass()).thenReturn(VirtualHost.class);
        when(vh.getName()).thenReturn(TEST_VIRTUAL_HOST);
        when(vh.getAttribute(VirtualHost.NAME)).thenReturn(TEST_VIRTUAL_HOST);
        when(vh.getParent(VirtualHostNode.class)).thenReturn(_virtualHostNode);
        when(vh.getModel()).thenReturn(BrokerModel.getInstance());
        return vh;
    }

    private VirtualHostNode getMockVirtualHostNode()
    {
        VirtualHostNode vhn = mock(VirtualHostNode.class);
        when(vhn.getCategoryClass()).thenReturn(VirtualHostNode.class);
        when(vhn.getName()).thenReturn("testVHN");
        when(vhn.getAttribute(ConfiguredObject.NAME)).thenReturn("testVHN");
        when(vhn.getParent(Broker.class)).thenReturn(_broker);
        when(vhn.getModel()).thenReturn(BrokerModel.getInstance());
        return vhn;
    }

    private void assertBrokerChildCreateAuthorization(ConfiguredObject object)
    {
        String description = String.format("%s %s '%s'",
                Operation.CREATE.name().toLowerCase(),
                object.getCategoryClass().getSimpleName().toLowerCase(),
                "TEST");
        ObjectProperties properties = new OperationLoggingDetails(description);
        assertCreateAuthorization(object, Operation.CONFIGURE, ObjectType.BROKER, properties, _broker );
    }

    private void assertBrokerChildUpdateAuthorization(ConfiguredObject configuredObject)
    {
        String description = String.format("%s %s '%s'",
                Operation.UPDATE.name().toLowerCase(),
                configuredObject.getCategoryClass().getSimpleName().toLowerCase(),
                configuredObject.getAttribute(ConfiguredObject.NAME));
        ObjectProperties properties = new OperationLoggingDetails(description);

        assertUpdateAuthorization(configuredObject, Operation.CONFIGURE, ObjectType.BROKER,
                properties, _broker );
    }

    private void assertBrokerChildDeleteAuthorization(ConfiguredObject configuredObject)
    {
        String description = String.format("%s %s '%s'",
                Operation.DELETE.name().toLowerCase(),
                configuredObject.getCategoryClass().getSimpleName().toLowerCase(),
                configuredObject.getAttribute(ConfiguredObject.NAME));
        ObjectProperties properties = new OperationLoggingDetails(description);

        assertDeleteAuthorization(configuredObject, Operation.CONFIGURE, ObjectType.BROKER,
                properties, _broker );
    }

    private void assertAuthorization(Operation operation, ConfiguredObject<?> configuredObject, Operation aclOperation, ObjectType aclObjectType, ObjectProperties expectedProperties, ConfiguredObject... objects)
    {
        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authorise(operation, configuredObject);
        verify(_accessControl).authorise(eq(aclOperation), eq(aclObjectType), eq(expectedProperties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authorise(operation, configuredObject);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            String expectedMessage = "Permission " + aclOperation.name() + " "
                    + aclObjectType.name() +" is denied for : " + operation.name() + " "
                    + configuredObject.getCategoryClass().getSimpleName() + " '"
                    + configuredObject.getAttribute(ConfiguredObject.NAME) + "' on";

            assertTrue("Unexpected exception message: " + e.getMessage() + " vs " + expectedMessage,
                    e.getMessage().startsWith(expectedMessage));
            for (ConfiguredObject object: objects)
            {
                String parentInfo = object.getCategoryClass().getSimpleName() + " '"
                        + object.getAttribute(ConfiguredObject.NAME) + "'";
                assertTrue("Exception message does not contain information about parent object "
                                + object.getCategoryClass() + " " + object.getAttribute(ConfiguredObject.NAME) + ":"
                                + e.getMessage(),
                        e.getMessage().contains(parentInfo));
            }
        }

        verify(_accessControl, times(2)).authorise(eq(aclOperation), eq(aclObjectType), eq(expectedProperties));
    }

    private void assertDeleteAuthorization(ConfiguredObject<?> configuredObject, Operation aclOperation, ObjectType aclObjectType, ObjectProperties expectedProperties, ConfiguredObject... objects)
    {
        assertAuthorization(Operation.DELETE, configuredObject, aclOperation, aclObjectType, expectedProperties, objects);
    }

    private void assertUpdateAuthorization(ConfiguredObject<?> configuredObject, Operation aclOperation, ObjectType aclObjectType, ObjectProperties expectedProperties, ConfiguredObject... objects)
    {
        assertAuthorization(Operation.UPDATE, configuredObject, aclOperation, aclObjectType, expectedProperties, objects);
    }

    private void assertCreateAuthorization(ConfiguredObject<?> configuredObject, Operation aclOperation, ObjectType aclObjectType, ObjectProperties expectedProperties, ConfiguredObject<?>... parents)
    {
        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authorise(Operation.CREATE, configuredObject);
        verify(_accessControl).authorise(eq(aclOperation), eq(aclObjectType), eq(expectedProperties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authorise(Operation.CREATE, configuredObject);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            String expectedMessage = "Permission " + aclOperation.name() + " "
                    + aclObjectType.name() +" is denied for : CREATE " + configuredObject.getCategoryClass().getSimpleName() + " '"
                    + configuredObject.getAttribute(ConfiguredObject.NAME) + "' on";

            assertTrue("Unexpected exception message", e.getMessage().startsWith(expectedMessage));
            for (ConfiguredObject object: parents)
            {
                String parentInfo = object.getCategoryClass().getSimpleName() + " '"
                        + object.getAttribute(ConfiguredObject.NAME) + "'";
                assertTrue("Exception message does not contain information about parent configuredObject "
                                + parentInfo + ": "
                                + e.getMessage(),
                        e.getMessage().contains(parentInfo));
            }
        }

        verify(_accessControl, times(2)).authorise(eq(aclOperation), eq(aclObjectType), eq(expectedProperties));
    }

    public void testAuthoriseLogsAccess()
    {
        configureAccessPlugin(Result.ALLOWED);
        assertTrue(_securityManager.authoriseLogsAccess());
        verify(_accessControl).authorise(ACCESS_LOGS, BROKER, ObjectProperties.EMPTY);

        configureAccessPlugin(Result.DENIED);
        assertFalse(_securityManager.authoriseLogsAccess());
        verify(_accessControl, times(2)).authorise(ACCESS_LOGS, BROKER, ObjectProperties.EMPTY);
    }

    private void configureAccessPlugin(Result result)
    {
        when(_accessControl.authorise(any(Operation.class), any(ObjectType.class), any(ObjectProperties.class))).thenReturn(result);
    }

    private ObjectProperties createExpectedExchangeObjectProperties()
    {
        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.AUTO_DELETE, true);
        properties.put(Property.TEMPORARY, true);
        properties.put(Property.DURABLE, false);
        properties.put(Property.TYPE, TEST_EXCHANGE_TYPE);
        return properties;
    }

    private ObjectProperties createExpectedQueueObjectProperties()
    {
        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_QUEUE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.AUTO_DELETE, true);
        properties.put(Property.TEMPORARY, true);
        properties.put(Property.DURABLE, false);
        properties.put(Property.EXCLUSIVE, false);
        return properties;
    }



}
