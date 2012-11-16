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

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.ProtocolExclusion;
import org.apache.qpid.server.ProtocolInclusion;
import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;

public class PortConfigurationHelper
{
    private final ConfigurationEntryStore _configurationEntryStore;

    public PortConfigurationHelper(ConfigurationEntryStore configurationEntryStore)
    {
        _configurationEntryStore = configurationEntryStore;
    }

    public Map<UUID, ConfigurationEntry> getPortConfiguration(ServerConfiguration serverConfig, BrokerOptions options)
    {
        Map<UUID, ConfigurationEntry> portConfiguration = new HashMap<UUID, ConfigurationEntry>();

        Set<Integer> ports = new HashSet<Integer>(options.getPorts());
        if (ports.isEmpty())
        {
            parsePortList(ports, serverConfig.getPorts());
        }

        Set<Integer> sslPorts = new HashSet<Integer>(options.getSSLPorts());
        if (sslPorts.isEmpty())
        {
            parsePortList(sslPorts, serverConfig.getSSLPorts());
        }

        // 1-0 excludes and includes
        Set<Integer> exclude_1_0 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v1_0));
        if (exclude_1_0.isEmpty())
        {
            parsePortList(exclude_1_0, serverConfig.getPortExclude10());
        }

        Set<Integer> include_1_0 = new HashSet<Integer>(options.getIncludedPorts(ProtocolInclusion.v1_0));
        if (include_1_0.isEmpty())
        {
            parsePortList(include_1_0, serverConfig.getPortInclude10());
        }

        // 0-10 excludes and includes
        Set<Integer> exclude_0_10 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_10));
        if (exclude_0_10.isEmpty())
        {
            parsePortList(exclude_0_10, serverConfig.getPortExclude010());
        }

        Set<Integer> include_0_10 = new HashSet<Integer>(options.getIncludedPorts(ProtocolInclusion.v0_10));
        if (include_0_10.isEmpty())
        {
            parsePortList(include_0_10, serverConfig.getPortInclude010());
        }

        // 0-9-1 excludes and includes
        Set<Integer> exclude_0_9_1 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_9_1));
        if (exclude_0_9_1.isEmpty())
        {
            parsePortList(exclude_0_9_1, serverConfig.getPortExclude091());
        }

        Set<Integer> include_0_9_1 = new HashSet<Integer>(options.getIncludedPorts(ProtocolInclusion.v0_9_1));
        if (include_0_9_1.isEmpty())
        {
            parsePortList(include_0_9_1, serverConfig.getPortInclude091());
        }

        // 0-9 excludes and includes
        Set<Integer> exclude_0_9 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_9));
        if (exclude_0_9.isEmpty())
        {
            parsePortList(exclude_0_9, serverConfig.getPortExclude09());
        }

        Set<Integer> include_0_9 = new HashSet<Integer>(options.getIncludedPorts(ProtocolInclusion.v0_9));
        if (include_0_9.isEmpty())
        {
            parsePortList(include_0_9, serverConfig.getPortInclude09());
        }

        // 0-8 excludes and includes
        Set<Integer> exclude_0_8 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_8));
        if (exclude_0_8.isEmpty())
        {
            parsePortList(exclude_0_8, serverConfig.getPortExclude08());
        }

        Set<Integer> include_0_8 = new HashSet<Integer>(options.getIncludedPorts(ProtocolInclusion.v0_8));
        if (include_0_8.isEmpty())
        {
            parsePortList(include_0_8, serverConfig.getPortInclude08());
        }

        String bindAddress = getBindAddress(options, serverConfig);

        if (!serverConfig.getSSLOnly())
        {
            addAmqpPort(bindAddress, ports, Transport.TCP, serverConfig, exclude_1_0, include_1_0, exclude_0_10,
                    include_0_10, exclude_0_9_1, include_0_9_1, exclude_0_9, include_0_9, exclude_0_8, include_0_8,
                    portConfiguration);
        }

        if (serverConfig.getEnableSSL())
        {
            addAmqpPort(bindAddress, sslPorts, Transport.SSL, serverConfig, exclude_1_0, include_1_0, exclude_0_10,
                    include_0_10, exclude_0_9_1, include_0_9_1, exclude_0_9, include_0_9, exclude_0_8, include_0_8,
                    portConfiguration);
        }

        return portConfiguration;
    }

    private String getBindAddress(final BrokerOptions options, ServerConfiguration serverConfig)
    {
        String bindAddr = options.getBind();
        if (bindAddr == null)
        {
            bindAddr = serverConfig.getBind();
        }

        String bindAddress;
        if (bindAddr.equals(WILDCARD_ADDRESS))
        {
            bindAddress = null;
        }
        else
        {
            bindAddress = bindAddr;
        }
        return bindAddress;
    }

    private void parsePortList(Set<Integer> output, List<?> ports)
    {
        if (ports != null)
        {
            for (Object o : ports)
            {
                try
                {
                    output.add(Integer.parseInt(String.valueOf(o)));
                }
                catch (NumberFormatException e)
                {
                    throw new IllegalConfigurationException("Invalid port: " + o, e);
                }
            }
        }
    }

    private void addAmqpPort(String bindAddress, Set<Integer> ports, Transport transport, ServerConfiguration serverConfig,
            Set<Integer> exclude_1_0, Set<Integer> include_1_0, Set<Integer> exclude_0_10, Set<Integer> include_0_10,
            Set<Integer> exclude_0_9_1, Set<Integer> include_0_9_1, Set<Integer> exclude_0_9, Set<Integer> include_0_9,
            Set<Integer> exclude_0_8, Set<Integer> include_0_8, Map<UUID, ConfigurationEntry> portConfiguration)
    {
        for (int port : ports)
        {
            final Set<Protocol> supported = getSupportedVersions(port, exclude_1_0, exclude_0_10, exclude_0_9_1,
                    exclude_0_9, exclude_0_8, include_1_0, include_0_10, include_0_9_1, include_0_9, include_0_8,
                    serverConfig);

            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(Port.PROTOCOLS, supported);
            attributes.put(Port.TRANSPORTS, Collections.singleton(transport));
            attributes.put(Port.PORT, port);
            attributes.put(Port.BINDING_ADDRESS, bindAddress);
            attributes.put(Port.TCP_NO_DELAY, serverConfig.getTcpNoDelay());
            attributes.put(Port.RECEIVE_BUFFER_SIZE, serverConfig.getReceiveBufferSize());
            attributes.put(Port.SEND_BUFFER_SIZE, serverConfig.getWriteBufferSize());
            attributes.put(Port.NEED_CLIENT_AUTH, serverConfig.needClientAuth());
            attributes.put(Port.WANT_CLIENT_AUTH, serverConfig.wantClientAuth());
            attributes.put(Port.AUTHENTICATION_MANAGER, serverConfig.getPortAuthenticationMappings().get(port));

            ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), Port.class.getSimpleName(), attributes,
                    null, _configurationEntryStore);

            portConfiguration.put(entry.getId(), entry);
        }
    }

    private Set<Protocol> getSupportedVersions(final int port, final Set<Integer> exclude_1_0,
            final Set<Integer> exclude_0_10, final Set<Integer> exclude_0_9_1, final Set<Integer> exclude_0_9,
            final Set<Integer> exclude_0_8, final Set<Integer> include_1_0, final Set<Integer> include_0_10,
            final Set<Integer> include_0_9_1, final Set<Integer> include_0_9, final Set<Integer> include_0_8,
            final ServerConfiguration serverConfig)
    {
        final EnumSet<Protocol> supported = EnumSet.of(Protocol.AMQP_1_0, Protocol.AMQP_0_10, Protocol.AMQP_0_9_1,
                Protocol.AMQP_0_9, Protocol.AMQP_0_8);

        if ((exclude_1_0.contains(port) || !serverConfig.isAmqp10enabled()) && !include_1_0.contains(port))
        {
            supported.remove(Protocol.AMQP_1_0);
        }

        if ((exclude_0_10.contains(port) || !serverConfig.isAmqp010enabled()) && !include_0_10.contains(port))
        {
            supported.remove(Protocol.AMQP_0_10);
        }

        if ((exclude_0_9_1.contains(port) || !serverConfig.isAmqp091enabled()) && !include_0_9_1.contains(port))
        {
            supported.remove(Protocol.AMQP_0_9_1);
        }

        if ((exclude_0_9.contains(port) || !serverConfig.isAmqp09enabled()) && !include_0_9.contains(port))
        {
            supported.remove(Protocol.AMQP_0_9);
        }

        if ((exclude_0_8.contains(port) || !serverConfig.isAmqp08enabled()) && !include_0_8.contains(port))
        {
            supported.remove(Protocol.AMQP_0_8);
        }

        return supported;
    }

}
