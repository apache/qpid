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
package org.apache.qpid.server.jmx;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class QpidSslRMIServerSocketFactory extends SslRMIServerSocketFactory
{
    private final SSLContext _sslContext;

    /**
     * SslRMIServerSocketFactory which creates the ServerSocket using the
     * supplied SSLContext rather than the system default context normally
     * used by the superclass, allowing us to use a configuration-specified
     * key store.
     *
     * @param sslContext previously created sslContext using the desired key store.
     * @throws NullPointerException if the provided {@link SSLContext} is null.
     */
    public QpidSslRMIServerSocketFactory(SSLContext sslContext) throws NullPointerException
    {
        super();

        if(sslContext == null)
        {
            throw new NullPointerException("The provided SSLContext must not be null");
        }

        _sslContext = sslContext;

        //TODO: settings + implementation for SSL client auth, updating equals and hashCode appropriately.
    }

    @Override
    public ServerSocket createServerSocket(int port) throws IOException
    {
        final SSLSocketFactory factory = _sslContext.getSocketFactory();

        ServerSocket serverSocket = new ServerSocket()
        {
            public Socket accept() throws IOException
            {
                Socket socket = super.accept();

                SSLSocket sslSocket =
                        (SSLSocket) factory.createSocket(socket,
                                                         socket.getInetAddress().getHostName(),
                                                         socket.getPort(),
                                                         true);
                sslSocket.setUseClientMode(false);
                SSLUtil.removeSSLv3Support(sslSocket);
                return sslSocket;
            }
        };
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        return serverSocket;
    }

    /**
     * One QpidSslRMIServerSocketFactory is equal to
     * another if their (non-null) SSLContext are equal.
     */
    @Override
    public boolean equals(Object object)
    {
        if (!(object instanceof QpidSslRMIServerSocketFactory))
        {
            return false;
        }

        QpidSslRMIServerSocketFactory that = (QpidSslRMIServerSocketFactory) object;

        return _sslContext.equals(that._sslContext);
    }

    @Override
    public int hashCode()
    {
        return _sslContext.hashCode();
    }

}
