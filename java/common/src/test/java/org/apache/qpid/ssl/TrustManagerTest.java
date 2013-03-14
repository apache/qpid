/* Licensed to the Apache Software Foundation (ASF) under one
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

package org.apache.qpid.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.network.security.ssl.QpidMultipleTrustManager;
import org.apache.qpid.transport.network.security.ssl.QpidPeersOnlyTrustManager;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class TrustManagerTest extends QpidTestCase
{
    private static final String BROKER_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_broker_keystore.jks";
    private static final String BROKER_PEERSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_broker_peerstore.jks";
    private static final String CLIENT_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_client_keystore.jks";
    private static final String STORE_PASSWORD = "password";
    private static final String STORE_TYPE = "JKS";
    private static final String DEFAULT_TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();
    private static final String CERT_ALIAS_APP1 = "app1";
    private static final String CERT_ALIAS_APP2 = "app2";

    // retrieves the client certificate's chain from store and returns it as an array
    private X509Certificate[] getClientChain() throws Exception
    {
        final KeyStore ks = SSLUtil.getInitializedKeyStore(CLIENT_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final Certificate[] chain = ks.getCertificateChain(CERT_ALIAS_APP1);
        return Arrays.copyOf(chain, chain.length, X509Certificate[].class);
    }
    
    // verifies that peer store is loaded only with client's (peer's) certificates (no CA)
    private void noCAinPeerStore(final KeyStore ps) throws KeyStoreException
    {
        final Enumeration<String> aliases = ps.aliases();
        while (aliases.hasMoreElements())
        {
            final String alias = aliases.nextElement();
            if (!alias.equalsIgnoreCase(CERT_ALIAS_APP1) && !alias.equalsIgnoreCase(CERT_ALIAS_APP2))
            {
                fail("Broker's peer store contains other certificate than client's public keys");
            }
        }
    }

    public void testPeerStoreClientAuthentication() throws Exception
    {
        // first let's check that peer manager loaded with proper store succeeds
        final KeyStore ps = SSLUtil.getInitializedKeyStore(BROKER_PEERSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        this.noCAinPeerStore(ps);
        final TrustManagerFactory pmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        pmf.init(ps);
        final TrustManager[] delegatePeerManagers = pmf.getTrustManagers();
        boolean peerManagerTested = false;
        for (final TrustManager tm : delegatePeerManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                peerManagerTested = true;
                // peer manager is supposed to trust only clients which peers certificates
                // are directly in the store. CA signing will not be considered.
                X509TrustManager peerManager = new QpidPeersOnlyTrustManager(ps, (X509TrustManager) tm);
                // since broker's peerstore contains the client's certificate, the check should succeed
                try
                {
                    peerManager.checkClientTrusted(this.getClientChain(), "RSA");
                }
                catch (CertificateException e)
                {
                    fail("Client's validation against the broker's peer store manager failed.");
                }
            }
        }
        assertEquals("No QpidPeersOnlyTrustManager(s) were created from the peer store.", true, peerManagerTested);
        // now let's check that peer manager loaded with improper store fails
        final KeyStore ts = SSLUtil.getInitializedKeyStore(BROKER_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        tmf.init(ts);
        final TrustManager[] delegateTrustManagers = tmf.getTrustManagers();
        for (final TrustManager tm : delegateTrustManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // peer manager is supposed to trust only clients which peers certificates
                // are directly in the store. CA signing will not be considered.
                X509TrustManager peerManager = new QpidPeersOnlyTrustManager(ts, (X509TrustManager) tm);
                // since broker's truststore doesn't contain the client's certificate, the check should fail
                try
                {
                    peerManager.checkClientTrusted(this.getClientChain(), "RSA");
                    fail("Client's validation against the broker's peer store manager didn't fail.");
                }
                catch (CertificateException e)
                {
                    // expected
                }
            }
        }
    }
    
    public void testMultipleStoreClientAuthentication() throws Exception
    {
        final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
        final KeyStore ts = SSLUtil.getInitializedKeyStore(BROKER_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        tmf.init(ts);
        final TrustManager[] delegateTrustManagers = tmf.getTrustManagers();
        for (final TrustManager tm : delegateTrustManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's trust manager
                mulTrustManager.addTrustManager((X509TrustManager) tm);
            }
        }
        final KeyStore ps = SSLUtil.getInitializedKeyStore(BROKER_PEERSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory pmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        pmf.init(ps);
        final TrustManager[] delegatePeerManagers = pmf.getTrustManagers();
        for (final TrustManager tm : delegatePeerManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's peer manager
                mulTrustManager.addTrustManager(new QpidPeersOnlyTrustManager(ps, (X509TrustManager) tm));
            }
        }
        try
        {
            mulTrustManager.checkClientTrusted(this.getClientChain(), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Client's validation against the broker's multi store manager failed.");
        }
    }
}
