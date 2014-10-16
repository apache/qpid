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
package org.apache.qpid.amqp_1_0.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;

public class SSLUtil
{
    public static final String TRANSPORT_LAYER_SECURITY_CODE = "TLS";
    public static final String SSLV3_PROTOCOL = "SSLv3";

    public static SSLContext buildSslContext(final String certAlias,
                                             final String keyStorePath,
                                             final String keyStoreType,
                                             final String keyStorePassword,
                                             final String keyManagerFactoryAlgorithm,
                                             final String trustStorePath,
                                             final String trustStorePassword,
                                             final String trustStoreType,
                                             final String trustManagerFactoryAlgorithm) throws GeneralSecurityException, IOException
    {

        final SSLContext sslContext = SSLContext
                .getInstance(TRANSPORT_LAYER_SECURITY_CODE);

        final TrustManager[] trustManagers;
        final KeyManager[] keyManagers;

        if (trustStorePath != null)
        {
            final KeyStore ts = getInitializedKeyStore(trustStorePath, trustStorePassword, trustStoreType);
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm);

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
                        certAlias, keyStorePath, keyStoreType, keyStorePassword,
                        keyManagerFactoryAlgorithm) };
            }
            else
            {
                final KeyStore ks = SSLUtil.getInitializedKeyStore(keyStorePath, keyStorePassword, keyStoreType);

                char[] keyStoreCharPassword = keyStorePassword == null ? null : keyStorePassword.toCharArray();
                // Set up key manager factory to use our key store
                final KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagerFactoryAlgorithm);
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

    public static X509Certificate[] getClientCertificates(final String alias,
                                                final String keyStorePath,
                                                final String keyStorePassword,
                                                final String keyStoreType,
                                                final String keyManagerFactoryAlgorithm)
            throws GeneralSecurityException, IOException
    {
        return (new QpidClientX509KeyManager(alias,keyStorePath,keyStoreType,keyStorePassword,keyManagerFactoryAlgorithm)).getCertificateChain(alias);
    }

    public static KeyStore getInitializedKeyStore(String storePath, String storePassword, String keyStoreType) throws GeneralSecurityException, IOException
    {
        KeyStore ks = KeyStore.getInstance(keyStoreType);
        InputStream in = null;
        try
        {
            File f = new File(storePath);
            if (f.exists())
            {
                in = new FileInputStream(f);
            }
            else
            {
                in = Thread.currentThread().getContextClassLoader().getResourceAsStream(storePath);
            }
            if (in == null && !"PKCS11".equalsIgnoreCase(keyStoreType)) // PKCS11 will not require an explicit path
            {
                throw new IOException("Unable to load keystore resource: " + storePath);
            }

            char[] storeCharPassword = storePassword == null ? null : storePassword.toCharArray();

            ks.load(in, storeCharPassword);
        }
        finally
        {
            if (in != null)
            {
                //noinspection EmptyCatchBlock
                try
                {
                    in.close();
                }
                catch (IOException ignored)
                {
                }
            }
        }
        return ks;
    }

    public static class QpidClientX509KeyManager extends X509ExtendedKeyManager
    {

        private X509ExtendedKeyManager delegate;
        private String alias;

        public QpidClientX509KeyManager(String alias, String keyStorePath, String keyStoreType,
                                        String keyStorePassword, String keyManagerFactoryAlgorithmName) throws
                                                                                                        GeneralSecurityException,
                                                                                                        IOException
        {
            this.alias = alias;
            KeyStore ks = getInitializedKeyStore(keyStorePath, keyStorePassword, keyStoreType);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyManagerFactoryAlgorithmName);
            kmf.init(ks, keyStorePassword.toCharArray());
            this.delegate = (X509ExtendedKeyManager) kmf.getKeyManagers()[0];
        }

        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
        {
            return alias;
        }

        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
        {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }

        public X509Certificate[] getCertificateChain(String alias)
        {
            return delegate.getCertificateChain(alias);
        }

        public String[] getClientAliases(String keyType, Principal[] issuers)
        {
            return new String[]{alias};
        }

        public PrivateKey getPrivateKey(String alias)
        {
            return delegate.getPrivateKey(alias);
        }

        public String[] getServerAliases(String keyType, Principal[] issuers)
        {
            return delegate.getServerAliases(keyType, issuers);
        }

        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine)
        {
            return alias;
        }

        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine)
        {
            return delegate.chooseEngineServerAlias(keyType, issuers, engine);
        }
    }

    public static void removeSSLv3Support(final SSLSocket socket)
    {
        List<String> enabledProtocols = Arrays.asList(socket.getEnabledProtocols());
        if(enabledProtocols.contains(SSLV3_PROTOCOL))
        {
            List<String> allowedProtocols = new ArrayList<>(enabledProtocols);
            allowedProtocols.remove(SSLV3_PROTOCOL);
            socket.setEnabledProtocols(allowedProtocols.toArray(new String[allowedProtocols.size()]));
        }
    }

}
