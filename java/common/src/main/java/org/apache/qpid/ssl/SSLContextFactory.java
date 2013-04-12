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
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.transport.network.security.ssl.QpidPeersOnlyTrustManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Factory used to create SSLContexts. SSL needs to be configured
 * before this will work.
 * 
 */
public class SSLContextFactory
{
    public static final String TRANSPORT_LAYER_SECURITY_CODE = "TLS";
    
    public static class TrustStoreWrapper
    {
        private final String trustStorePath;
        private final String trustStorePassword;
        private final String trustStoreType;
        private final Boolean trustStorePeersOnly;
        private String trustManagerFactoryAlgorithm;
        
        public TrustStoreWrapper(final String trustStorePath, final String trustStorePassword,
                final String trustStoreType, final Boolean trustStorePeersOnly,
                final String trustManagerFactoryAlgorithm)
        {
            this.trustStorePath = trustStorePath;
            this.trustStorePassword = trustStorePassword;
            this.trustStoreType = trustStoreType;
            this.trustStorePeersOnly = trustStorePeersOnly;
            this.trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;
        }
    }

    private SSLContextFactory()
    {
        //no instances
    }

    public static SSLContext buildServerContext(final String keyStorePath,
            final String keyStorePassword, final String keyStoreType,
            final String keyManagerFactoryAlgorithm)
            throws GeneralSecurityException, IOException
    {
        return buildContext(Collections.<TrustStoreWrapper>emptyList(), keyStorePath,
                keyStorePassword, keyStoreType, keyManagerFactoryAlgorithm, null);
    }
    
    public static SSLContext buildClientContext(Collection<TrustStoreWrapper> trustStores,
            final String keyStorePath, final String keyStorePassword,
            final String keyStoreType, final String keyManagerFactoryAlgorithm,
            final String certAlias) throws GeneralSecurityException, IOException
    {
        return buildContext(trustStores, keyStorePath, keyStorePassword, keyStoreType,
                            keyManagerFactoryAlgorithm, certAlias);
    }

    public static SSLContext buildClientContext(final String trustStorePath,
            final String trustStorePassword, final String trustStoreType,
            final String trustManagerFactoryAlgorithm, final String keyStorePath,
            final String keyStorePassword, final String keyStoreType,
            final String keyManagerFactoryAlgorithm, final String certAlias)
            throws GeneralSecurityException, IOException
    {
        TrustStoreWrapper trstWrapper = new TrustStoreWrapper(trustStorePath, trustStorePassword,
                                                              trustStoreType, Boolean.FALSE,
                                                              trustManagerFactoryAlgorithm);
        return buildContext(Collections.singletonList(trstWrapper), keyStorePath,
                keyStorePassword, keyStoreType, keyManagerFactoryAlgorithm, certAlias);
    }
    
    private static SSLContext buildContext(final Collection<TrustStoreWrapper> trstWrappers,
            final String keyStorePath, final String keyStorePassword, 
            final String keyStoreType, final String keyManagerFactoryAlgorithm,
            final String certAlias)
            throws GeneralSecurityException, IOException
    {
        // Initialize the SSLContext to work with our key managers.
        final SSLContext sslContext = SSLContext
                .getInstance(TRANSPORT_LAYER_SECURITY_CODE);

        final TrustManager[] trustManagers;
        final KeyManager[] keyManagers;
        
        final Collection<TrustManager> trustManagersCol = new ArrayList<TrustManager>();
        final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
        for (TrustStoreWrapper tsw : trstWrappers)
        {
            if (tsw.trustStorePath != null)
            {
                final KeyStore ts = SSLUtil.getInitializedKeyStore(tsw.trustStorePath,
                        tsw.trustStorePassword, tsw.trustStoreType);
                final TrustManagerFactory tmf = TrustManagerFactory
                        .getInstance(tsw.trustManagerFactoryAlgorithm);
                tmf.init(ts);
                TrustManager[] delegateManagers = tmf.getTrustManagers();
                for (TrustManager tm : delegateManagers)
                {
                    if (tm instanceof X509TrustManager)
                    {
                        if (Boolean.TRUE.equals(tsw.trustStorePeersOnly))
                        {
                            // truststore is supposed to trust only clients which peers certificates
                            // are directly in the store. CA signing will not be considered.
                            mulTrustManager.addTrustManager(new QpidPeersOnlyTrustManager(ts, (X509TrustManager) tm));
                        }
                        else
                        {
                            mulTrustManager.addTrustManager((X509TrustManager) tm);
                        }
                    }
                    else
                    {
                        trustManagersCol.add(tm);
                    }
                }
            }
        }
        if (! mulTrustManager.isEmpty())
        {
            trustManagersCol.add(mulTrustManager);
        }
        
        if (trustManagersCol.isEmpty())
        {
            trustManagers = null;
        }
        else
        {
            trustManagers = trustManagersCol.toArray(new TrustManager[trustManagersCol.size()]);
        }

        if (keyStorePath != null)
        {
            if (certAlias != null)
            {
                keyManagers = new KeyManager[] { new QpidClientX509KeyManager(
                        certAlias, keyStorePath, keyStoreType, keyStorePassword,
                        keyManagerFactoryAlgorithm) };
            }
            else
            {
                final KeyStore ks = SSLUtil.getInitializedKeyStore(
                        keyStorePath, keyStorePassword, keyStoreType);

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
