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
package org.apache.qpid.transport.network;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.transport.TransportException;

public class Transport
{
    public static final String QPID_TRANSPORT_PROPNAME = "qpid.transport";
    public static final String QPID_TRANSPORT_V0_8_PROPNAME = "qpid.transport.v0_8";
    public static final String QPID_TRANSPORT_V0_9_PROPNAME = "qpid.transport.v0_9";
    public static final String QPID_TRANSPORT_V0_9_1_PROPNAME = "qpid.transport.v0_9_1";
    public static final String QPID_TRANSPORT_V0_10_PROPNAME = "qpid.transport.v0_10";
    public static final String QPID_BROKER_TRANSPORT_PROPNAME = "qpid.broker.transport";

    // Can't reference the class directly here, as this would preclude the ability to bundle transports separately.
    private static final String MINA_TRANSPORT_CLASSNAME = "org.apache.qpid.transport.network.mina.MinaNetworkTransport";
    private static final String IO_TRANSPORT_CLASSNAME = "org.apache.qpid.transport.network.io.IoNetworkTransport";

    public static final String TCP = "tcp";

    private final static Map<ProtocolVersion,String> OUTGOING_PROTOCOL_TO_IMPLDEFAULTS_MAP;

    static
    {
        final Map<ProtocolVersion,String> map = new HashMap<ProtocolVersion, String>();
        map.put(ProtocolVersion.v8_0, MINA_TRANSPORT_CLASSNAME);
        map.put(ProtocolVersion.v0_9, MINA_TRANSPORT_CLASSNAME);
        map.put(ProtocolVersion.v0_91, MINA_TRANSPORT_CLASSNAME);
        map.put(ProtocolVersion.v0_10, IO_TRANSPORT_CLASSNAME);

        OUTGOING_PROTOCOL_TO_IMPLDEFAULTS_MAP = Collections.unmodifiableMap(map);
    }

    public static IncomingNetworkTransport getIncomingTransportInstance()
    {
        return (IncomingNetworkTransport) loadTransportClass(
                System.getProperty(QPID_BROKER_TRANSPORT_PROPNAME, MINA_TRANSPORT_CLASSNAME));
    }

    public static OutgoingNetworkTransport getOutgoingTransportInstance(
            final ProtocolVersion protocolVersion)
    {

        final String overrride = getOverrideClassNameFromSystemProperty(protocolVersion);
        final String networkTransportClassName;
        if (overrride != null)
        {
            networkTransportClassName = overrride;
        }
        else
        {
            networkTransportClassName = OUTGOING_PROTOCOL_TO_IMPLDEFAULTS_MAP.get(protocolVersion);
        }

        return (OutgoingNetworkTransport) loadTransportClass(networkTransportClassName);
    }

    private static NetworkTransport loadTransportClass(final String networkTransportClassName)
    {
        if (networkTransportClassName == null)
        {
            throw new IllegalArgumentException("transport class name must not be null");
        }

        try
        {
            final Class<?> clazz = Class.forName(networkTransportClassName);
            return (NetworkTransport) clazz.newInstance();
        }
        catch (InstantiationException e)
        {
            throw new TransportException("Unable to instantiate transport class " + networkTransportClassName, e);
        }
        catch (IllegalAccessException e)
        {
            throw new TransportException("Access exception " + networkTransportClassName, e);
        }
        catch (ClassNotFoundException e)
        {
            throw new TransportException("Unable to load transport class " + networkTransportClassName, e);
        }
    }

    private static String getOverrideClassNameFromSystemProperty(final ProtocolVersion protocolVersion)
    {
        final String protocolSpecificSystemProperty;

        if (ProtocolVersion.v0_10.equals(protocolVersion))
        {
            protocolSpecificSystemProperty = QPID_TRANSPORT_V0_10_PROPNAME;
        }
        else if (ProtocolVersion.v0_91.equals(protocolVersion))
        {
            protocolSpecificSystemProperty = QPID_TRANSPORT_V0_9_1_PROPNAME;
        }
        else if (ProtocolVersion.v0_9.equals(protocolVersion))
        {
            protocolSpecificSystemProperty = QPID_TRANSPORT_V0_9_PROPNAME;
        }
        else if (ProtocolVersion.v8_0.equals(protocolVersion))
        {
            protocolSpecificSystemProperty = QPID_TRANSPORT_V0_8_PROPNAME;
        }
        else
        {
            throw new IllegalArgumentException("Unknown ProtocolVersion " + protocolVersion);
        }

        return System.getProperty(protocolSpecificSystemProperty, System.getProperty(QPID_TRANSPORT_PROPNAME));
    }
}
