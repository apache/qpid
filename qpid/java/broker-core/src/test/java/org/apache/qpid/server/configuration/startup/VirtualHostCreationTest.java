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
package org.apache.qpid.server.configuration.startup;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.StandardVirtualHost;

public class VirtualHostCreationTest extends TestCase
{
    private VirtualHostNode<?> _virtualHostNode;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        EventLogger eventLogger = mock(EventLogger.class);
        SecurityManager securityManager = mock(SecurityManager.class);

        SystemContext systemContext = mock(SystemContext.class);
        ConfiguredObjectFactory objectFactory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        when(systemContext.getObjectFactory()).thenReturn(objectFactory);
        when(systemContext.getModel()).thenReturn(objectFactory.getModel());
        when(systemContext.getEventLogger()).thenReturn(eventLogger);

        Broker broker = mock(Broker.class);
        when(broker.getObjectFactory()).thenReturn(objectFactory);
        when(broker.getModel()).thenReturn(objectFactory.getModel());
        when(broker.getSecurityManager()).thenReturn(securityManager);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        when(broker.getParent(eq(SystemContext.class))).thenReturn(systemContext);

        _virtualHostNode = mock(VirtualHostNode.class);
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(broker);
        when(_virtualHostNode.getObjectFactory()).thenReturn(objectFactory);
        when(_virtualHostNode.getConfigurationStore()).thenReturn(mock(DurableConfigurationStore.class));
        when(_virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
    }

    public void testCreateVirtualHostFromStoreConfigAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.TYPE, StandardVirtualHost.TYPE);
        attributes.put(VirtualHost.ID, UUID.randomUUID());

        attributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, Collections.singletonMap(MessageStore.STORE_TYPE, TestMemoryMessageStore.TYPE));

        VirtualHost<?,?,?> host = new StandardVirtualHost(attributes, _virtualHostNode);
        host.open();

        assertNotNull("Null is returned", host);
        assertEquals("Unexpected name", getName(), host.getName());
    }

    public void testCreateWithoutMandatoryAttributesResultsInException()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.TYPE, StandardVirtualHost.TYPE);
        attributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, Collections.singletonMap(MessageStore.STORE_TYPE,
                                                                                    TestMemoryMessageStore.TYPE));
        String[] mandatoryAttributes = {VirtualHost.NAME, VirtualHost.MESSAGE_STORE_SETTINGS};

        checkMandatoryAttributesAreValidated(mandatoryAttributes, attributes);
    }

    public void checkMandatoryAttributesAreValidated(String[] mandatoryAttributes, Map<String, Object> attributes)
    {
        for (String name : mandatoryAttributes)
        {
            Map<String, Object> copy = new HashMap<String, Object>(attributes);
            copy.remove(name);
            copy.put(ConfiguredObject.ID, UUID.randomUUID());
            try
            {
                AbstractVirtualHost<StandardVirtualHost> host = new StandardVirtualHost(copy,_virtualHostNode);
                host.open();
                fail("Cannot create a virtual host without a mandatory attribute " + name);
            }
            catch(IllegalConfigurationException e)
            {
                // pass
            }
            catch(IllegalArgumentException e)
            {
                // pass
            }
        }
    }
}
