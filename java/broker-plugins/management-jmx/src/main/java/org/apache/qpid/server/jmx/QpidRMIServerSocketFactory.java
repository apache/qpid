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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

import javax.net.ServerSocketFactory;

import org.apache.log4j.Logger;

class QpidRMIServerSocketFactory implements RMIServerSocketFactory
{
    private static final Logger LOGGER = Logger.getLogger(QpidRMIServerSocketFactory.class);

    @Override
    public ServerSocket createServerSocket(final int port) throws IOException
    {
        ServerSocket serverSocket = new ServerSocket()
        {
            @Override
            public void close() throws IOException
            {
                try
                {
                    super.close();
                }
                finally
                {
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Closed server socket : " + getInetAddress());
                    }
                }
            }
        };
        serverSocket.setReuseAddress(true);
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Binding server socket : " + port);
        }
        serverSocket.bind(new InetSocketAddress(port));
        return serverSocket;
    }

    @Override
    public int hashCode()
    {
        final int prime = 37;
        return prime * QpidRMIServerSocketFactory.class.getName().hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        return obj != null && getClass() == obj.getClass();
    }
}