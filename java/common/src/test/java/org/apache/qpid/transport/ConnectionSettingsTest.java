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
package org.apache.qpid.transport;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConnectionSettingsTest extends QpidTestCase
{
    ConnectionSettings _conConnectionSettings;

    protected void setUp() throws Exception
    {
        super.setUp();
        _conConnectionSettings = new ConnectionSettings();
    }

    public void testDefaultTCP_NODELAY()
    {
        assertTrue("Default for isTcpNodelay() should be true", _conConnectionSettings.isTcpNodelay());
    }

    public void testSystemPropertyOverrideTrueForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, true);
    }
    
    public void testSystemPropertyOverrideFalseForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.QPID_TCP_NODELAY_PROP_NAME, false);
    }

    public void testLegacySystemPropertyOverrideTrueForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, true);
    }

    public void testLegacySystemPropertyOverrideFalseForTCP_NODELAY()
    {
        systemPropertyOverrideForTCP_NODELAYImpl(ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME, false);
    }

    private void systemPropertyOverrideForTCP_NODELAYImpl(String propertyName, boolean value)
    {
        //set the default via system property
        setTestSystemProperty(propertyName, String.valueOf(value));

        _conConnectionSettings = new ConnectionSettings();
        assertEquals("Value for isTcpNodelay() is incorrect", value, _conConnectionSettings.isTcpNodelay());
    }
}
