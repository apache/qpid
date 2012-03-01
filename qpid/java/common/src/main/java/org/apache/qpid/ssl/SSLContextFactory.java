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
package org.apache.qpid.ssl;

import org.apache.qpid.transport.network.security.ssl.QpidClientX509KeyManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Factory used to create SSLContexts. SSL needs to be configured
 * before this will work.
 * 
 */
public class SSLContextFactory
{
    public static final String JAVA_KEY_STORE_CODE = "JKS";
    public static final String TRANSPORT_LAYER_SECURITY_CODE = "TLS";

    private SSLContextFactory()
    {
        //no instances
    }

    public static SSLContext buildServerContext(final String keyStorePath,
            final String keyStorePassword, final String keyManagerFactoryAlgorithm)
            throws GeneralSecurityException, IOException
    {
        return buildContext(null, null, null, keyStorePath, keyStorePassword,
                keyManagerFactoryAlgorithm, null);
    }

    public static SSLContext buildClientContext(final String trustStorePath,
            final String trustStorePassword, final String trustManagerFactoryAlgorithm,
            final String keyStorePath, final String keyStorePassword,
            final String keyManagerFactoryAlgorithm, final String certAlias)
            throws GeneralSecurityException, IOException
    {
        return buildContext(trustStorePath, trustStorePassword,
                trustManagerFactoryAlgorithm, keyStorePath, keyStorePassword,
                keyManagerFactoryAlgorithm, certAlias);
    }
    
    private static SSLContext buildContext(final String trustStorePath,
            final String trustStorePassword, final String trustManagerFactoryAlgorithm,
            final String keyStorePath, final String keyStorePassword,
            final String keyManagerFactoryAlgorithm, final String certAlias)
            throws GeneralSecurityException, IOException
    {
        // Initialize the SSLContext to work with our key managers.
        final SSLContext sslContext = SSLContext
                .getInstance(TRANSPORT_LAYER_SECURITY_CODE);

        final TrustManager[] trustManagers;
        final KeyManager[] keyManagers;

        if (trustStorePath != null)
        {
            final KeyStore ts = SSLUtil.getInitializedKeyStore(trustStorePath,
                    trustStorePassword);
            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(trustManagerFactoryAlgorithm);
            tmf.init(ts);

            trustManagers = tmf.getTrustManagers();
        }
        else
        {
            trustManagers = null;
        }

        if (keyStorePath != null)
        {
            if (certAlias != null)
            {
                keyManagers = new KeyManager[] { new QpidClientX509KeyManager(
                        certAlias, keyStorePath, keyStorePassword,
                        keyManagerFactoryAlgorithm) };
            }
            else
            {
                final KeyStore ks = SSLUtil.getInitializedKeyStore(
                        keyStorePath, keyStorePassword);

                char[] keyStoreCharPassword = keyStorePassword == null ? null : keyStorePassword.toCharArray();
                // Set up key manager factory to use our key store
                final KeyManagerFactory kmf = KeyManagerFactory
                        .getInstance(keyManagerFactoryAlgorithm);
                kmf.init(ks, keyStoreCharPassword);
                keyManagers = kmf.getKeyManagers();
            }
        }
        else
        {
            keyManagers = null;
        }

        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }
}
