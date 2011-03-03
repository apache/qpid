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

import org.apache.qpid.transport.TransportException;

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
    public static final String MINA_TRANSPORT = "org.apache.qpid.transport.network.mina.MinaNetworkTransport"; // TODO
    public static final String NIO_TRANSPORT = "org.apache.qpid.transport.network.nio.NioNetworkTransport"; // TODO
    public static final String NETTY_TRANSPORT = "org.apache.qpid.transport.network.netty.NettyNetworkTransport"; // TODO

    private final static Class<?> transportClass;
    
    static 
    {
        try
        {
            transportClass = Class.forName(System.getProperty("qpid.transport", IO_TRANSPORT));
        }
        catch(Exception e)
        {
            throw new Error("Error occured while loading Qpid Transport",e);
        }
    }
    
    public static NetworkTransport getTransport() throws TransportException
    {
        try
        {
            return (NetworkTransport)transportClass.newInstance();
        }
        catch (Exception e)
        {
            throw new TransportException("Error while creating a new transport instance",e);
        }
    }
}
