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
package org.apache.qpid.server.configuration.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.ProtocolExclusion;
import org.apache.qpid.server.ProtocolInclusion;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.transport.ConnectionSettings;

public class PortConfigurationHelperTest extends TestCase
{
    private ServerConfiguration _serverConfig;
    private int _port;
    private List<Integer> _ports;
    private PortConfigurationHelper _helper;

    protected void setUp() throws Exception
    {
        super.setUp();

        _port = 5679;
        _ports = Collections.singletonList(_port);
        _serverConfig = mock(ServerConfiguration.class);
        when(_serverConfig.getPorts()).thenReturn(_ports);

        _helper = new PortConfigurationHelper(null);
    }

    public void testPortConfigurationCLIExclude()
    {
        setEnabledAmqpProtocolsOnServerConfiguration(true);
        Set<ProtocolExclusion> exclusions = EnumSet.allOf(ProtocolExclusion.class);
        for (ProtocolExclusion protocolExclusion : exclusions)
        {
            BrokerOptions options = new BrokerOptions();
            options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
            options.addExcludedPort(protocolExclusion, _port);

            Map<UUID, ConfigurationEntry> entries = _helper.getPortConfiguration(_serverConfig, options);
            assertNotNull("Entries map is null", entries);
            assertEquals("Unexpected number of entries", 1, entries.size());
            ConfigurationEntry entry = entries.values().iterator().next();

            Map<String, Object> attributes = entry.getAttributes();
            assertNotNull("Entry attributes are null", attributes);

            @SuppressWarnings("unchecked")
            Set<Protocol> supported = (Set<Protocol>) attributes.get(Port.PROTOCOLS);
            assertNotNull("No protocols found", supported);

            final Set<Protocol> expected = getAllAmqpProtocolsExcept(protocolExclusion);

            assertEquals("Unexpected protocols", expected, supported);
        }
    }

    public void testPortConfigurationCLIInclude()
    {
        setEnabledAmqpProtocolsOnServerConfiguration(false);
        Set<ProtocolInclusion> inclusions = EnumSet.allOf(ProtocolInclusion.class);
        for (ProtocolInclusion protocolInclusion : inclusions)
        {
            BrokerOptions options = new BrokerOptions();
            options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
            options.addIncludedPort(protocolInclusion, _port);

            Map<UUID, ConfigurationEntry> entries = _helper.getPortConfiguration(_serverConfig, options);
            assertNotNull("Entries map is null", entries);
            assertEquals("Unexpected number of entries", 1, entries.size());
            ConfigurationEntry entry = entries.values().iterator().next();

            Map<String, Object> attributes = entry.getAttributes();
            assertNotNull("Entry attributes are null", attributes);

            @SuppressWarnings("unchecked")
            Set<Protocol> supported = (Set<Protocol>) attributes.get(Port.PROTOCOLS);
            assertNotNull("No protocols found", supported);

            Protocol protocol = toProtocol(protocolInclusion);
            final Set<Protocol> expected = EnumSet.of(protocol);

            assertEquals("Unexpected protocols", expected, supported);
        }

    }

    public void testCLIPortOverride()
    {
        int cliPort = 9876;
        BrokerOptions options = new BrokerOptions();
        options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
        options.addPort(cliPort);

        Map<UUID, ConfigurationEntry> entries = _helper.getPortConfiguration(_serverConfig, options);
        assertNotNull("Entries map is null", entries);
        assertEquals("Unexpected number of entries", 1, entries.size());
        ConfigurationEntry entry = entries.values().iterator().next();

        Map<String, Object> attributes = entry.getAttributes();
        assertNotNull("Entry attributes are null", attributes);

        Integer port = (Integer) attributes.get(Port.PORT);
        assertNotNull("No port found", port);

        @SuppressWarnings("unchecked")
        Collection<Transport> transports = (Collection<Transport>) attributes.get(Port.TRANSPORTS);
        assertEquals("Unexpected transports", Collections.singleton(Transport.TCP), transports);
    }

    public void testCLISSLPortOverride()
    {
        when(_serverConfig.getEnableSSL()).thenReturn(true);
        when(_serverConfig.getSSLOnly()).thenReturn(true);

        int cliPort = 9876;
        BrokerOptions options = new BrokerOptions();
        options.setBind(ConnectionSettings.WILDCARD_ADDRESS);
        options.addSSLPort(cliPort);

        Map<UUID, ConfigurationEntry> entries = _helper.getPortConfiguration(_serverConfig, options);
        assertNotNull("Entries map is null", entries);
        assertEquals("Unexpected number of entries", 1, entries.size());
        Iterator<ConfigurationEntry> iterator = entries.values().iterator();

        ConfigurationEntry entry = iterator.next();

        Map<String, Object> attributes = entry.getAttributes();
        assertNotNull("Entry attributes are null", attributes);

        Integer port = (Integer) attributes.get(Port.PORT);
        assertNotNull("No port found", port);

        assertEquals("Unexpected port", cliPort, port.intValue());

        @SuppressWarnings("unchecked")
        Collection<Transport> transports = (Collection<Transport>) attributes.get(Port.TRANSPORTS);
        assertEquals("Unexpected transports", Collections.singleton(Transport.SSL), transports);
    }

    private void setEnabledAmqpProtocolsOnServerConfiguration(boolean isEnabled)
    {
        when(_serverConfig.isAmqp010enabled()).thenReturn(isEnabled);
        when(_serverConfig.isAmqp08enabled()).thenReturn(isEnabled);
        when(_serverConfig.isAmqp09enabled()).thenReturn(isEnabled);
        when(_serverConfig.isAmqp091enabled()).thenReturn(isEnabled);
        when(_serverConfig.isAmqp10enabled()).thenReturn(isEnabled);
    }

    private Set<Protocol> getAllAmqpProtocolsExcept(ProtocolExclusion excludedProtocol)
    {
        Set<Protocol> protocols = EnumSet.allOf(Protocol.class);
        Protocol excluded = toProtocol(excludedProtocol);
        protocols.remove(excluded);
        protocols.remove(Protocol.HTTP);
        protocols.remove(Protocol.HTTPS);
        protocols.remove(Protocol.JMX_RMI);
        protocols.remove(Protocol.RMI);
        return protocols;
    }

    private Protocol toProtocol(ProtocolExclusion excludedProtocol)
    {
        switch (excludedProtocol)
        {
        case v0_8:
            return Protocol.AMQP_0_8;
        case v0_9:
            return Protocol.AMQP_0_9;
        case v0_9_1:
            return Protocol.AMQP_0_9_1;
        case v0_10:
            return Protocol.AMQP_0_10;
        case v1_0:
            return Protocol.AMQP_1_0;
        }
        throw new RuntimeException("Unsupported " + excludedProtocol);
    }

    private Protocol toProtocol(ProtocolInclusion protocolInclusion)
    {
        switch (protocolInclusion)
        {
        case v0_8:
            return Protocol.AMQP_0_8;
        case v0_9:
            return Protocol.AMQP_0_9;
        case v0_9_1:
            return Protocol.AMQP_0_9_1;
        case v0_10:
            return Protocol.AMQP_0_10;
        case v1_0:
            return Protocol.AMQP_1_0;
        }
        throw new RuntimeException("Unsupported " + protocolInclusion);
    }

}
