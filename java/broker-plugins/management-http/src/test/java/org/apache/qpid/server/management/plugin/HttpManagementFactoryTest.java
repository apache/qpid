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
package org.apache.qpid.server.management.plugin;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.test.utils.QpidTestCase;

public class HttpManagementFactoryTest extends QpidTestCase
{
    private static final int SESSION_TIMEOUT = 3600;

    private PluginFactory _pluginFactory = new HttpManagementFactory();
    private Map<String, Object> _attributes = new HashMap<String, Object>();
    private Broker _broker = mock(Broker.class);
    private UUID _id = UUID.randomUUID();

    public void testCreateInstanceReturnsNullWhenPluginTypeMissing() throws Exception
    {
        assertNull(_pluginFactory.createInstance(_id, _attributes, _broker));
    }
    public void testCreateInstanceReturnsNullWhenPluginTypeNotHttp()
    {
        _attributes.put(PluginFactory.PLUGIN_TYPE, "notHttp");
        assertNull(_pluginFactory.createInstance(_id, _attributes, _broker));
    }

    public void testCreateInstance() throws Exception
    {
        _attributes.put(PluginFactory.PLUGIN_TYPE, HttpManagement.PLUGIN_TYPE);
        _attributes.put(HttpManagement.TIME_OUT, SESSION_TIMEOUT);

        HttpManagement management = (HttpManagement) _pluginFactory.createInstance(_id, _attributes, _broker);

        assertEquals(_broker, management.getBroker());
        assertEquals(SESSION_TIMEOUT, management.getSessionTimeout());
    }

}
