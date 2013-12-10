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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.X509TrustManager;

/**
 * Supports multiple X509TrustManager(s). Check succeeds if any of the
 * underlying managers succeeds.
 */
public class QpidMultipleTrustManager implements X509TrustManager {

    private List<X509TrustManager> trustManagers;

    public QpidMultipleTrustManager() {
        this.trustManagers = new ArrayList<X509TrustManager>();
    }

    public boolean isEmpty()
    {
        return trustManagers.isEmpty();
    }
    
    public void addTrustManager(final X509TrustManager trustManager)
    {
        this.trustManagers.add(trustManager);
    }
    
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        for (X509TrustManager trustManager : this.trustManagers)
        {
            try
            {
                trustManager.checkClientTrusted(chain, authType);
                // this trustManager check succeeded, no need to check another one
                return;
            }
            catch(CertificateException ex)
            {
                // do nothing, try another one in a loop
            }
        }
        // no trustManager call succeeded, throw an exception
        throw new CertificateException();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        for (X509TrustManager trustManager : this.trustManagers)
        {
            try
            {
                trustManager.checkServerTrusted(chain, authType);
                // this trustManager check succeeded, no need to check another one
                return;
            }
            catch(CertificateException ex)
            {
                // do nothing, try another one in a loop
            }
        }
        // no trustManager call succeeded, throw an exception
        throw new CertificateException();
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        final Collection<X509Certificate> accIssuersCol = new ArrayList<X509Certificate>();
        for (X509TrustManager trustManager : this.trustManagers)
        {
            accIssuersCol.addAll(Arrays.asList(trustManager.getAcceptedIssuers()));
        }
        return accIssuersCol.toArray(new X509Certificate[accIssuersCol.size()]);
    }
}
