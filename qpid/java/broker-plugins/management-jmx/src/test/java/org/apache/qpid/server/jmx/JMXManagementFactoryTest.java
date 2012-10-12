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

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidTestCase;

public class JMXManagementFactoryTest extends QpidTestCase
{
    private final JMXManagementFactory _jmxManagementFactory = new JMXManagementFactory();
    private final ServerConfiguration _serverConfiguration = mock(ServerConfiguration.class);
    private final Broker _broker = mock(Broker.class);

    public void testJMXConfigured() throws Exception
    {
        when(_serverConfiguration.getJMXManagementEnabled()).thenReturn(true);

        JMXManagement jmxManagement = _jmxManagementFactory.createInstance(_serverConfiguration, _broker);

        assertNotNull(jmxManagement);
    }

    public void testJMXNotConfigured() throws Exception
    {
        when(_serverConfiguration.getJMXManagementEnabled()).thenReturn(false);

        JMXManagement jmxManagement = _jmxManagementFactory.createInstance(_serverConfiguration, _broker);

        assertNull(jmxManagement);
    }
}
