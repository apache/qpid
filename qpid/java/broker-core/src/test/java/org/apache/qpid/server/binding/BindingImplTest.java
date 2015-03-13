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
package org.apache.qpid.server.binding;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class BindingImplTest extends QpidTestCase
{
    private TaskExecutor _taskExecutor;
    private Model _model;

    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        _model = BrokerModel.getInstance();
    }

    public void testBindingValidationOnCreateWithInvalidSelector()
    {
        Map<String, String> arguments = new HashMap<>();
        arguments.put(AMQPFilterTypes.JMS_SELECTOR.toString(), "test in (");
        Map<String,Object> attributes = new HashMap<>();
        attributes.put(Binding.ARGUMENTS, arguments);
        attributes.put(Binding.NAME, getTestName());
        AMQQueue queue = mock(AMQQueue.class);
        VirtualHostImpl vhost = mock(VirtualHostImpl.class);
        SecurityManager securityManager = mock(SecurityManager.class);
        when(vhost.getSecurityManager()).thenReturn(securityManager);
        when(queue.getTaskExecutor()).thenReturn(_taskExecutor);
        when(queue.getChildExecutor()).thenReturn(_taskExecutor);
        when(queue.getVirtualHost()).thenReturn(vhost);
        when(queue.getModel()).thenReturn(_model);
        ExchangeImpl exchange = mock(ExchangeImpl.class);
        when(exchange.getTaskExecutor()).thenReturn(_taskExecutor);
        when(exchange.getChildExecutor()).thenReturn(_taskExecutor);
        when(exchange.getModel()).thenReturn(_model);
        BindingImpl binding = new BindingImpl(attributes, queue, exchange);
        try
        {
            binding.create();
            fail("Exception is expected on validation with invalid selector");
        }
        catch (IllegalConfigurationException e)
        {
            assertTrue("Unexpected exception message " + e.getMessage(), e.getMessage().startsWith("Cannot parse JMS selector"));
        }
    }
}
