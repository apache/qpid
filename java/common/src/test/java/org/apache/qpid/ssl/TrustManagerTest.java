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
    private static final String BROKER_TRUSTSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_broker_truststore.jks";
    private static final String BROKER_PEERSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_broker_peerstore.jks";
    private static final String CLIENT_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_client_keystore.jks";
    private static final String CLIENT_UNTRUSTED_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_client_untrusted_keystore.jks";
    private static final String STORE_PASSWORD = "password";
    private static final String STORE_TYPE = "JKS";
    private static final String DEFAULT_TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();
    private static final String CERT_ALIAS_APP1 = "app1";
    private static final String CERT_ALIAS_APP2 = "app2";
    private static final String CERT_ALIAS_UNTRUSTED_CLIENT = "untrusted_client";

    // retrieves the client certificate's chain from store and returns it as an array
    private X509Certificate[] getClientChain(final String storePath, final String alias) throws Exception
    {
        final KeyStore ks = SSLUtil.getInitializedKeyStore(storePath, STORE_PASSWORD, STORE_TYPE);
        final Certificate[] chain = ks.getCertificateChain(alias);
        return Arrays.copyOf(chain, chain.length, X509Certificate[].class);
    }
    
    // verifies that peer store is loaded only with client's (peer's) app1 certificate (no CA)
    private void noCAinPeerStore(final KeyStore ps) throws KeyStoreException
    {
        final Enumeration<String> aliases = ps.aliases();
        while (aliases.hasMoreElements())
        {
            final String alias = aliases.nextElement();
            if (!alias.equalsIgnoreCase(CERT_ALIAS_APP1))
            {
                fail("Broker's peer store contains other certificate than client's  app1 public key");
            }
        }
    }

    /**
     * Tests that the QpidPeersOnlyTrustManager gives the expected behaviour when loaded separately
     * with the broker peerstore and truststore.
     */
    public void testQpidPeersOnlyTrustManager() throws Exception
    {
        // first let's check that peer manager loaded with the PEERstore succeeds
        final KeyStore ps = SSLUtil.getInitializedKeyStore(BROKER_PEERSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        this.noCAinPeerStore(ps);
        final TrustManagerFactory pmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        pmf.init(ps);
        final TrustManager[] delegatePeerManagers = pmf.getTrustManagers();

        X509TrustManager peerManager = null;
        for (final TrustManager tm : delegatePeerManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // peer manager is supposed to trust only clients which peers certificates
                // are directly in the store. CA signing will not be considered.
                peerManager = new QpidPeersOnlyTrustManager(ps, (X509TrustManager) tm);
            }
        }

        try
        {
            // since broker's peerstore contains the client's app1 certificate, the check should succeed
            peerManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP1), "RSA");
        }
        catch (CertificateException e)
        {
            fail("Trusted client's validation against the broker's peer store manager failed.");
        }

        try
        {
            // since broker's peerstore does not contain the client's app2 certificate, the check should fail
            peerManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP2), "RSA");
            fail("Untrusted client's validation against the broker's peer store manager succeeded.");
        }
        catch (CertificateException e)
        {
            //expected
        }

        // now let's check that peer manager loaded with the brokers TRUSTstore fails because
        // it does not have the clients certificate in it (though it does have a CA-cert that
        // would otherwise trust the client cert when using the regular trust manager).
        final KeyStore ts = SSLUtil.getInitializedKeyStore(BROKER_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        tmf.init(ts);
        final TrustManager[] delegateTrustManagers = tmf.getTrustManagers();

        peerManager = null;
        for (final TrustManager tm : delegateTrustManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // peer manager is supposed to trust only clients which peers certificates
                // are directly in the store. CA signing will not be considered.
                peerManager = new QpidPeersOnlyTrustManager(ts, (X509TrustManager) tm);
            }
        }

        try
        {
            // since broker's truststore doesn't contain the client's app1 certificate, the check should fail
            // despite the fact that the truststore does have a CA that would otherwise trust the cert
            peerManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP1), "RSA");
            fail("Client's validation against the broker's peer store manager didn't fail.");
        }
        catch (CertificateException e)
        {
            // expected
        }

        try
        {
            // since broker's truststore doesn't contain the client's app2 certificate, the check should fail
            // despite the fact that the truststore does have a CA that would otherwise trust the cert
            peerManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP2), "RSA");
            fail("Client's validation against the broker's peer store manager didn't fail.");
        }
        catch (CertificateException e)
        {
            // expected
        }
    }

    /**
     * Tests that the QpidMultipleTrustManager gives the expected behaviour when wrapping a
     * regular TrustManager against the broker truststore.
     */
    public void testQpidMultipleTrustManagerWithRegularTrustStore() throws Exception
    {
        final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
        final KeyStore ts = SSLUtil.getInitializedKeyStore(BROKER_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        tmf.init(ts);
        final TrustManager[] delegateTrustManagers = tmf.getTrustManagers();
        boolean trustManagerAdded = false;
        for (final TrustManager tm : delegateTrustManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's trust manager
                mulTrustManager.addTrustManager((X509TrustManager) tm);
                trustManagerAdded = true;
            }
        }
        assertTrue("The regular trust manager for the trust store was not added", trustManagerAdded);

        try
        {
            // verify the CA-trusted app1 cert (should succeed)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP1), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Trusted client's validation against the broker's multi store manager failed.");
        }

        try
        {
            // verify the CA-trusted app2 cert (should succeed)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP2), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Trusted client's validation against the broker's multi store manager failed.");
        }

        try
        {
            // verify the untrusted cert (should fail)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_UNTRUSTED_KEYSTORE_PATH, CERT_ALIAS_UNTRUSTED_CLIENT), "RSA");
            fail("Untrusted client's validation against the broker's multi store manager unexpectedly passed.");
        }
        catch (CertificateException ex)
        {
            // expected
        }
    }

    /**
     * Tests that the QpidMultipleTrustManager gives the expected behaviour when wrapping a
     * QpidPeersOnlyTrustManager against the broker peerstore.
     */
    public void testQpidMultipleTrustManagerWithPeerStore() throws Exception
    {
        final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
        final KeyStore ps = SSLUtil.getInitializedKeyStore(BROKER_PEERSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory pmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        pmf.init(ps);
        final TrustManager[] delegatePeerManagers = pmf.getTrustManagers();
        boolean peerManagerAdded = false;
        for (final TrustManager tm : delegatePeerManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's peer manager
                mulTrustManager.addTrustManager(new QpidPeersOnlyTrustManager(ps, (X509TrustManager) tm));
                peerManagerAdded = true;
            }
        }
        assertTrue("The QpidPeersOnlyTrustManager for the peerstore was not added", peerManagerAdded);

        try
        {
            // verify the trusted app1 cert (should succeed as the key is in the peerstore)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP1), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Trusted client's validation against the broker's multi store manager failed.");
        }

        try
        {
            // verify the untrusted app2 cert (should fail as the key is not in the peerstore)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP2), "RSA");
            fail("Untrusted client's validation against the broker's multi store manager unexpectedly passed.");
        }
        catch (CertificateException ex)
        {
            // expected
        }

        try
        {
            // verify the untrusted cert (should fail as the key is not in the peerstore)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_UNTRUSTED_KEYSTORE_PATH, CERT_ALIAS_UNTRUSTED_CLIENT), "RSA");
            fail("Untrusted client's validation against the broker's multi store manager unexpectedly passed.");
        }
        catch (CertificateException ex)
        {
            // expected
        }
    }

    /**
     * Tests that the QpidMultipleTrustManager gives the expected behaviour when wrapping a
     * QpidPeersOnlyTrustManager against the broker peerstore, a regular TrustManager
     * against the broker truststore.
     */
    public void testQpidMultipleTrustManagerWithTrustAndPeerStores() throws Exception
    {
        final QpidMultipleTrustManager mulTrustManager = new QpidMultipleTrustManager();
        final KeyStore ts = SSLUtil.getInitializedKeyStore(BROKER_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        tmf.init(ts);
        final TrustManager[] delegateTrustManagers = tmf.getTrustManagers();
        boolean trustManagerAdded = false;
        for (final TrustManager tm : delegateTrustManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's trust manager
                mulTrustManager.addTrustManager((X509TrustManager) tm);
                trustManagerAdded = true;
            }
        }
        assertTrue("The regular trust manager for the trust store was not added", trustManagerAdded);

        final KeyStore ps = SSLUtil.getInitializedKeyStore(BROKER_PEERSTORE_PATH, STORE_PASSWORD, STORE_TYPE);
        final TrustManagerFactory pmf = TrustManagerFactory.getInstance(DEFAULT_TRUST_MANAGER_ALGORITHM);
        pmf.init(ps);
        final TrustManager[] delegatePeerManagers = pmf.getTrustManagers();
        boolean peerManagerAdded = false;
        for (final TrustManager tm : delegatePeerManagers)
        {
            if (tm instanceof X509TrustManager)
            {
                // add broker's peer manager
                mulTrustManager.addTrustManager(new QpidPeersOnlyTrustManager(ps, (X509TrustManager) tm));
                peerManagerAdded = true;
            }
        }
        assertTrue("The QpidPeersOnlyTrustManager for the peerstore was not added", peerManagerAdded);

        try
        {
            // verify the CA-trusted app1 cert (should succeed)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP1), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Trusted client's validation against the broker's multi store manager failed.");
        }

        try
        {
            // verify the CA-trusted app2 cert (should succeed)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_KEYSTORE_PATH, CERT_ALIAS_APP2), "RSA");
        }
        catch (CertificateException ex)
        {
            fail("Trusted client's validation against the broker's multi store manager failed.");
        }

        try
        {
            // verify the untrusted cert (should fail)
            mulTrustManager.checkClientTrusted(this.getClientChain(CLIENT_UNTRUSTED_KEYSTORE_PATH, CERT_ALIAS_UNTRUSTED_CLIENT), "RSA");
            fail("Untrusted client's validation against the broker's multi store manager unexpectedly passed.");
        }
        catch (CertificateException ex)
        {
            // expected
        }
    }
}
