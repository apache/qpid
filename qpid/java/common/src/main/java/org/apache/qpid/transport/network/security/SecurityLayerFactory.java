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
package org.apache.qpid.transport.network.security;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ByteBufferReceiver;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.security.sasl.SASLReceiver;
import org.apache.qpid.transport.network.security.sasl.SASLSender;
import org.apache.qpid.transport.network.security.ssl.SSLReceiver;
import org.apache.qpid.transport.network.security.ssl.SSLSender;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class SecurityLayerFactory
{
    private SecurityLayerFactory()
    {
    }

    public static SecurityLayer newInstance(ConnectionSettings settings)
    {

        SecurityLayer layer = NullSecurityLayer.getInstance();

        if (settings.isUseSSL())
        {
            layer = new SSLSecurityLayer(settings, layer);
        }
        if (settings.isUseSASLEncryption())
        {
            layer = new SASLSecurityLayer(layer);
        }

        return layer;

    }

    static class SSLSecurityLayer implements SecurityLayer
    {

        private final SSLEngine _engine;
        private final SSLStatus _sslStatus = new SSLStatus();
        private String _hostname;
        private SecurityLayer _layer;


        public SSLSecurityLayer(ConnectionSettings settings, SecurityLayer layer)
        {

            SSLContext sslCtx;
            _layer = layer;
            try
            {
                sslCtx = SSLContextFactory
                        .buildClientContext(settings.getTrustStorePath(),
                                settings.getTrustStorePassword(),
                                settings.getTrustStoreType(),
                                settings.getTrustManagerFactoryAlgorithm(),
                                settings.getKeyStorePath(),
                                settings.getKeyStorePassword(),
                                settings.getKeyStoreType(),
                                settings.getKeyManagerFactoryAlgorithm(),
                                settings.getCertAlias());
            }
            catch (Exception e)
            {
                throw new TransportException("Error creating SSL Context", e);
            }

            if(settings.isVerifyHostname())
            {
                _hostname = settings.getHost();
            }

            try
            {
                _engine = sslCtx.createSSLEngine();
                _engine.setUseClientMode(true);
                SSLUtil.removeSSLv3Support(_engine);
            }
            catch(Exception e)
            {
                throw new TransportException("Error creating SSL Engine", e);
            }

        }

        public ByteBufferSender sender(ByteBufferSender delegate)
        {
            SSLSender sender = new SSLSender(_engine, _layer.sender(delegate), _sslStatus);
            sender.setHostname(_hostname);
            return sender;
        }

        public ByteBufferReceiver receiver(ByteBufferReceiver delegate)
        {
            SSLReceiver receiver = new SSLReceiver(_engine, _layer.receiver(delegate), _sslStatus);
            receiver.setHostname(_hostname);
            return receiver;
        }

        public String getUserID()
        {
            return SSLUtil.retrieveIdentity(_engine);
        }
    }


    static class SASLSecurityLayer implements SecurityLayer
    {

        private SecurityLayer _layer;

        SASLSecurityLayer(SecurityLayer layer)
        {
            _layer = layer;
        }

        public SASLSender sender(ByteBufferSender delegate)
        {
            SASLSender sender = new SASLSender(_layer.sender(delegate));
            return sender;
        }

        public SASLReceiver receiver(ByteBufferReceiver delegate)
        {
            SASLReceiver receiver = new SASLReceiver(_layer.receiver(delegate));
            return receiver;
        }

        public String getUserID()
        {
            return _layer.getUserID();
        }
    }


    static class NullSecurityLayer implements SecurityLayer
    {

        private static final NullSecurityLayer INSTANCE = new NullSecurityLayer();

        private NullSecurityLayer()
        {
        }

        public ByteBufferSender sender(ByteBufferSender delegate)
        {
            return delegate;
        }

        public ByteBufferReceiver receiver(ByteBufferReceiver delegate)
        {
            return delegate;
        }

        public String getUserID()
        {
            return null;
        }

        public static NullSecurityLayer getInstance()
        {
            return INSTANCE;
        }
    }
}
