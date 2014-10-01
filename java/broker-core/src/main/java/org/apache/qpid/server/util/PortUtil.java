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

package org.apache.qpid.server.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class PortUtil
{
    public static boolean isPortAvailable(String hostName, int port)
    {
        InetSocketAddress socketAddress = null;
        if ( hostName == null || "".equals(hostName) || "*".equals(hostName) )
        {
            socketAddress = new InetSocketAddress(port);
        }
        else
        {
            socketAddress = new InetSocketAddress(hostName, port);
        }

        ServerSocket serverSocket = null;
        try
        {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(socketAddress);
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
        finally
        {
            if (serverSocket != null)
            {
                try
                {
                    serverSocket.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Couldn't close port " + port + " that was created to check its availability", e);
                }
            }
        }
    }
}
