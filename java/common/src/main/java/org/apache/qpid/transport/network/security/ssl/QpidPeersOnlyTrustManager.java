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
package org.apache.qpid.transport.network.security.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.net.ssl.X509TrustManager;

/**
 * TrustManager implementation which accepts the client certificate
 * only if the underlying check by the delegate pass through and
 * the certificate is physically saved in the truststore. 
 */
public class QpidPeersOnlyTrustManager implements X509TrustManager {

    final private KeyStore ts;
    final private X509TrustManager delegate;
    final List<Certificate> trustedCerts = new ArrayList<Certificate>();
    
    public QpidPeersOnlyTrustManager(KeyStore ts, X509TrustManager trustManager) throws KeyStoreException {
        this.ts = ts;
        this.delegate = trustManager;
        Enumeration<String> aliases = this.ts.aliases();
        while (aliases.hasMoreElements())
        {
            trustedCerts.add(ts.getCertificate(aliases.nextElement()));
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        this.delegate.checkClientTrusted(chain, authType);
        for (Certificate serverTrustedCert : this.trustedCerts)
        {
            // first position in the chain contains the peer's own certificate
            if (chain[0].equals(serverTrustedCert))
                return;  // peer's certificate found in the store
        }
        // peer's certificate was not found in the store, do not trust the client
        throw new CertificateException();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        this.delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // return empty array since this implementation of TrustManager doesn't
        // rely on certification authorities
        return new X509Certificate[0];
    }
}
