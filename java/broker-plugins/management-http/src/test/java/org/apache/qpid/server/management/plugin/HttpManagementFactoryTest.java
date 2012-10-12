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
import static org.mockito.Mockito.when;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidTestCase;

public class HttpManagementFactoryTest extends QpidTestCase
{
    private static final String KEY_STORE_PASSWORD = "keyStorePassword";
    private static final String KEY_STORE_PATH = "keyStorePath";
    private static final int SESSION_TIMEOUT = 3600;

    private HttpManagementFactory _managementFactory = new HttpManagementFactory();
    private ServerConfiguration _configuration = mock(ServerConfiguration.class);
    private Broker _broker = mock(Broker.class);

    public void testNoHttpManagementConfigured() throws Exception
    {
        ManagementPlugin management = _managementFactory.createInstance(_configuration, _broker);
        assertNull(management);
    }

    public void testHttpTransportConfigured() throws Exception
    {
        when(_configuration.getHTTPManagementEnabled()).thenReturn(true);
        when(_configuration.getHTTPSManagementEnabled()).thenReturn(false);

        when(_configuration.getManagementKeyStorePassword()).thenReturn(null);
        when(_configuration.getManagementKeyStorePath()).thenReturn(null);

        when(_configuration.getHTTPManagementSessionTimeout()).thenReturn(SESSION_TIMEOUT);

        HttpManagement management = _managementFactory.createInstance(_configuration, _broker);

        assertNotNull(management);
        assertEquals(_broker, management.getBroker());
        assertNull(management.getKeyStorePassword());
        assertNull(management.getKeyStorePath());
        assertEquals(SESSION_TIMEOUT, management.getSessionTimeout());

    }

    public void testHttpsTransportConfigured() throws Exception
    {
        when(_configuration.getHTTPManagementEnabled()).thenReturn(false);
        when(_configuration.getHTTPSManagementEnabled()).thenReturn(true);

        when(_configuration.getManagementKeyStorePassword()).thenReturn(KEY_STORE_PASSWORD);
        when(_configuration.getManagementKeyStorePath()).thenReturn(KEY_STORE_PATH);

        when(_configuration.getHTTPManagementSessionTimeout()).thenReturn(SESSION_TIMEOUT);

        HttpManagement management = _managementFactory.createInstance(_configuration, _broker);

        assertNotNull(management);
        assertEquals(_broker, management.getBroker());
        assertEquals(KEY_STORE_PASSWORD, management.getKeyStorePassword());
        assertEquals(KEY_STORE_PATH, management.getKeyStorePath());
        assertEquals(SESSION_TIMEOUT, management.getSessionTimeout());
    }

}
