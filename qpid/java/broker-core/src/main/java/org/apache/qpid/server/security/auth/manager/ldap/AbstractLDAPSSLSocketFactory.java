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
package org.apache.qpid.server.security.auth.manager.ldap;

import static org.apache.qpid.server.security.auth.manager.ldap.LDAPSSLSocketFactoryGenerator.getStaticFieldByReflection;
import static org.apache.qpid.server.security.auth.manager.ldap.LDAPSSLSocketFactoryGenerator.SSL_SOCKET_FACTORY_FIELD;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocketFactory;

/**
 * Abstract base class for all LDAPSSLSocketFactory implementations.
 * <p>
 * Concrete implementations of this class are <b>generated dynamically</b> at runtime by
 * the {@link LDAPSSLSocketFactoryGenerator#createSubClass(String, SSLSocketFactory)} method.
 * <p>
 * Callers will create new instances of the concrete implementations by using the static
 * <code>#getDefault()</code> method. This will return an instance of the sub-class that is
 * associated with the {@link SSLSocketFactory}.
 * <p>
 * If callers are passing the sub-class to an API via class-name (i.e. String), the caller
 * <b>must</b> ensure that the context classloader of the thread to set to the classloader
 * of sub-class for the duration of the API call(s).
 * <p>
 * For more details  see {@link LDAPSSLSocketFactoryGenerator}.
 */
public abstract class AbstractLDAPSSLSocketFactory extends SSLSocketFactory
{
    /** Socket factory to which this factory will delegate */
    private final SSLSocketFactory _delegate;

    protected AbstractLDAPSSLSocketFactory()
    {
        super();
        _delegate = getStaticFieldByReflection(getClass(), SSL_SOCKET_FACTORY_FIELD);
        if (_delegate == null)
        {
            throw new IllegalStateException("Delegate cannot be null - static field initialisation problem?");
        }
    }

    @Override
    public String[] getDefaultCipherSuites()
    {
        return _delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites()
    {
        return _delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException
    {
        return _delegate.createSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException
    {
        return _delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException
    {
        return _delegate.createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException
    {
        return _delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException
    {
        return _delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException
    {
        return _delegate.createSocket(address, port, localAddress, localPort);
    }


}
