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

import java.util.LinkedList;
import java.util.List;

import org.apache.qpid.transport.TransportException;

/**
 * Loads the network transport class.
 */
public class Transport
{
    public static final String TCP = "tcp";
    public static final String TLS = "tls";
    public static final String SSL = "ssl";
    public static final String VM = "vm";
    public static final String SOCKET = "socket";
    
    public static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
    public static final long DEFAULT_TIMEOUT = 60000;

    public static final boolean WINDOWS = ((String) System.getProperties().get("os.name")).matches("(?i).*windows.*");
 
    public static final String IO_TRANSPORT = "org.apache.qpid.transport.network.io.IoNetworkTransport";
    public static final String MINA_TRANSPORT = "org.apache.qpid.transport.network.mina.MinaNetworkTransport";
    public static final String NIO_TRANSPORT = "org.apache.qpid.transport.network.nio.NioNetworkTransport"; // TODO
    public static final String NETTY_TRANSPORT = "org.apache.qpid.transport.network.netty.NettyNetworkTransport"; // TODO

    public static final String OUTGOING = "outgoing";
    public static final String INCOMING = "incoming";

    private static final List<String> _outgoing = new LinkedList<String>();
    private static final List<String> _incoming = new LinkedList<String>();
    
    public static void registerIncomingTransport(String transport)
    {
        registerTransport(_incoming, transport);
    }
    
    public static void registerOutgoingTransport(Class<? extends OutgoingNetworkTransport> transport)
    {
        registerTransport(_outgoing, transport.getName());
    }
    
    public static void registerOutgoingTransport(String transport)
    {
        registerTransport(_outgoing, transport);
    }
    
    private static void registerTransport(List<String> registered, String transport)
    {
        registered.add(transport);
    }

    public static IncomingNetworkTransport getIncomingTransport() throws TransportException
    {
        return (IncomingNetworkTransport) getTransport(INCOMING, _incoming, MINA_TRANSPORT, null);
    }
    
    public static OutgoingNetworkTransport getOutgoingTransport() throws TransportException
    {
        return (OutgoingNetworkTransport) getTransport(OUTGOING, _outgoing, IO_TRANSPORT, null);
    }
    
    public static OutgoingNetworkTransport getOutgoingTransport(String protocol) throws TransportException
    {
        return (OutgoingNetworkTransport) getTransport(OUTGOING, _outgoing, IO_TRANSPORT, protocol);
    }
    
    private static NetworkTransport getTransport(String direction, List<String> registered, String defaultTransport, String protocol)
    {
        for (String transport : registered)
        {
            try
            {
                Class<?> clazz = Class.forName(transport);
                NetworkTransport network = (NetworkTransport) clazz.newInstance();
                if (protocol == null || network.isCompatible(protocol))
                {
                    return network;
                }
            }
            catch (Exception e)
            {
                // Ignore and move to next class
            }
        }
        
        try
        {
            String transport = System.getProperty("qpid.transport." + direction, defaultTransport);
            Class<?> clazz = Class.forName(transport);
            NetworkTransport network = (NetworkTransport) clazz.newInstance();
            if (protocol == null || network.isCompatible(protocol))
            {
                return network;
            }
        }
        catch (Exception e)
        {
            throw new TransportException("Error while creating a new " + direction + " transport instance", e);
        }
        
        throw new TransportException("Cannot create " + direction + " transport supporting " + protocol);
    }
}
