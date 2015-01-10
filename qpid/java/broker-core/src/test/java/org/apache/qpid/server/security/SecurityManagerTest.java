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

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
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

        Broker broker = mock(Broker.class);
        when(broker.getAccessControlProviders()).thenReturn(Collections.singleton(aclProvider));
        _securityManager = new SecurityManager(broker, false);
    }

    public void testAuthoriseCreateBinding()
    {
        ExchangeImpl exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getName()).thenReturn(TEST_EXCHANGE);

        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);
        when(queue.isDurable()).thenReturn(true);
        when(queue.getLifetimePolicy()).thenReturn(LifetimePolicy.PERMANENT);

        BindingImpl binding = mock(BindingImpl.class);
        when(binding.getExchange()).thenReturn(exchange);
        when(binding.getAMQQueue()).thenReturn(queue);
        when(binding.getBindingKey()).thenReturn("bindingKey");

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.QUEUE_NAME, TEST_QUEUE);
        properties.put(Property.ROUTING_KEY, "bindingKey");
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);


        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseCreateBinding(binding);
        verify(_accessControl).authorise(eq(Operation.BIND), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseCreateBinding(binding);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.BIND), eq(ObjectType.EXCHANGE), eq(properties));
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
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);
        when(queue.isDurable()).thenReturn(true);
        when(queue.getLifetimePolicy()).thenReturn(LifetimePolicy.PERMANENT);

        ConsumerImpl consumer = mock(ConsumerImpl.class);
        when(consumer.getMessageSource()).thenReturn(queue);

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_QUEUE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.AUTO_DELETE, false);
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);
        properties.put(Property.EXCLUSIVE, false);

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseCreateConsumer(consumer);
        verify(_accessControl).authorise(eq(Operation.CONSUME), eq(ObjectType.QUEUE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseCreateConsumer(consumer);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.CONSUME), eq(ObjectType.QUEUE), eq(properties));
    }

    public void testAuthoriseCreateExchange()
    {
        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getName()).thenReturn(TEST_EXCHANGE);
        when(exchange.getType()).thenReturn(TEST_EXCHANGE_TYPE);

        ObjectProperties properties = createExpectedExchangeObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseCreateExchange(exchange);
        verify(_accessControl).authorise(eq(Operation.CREATE), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseCreateExchange(exchange);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.CREATE), eq(ObjectType.EXCHANGE), eq(properties));
    }

    public void testAuthoriseCreateQueue()
    {
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);

        ObjectProperties properties = createExpectedQueueObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseCreateQueue(queue);
        verify(_accessControl).authorise(eq(Operation.CREATE), eq(ObjectType.QUEUE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseCreateQueue(queue);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.CREATE), eq(ObjectType.QUEUE), eq(properties));
    }

    public void testAuthoriseDeleteQueue()
    {
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);

        ObjectProperties properties = createExpectedQueueObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseDelete(queue);
        verify(_accessControl).authorise(eq(Operation.DELETE), eq(ObjectType.QUEUE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseDelete(queue);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.DELETE), eq(ObjectType.QUEUE), eq(properties));
    }

    public void testAuthoriseUpdateQueue()
    {
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);

        ObjectProperties properties = createExpectedQueueObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseUpdate(queue);
        verify(_accessControl).authorise(eq(Operation.UPDATE), eq(ObjectType.QUEUE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseUpdate(queue);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.UPDATE), eq(ObjectType.QUEUE), eq(properties));
    }

    public void testAuthoriseUpdateExchange()
    {
        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getName()).thenReturn(TEST_EXCHANGE);
        when(exchange.getType()).thenReturn(TEST_EXCHANGE_TYPE);

        ObjectProperties properties = createExpectedExchangeObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseUpdate(exchange);
        verify(_accessControl).authorise(eq(Operation.UPDATE), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseUpdate(exchange);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.UPDATE), eq(ObjectType.EXCHANGE), eq(properties));
    }

    public void testAuthoriseDeleteExchange()
    {
        ExchangeImpl<?> exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getName()).thenReturn(TEST_EXCHANGE);
        when(exchange.getType()).thenReturn(TEST_EXCHANGE_TYPE);

        ObjectProperties properties = createExpectedExchangeObjectProperties();

        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseDelete(exchange);
        verify(_accessControl).authorise(eq(Operation.DELETE), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseDelete(exchange);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.DELETE), eq(ObjectType.EXCHANGE), eq(properties));
    }

    public void testAuthoriseGroupOperation()
    {
        ObjectProperties properties = new ObjectProperties("testGroup");

         configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseGroupOperation(Operation.CREATE, "testGroup");
        verify(_accessControl).authorise(eq(Operation.CREATE), eq(ObjectType.GROUP), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseGroupOperation(Operation.CREATE, "testGroup");
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.CREATE), eq(ObjectType.GROUP), eq(properties));
    }

    public void testAuthoriseUserOperation()
    {
        ObjectProperties properties = new ObjectProperties("testUser");

        configureAccessPlugin(Result.ALLOWED);
       _securityManager.authoriseUserOperation(Operation.CREATE, "testUser");
       verify(_accessControl).authorise(eq(Operation.CREATE), eq(ObjectType.USER), eq(properties));

       configureAccessPlugin(Result.DENIED);
       try
       {
           _securityManager.authoriseUserOperation(Operation.CREATE, "testUser");
           fail("AccessControlException is expected");
       }
       catch(AccessControlException e)
       {
           // pass
       }
       verify(_accessControl, times(2)).authorise(eq(Operation.CREATE), eq(ObjectType.USER), eq(properties));
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
        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);

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
        ExchangeImpl exchange = mock(ExchangeImpl.class);
        when(exchange.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(exchange.getName()).thenReturn(TEST_EXCHANGE);

        AMQQueue<?> queue = mock(AMQQueue.class);
        when(queue.getParent(VirtualHost.class)).thenReturn(_virtualHost);
        when(queue.getName()).thenReturn(TEST_QUEUE);
        when(queue.isDurable()).thenReturn(true);
        when(queue.getLifetimePolicy()).thenReturn(LifetimePolicy.PERMANENT);

        BindingImpl binding = mock(BindingImpl.class);
        when(binding.getExchange()).thenReturn(exchange);
        when(binding.getAMQQueue()).thenReturn(queue);
        when(binding.getBindingKey()).thenReturn("bindingKey");

        ObjectProperties properties = new ObjectProperties();
        properties.put(Property.NAME, TEST_EXCHANGE);
        properties.put(Property.VIRTUALHOST_NAME, TEST_VIRTUAL_HOST);
        properties.put(Property.QUEUE_NAME, TEST_QUEUE);
        properties.put(Property.ROUTING_KEY, "bindingKey");
        properties.put(Property.TEMPORARY, false);
        properties.put(Property.DURABLE, true);


        configureAccessPlugin(Result.ALLOWED);
        _securityManager.authoriseUnbind(binding);
        verify(_accessControl).authorise(eq(Operation.UNBIND), eq(ObjectType.EXCHANGE), eq(properties));

        configureAccessPlugin(Result.DENIED);
        try
        {
            _securityManager.authoriseUnbind(binding);
            fail("AccessControlException is expected");
        }
        catch(AccessControlException e)
        {
            // pass
        }
        verify(_accessControl, times(2)).authorise(eq(Operation.UNBIND), eq(ObjectType.EXCHANGE), eq(properties));
    }

    public void testAuthoriseConfiguringBroker()
    {
        OperationLoggingDetails properties = new OperationLoggingDetails("create virtualhost 'test'");

        configureAccessPlugin(Result.ALLOWED);
        assertTrue(_securityManager.authoriseConfiguringBroker("test", VirtualHost.class, Operation.CREATE));
        verify(_accessControl).authorise(eq(Operation.CONFIGURE), eq(ObjectType.BROKER), eq(properties));

        configureAccessPlugin(Result.DENIED);
        assertFalse(_securityManager.authoriseConfiguringBroker("test", VirtualHost.class, Operation.CREATE));
        verify(_accessControl, times(2)).authorise(eq(Operation.CONFIGURE), eq(ObjectType.BROKER), eq(properties));
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
        properties.put(Property.AUTO_DELETE, false);
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
