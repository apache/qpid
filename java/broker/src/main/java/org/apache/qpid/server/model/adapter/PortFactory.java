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
package org.apache.qpid.server.model.adapter;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Protocol.ProtocolType;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.util.MapValueConverter;

public class PortFactory
{
    public static final int DEFAULT_AMQP_SEND_BUFFER_SIZE = 262144;
    public static final int DEFAULT_AMQP_RECEIVE_BUFFER_SIZE = 262144;
    public static final boolean DEFAULT_AMQP_NEED_CLIENT_AUTH = false;
    public static final boolean DEFAULT_AMQP_WANT_CLIENT_AUTH = false;
    public static final boolean DEFAULT_AMQP_TCP_NO_DELAY = true;
    public static final String DEFAULT_AMQP_BINDING = "*";
    public static final Transport DEFAULT_TRANSPORT = Transport.TCP;

    private final Collection<Protocol> _defaultProtocols;

    public PortFactory()
    {
        Set<Protocol> defaultProtocols = EnumSet.of(Protocol.AMQP_0_8, Protocol.AMQP_0_9, Protocol.AMQP_0_9_1,
                Protocol.AMQP_0_10, Protocol.AMQP_1_0);
        String excludedProtocols = System.getProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_EXCLUDES);
        if (excludedProtocols != null)
        {
            String[] excludes = excludedProtocols.split(",");
            for (String exclude : excludes)
            {
                Protocol protocol = Protocol.valueOf(exclude);
                defaultProtocols.remove(protocol);
            }
        }
        String includedProtocols = System.getProperty(BrokerProperties.PROPERTY_BROKER_DEFAULT_AMQP_PROTOCOL_INCLUDES);
        if (includedProtocols != null)
        {
            String[] includes = includedProtocols.split(",");
            for (String include : includes)
            {
                Protocol protocol = Protocol.valueOf(include);
                defaultProtocols.add(protocol);
            }
        }
        _defaultProtocols = Collections.unmodifiableCollection(defaultProtocols);
    }

    public Port createPort(UUID id, Broker broker, Map<String, Object> attributes)
    {
        final Port port;
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put(Port.TRANSPORTS, Collections.singleton(DEFAULT_TRANSPORT));
        Object portValue = attributes.get(Port.PORT);
        if (portValue == null)
        {
            throw new IllegalConfigurationException("Port attribute is not specified for port: " + attributes);
        }
        Set<Protocol> protocols = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
        if (isAmqpProtocol(protocols, attributes))
        {
            Object binding = attributes.get(Port.BINDING_ADDRESS);
            if (binding == null)
            {
                binding = DEFAULT_AMQP_BINDING;
                defaults.put(Port.BINDING_ADDRESS, DEFAULT_AMQP_BINDING);
            }
            defaults.put(Port.NAME, binding + ":" + portValue);
            defaults.put(Port.PROTOCOLS, _defaultProtocols);
            defaults.put(Port.TCP_NO_DELAY, DEFAULT_AMQP_TCP_NO_DELAY);
            defaults.put(Port.WANT_CLIENT_AUTH, DEFAULT_AMQP_WANT_CLIENT_AUTH);
            defaults.put(Port.NEED_CLIENT_AUTH, DEFAULT_AMQP_NEED_CLIENT_AUTH);
            defaults.put(Port.RECEIVE_BUFFER_SIZE, DEFAULT_AMQP_RECEIVE_BUFFER_SIZE);
            defaults.put(Port.SEND_BUFFER_SIZE, DEFAULT_AMQP_SEND_BUFFER_SIZE);
            port = new AmqpPortAdapter(id, broker, attributes, defaults, broker.getTaskExecutor());
        }
        else
        {
            if (protocols.size() > 1)
            {
                throw new IllegalConfigurationException("Only one protocol can be used on non AMQP port");
            }
            Protocol protocol = protocols.iterator().next();
            defaults.put(Port.NAME, portValue + "-" + protocol.name());
            port = new PortAdapter(id, broker, attributes, defaults, broker.getTaskExecutor());
        }
        return port;
    }

    private boolean isAmqpProtocol(Set<Protocol> protocols, Map<String, Object> portAttributes)
    {
        if (protocols == null || protocols.isEmpty())
        {
            // defaulting to AMQP if protocol is not specified
            return true;
        }

        Set<ProtocolType> protocolTypes = new HashSet<ProtocolType>();
        for (Protocol protocolObject : protocols)
        {
            protocolTypes.add(protocolObject.getProtocolType());
        }

        if (protocolTypes.size() > 1)
        {
            throw new IllegalConfigurationException("Found different protocol types '" + protocolTypes
                    + "' for port configuration: " + portAttributes);
        }

        return protocolTypes.contains(ProtocolType.AMQP);
    }

    public Collection<Protocol> getDefaultProtocols()
    {
        return _defaultProtocols;
    }

}
