/*
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
 */
package org.apache.qpid.server.jmx;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class JMXManagementFactoryTest extends QpidTestCase
{
    private final JMXManagementFactory _jmxManagementFactory = new JMXManagementFactory();
    private final Map<String, Object> _attributes = new HashMap<String, Object>();
    private final Broker _broker = mock(Broker.class);
    private UUID _id = UUID.randomUUID();

    public void testJMXConfigured() throws Exception
    {
        _attributes.put(ConfiguredObject.ID,UUID.randomUUID());
        _attributes.put(ConfiguredObject.TYPE, JMXManagementPlugin.PLUGIN_TYPE);
        _attributes.put(ConfiguredObject.NAME, getName());
        ConfiguredObjectFactory objectFactory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());

        when(_broker.getObjectFactory()).thenReturn(objectFactory);
        when(_broker.getModel()).thenReturn(objectFactory.getModel());
        when(_broker.getCategoryClass()).thenReturn(Broker.class);

        JMXManagementPlugin jmxManagement = _jmxManagementFactory.createInstance( _attributes, _broker);
        jmxManagement.open();
        assertNotNull(jmxManagement);
        assertEquals("Unexpected plugin type", JMXManagementPlugin.PLUGIN_TYPE, jmxManagement.getType());
        assertEquals("Unexpected default mbean platform", Boolean.parseBoolean(JMXManagementPlugin.DEFAULT_USE_PLATFORM_MBEAN_SERVER), jmxManagement.getUsePlatformMBeanServer());
    }


}
