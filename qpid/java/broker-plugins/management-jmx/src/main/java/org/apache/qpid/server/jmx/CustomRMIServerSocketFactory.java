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
 *
 */
package org.apache.qpid.server.jmx;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIServerSocketFactory;

/**
 * Custom RMIServerSocketFactory class, used to prevent updates to the RMI registry.
 * Supplied to the registry at creation, this will prevent RMI-based operations on the
 * registry such as attempting to bind a new object, thereby securing it from tampering.
 * This is accomplished by always returning null when attempting to determine the address
 * of the caller, thus ensuring the registry will refuse the attempt. Calls to bind etc
 * made using the object reference will not be affected and continue to operate normally.
 */
class CustomRMIServerSocketFactory implements RMIServerSocketFactory
{

    public ServerSocket createServerSocket(int port) throws IOException
    {
        return new NoLocalAddressServerSocket(port);
    }

    private static class NoLocalAddressServerSocket extends ServerSocket
    {
        NoLocalAddressServerSocket(int port) throws IOException
        {
            super(port);
        }

        @Override
        public Socket accept() throws IOException
        {
            Socket s = new NoLocalAddressSocket();
            super.implAccept(s);
            return s;
        }
    }

    private static class NoLocalAddressSocket extends Socket
    {
        @Override
        public InetAddress getInetAddress()
        {
            return null;
        }
    }
}