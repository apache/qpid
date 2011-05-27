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

import java.net.Socket;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.qpid.transport.util.Logger;

public class QpidClientX509KeyManager extends X509ExtendedKeyManager
{
    private static final Logger log = Logger.get(QpidClientX509KeyManager.class);
    
    X509ExtendedKeyManager delegate;
    String alias;
    
    public QpidClientX509KeyManager(String alias, String keyStorePath,
                           String keyStorePassword,String keyStoreCertType) throws Exception
    {
        this.alias = alias;    
        KeyStore ks = SSLUtil.getInitializedKeyStore(keyStorePath,keyStorePassword);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(keyStoreCertType);
        kmf.init(ks, keyStorePassword.toCharArray());
        this.delegate = (X509ExtendedKeyManager)kmf.getKeyManagers()[0];
    }
        
    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
    {
        log.debug("chooseClientAlias:Returning alias " + alias);
        return alias;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
    {
        return delegate.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias)
    {
        return delegate.getCertificateChain(alias);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers)
    {
        log.debug("getClientAliases:Returning alias " + alias);
        return new String[]{alias};
    }

    @Override
    public PrivateKey getPrivateKey(String alias)
    {
        return delegate.getPrivateKey(alias);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers)
    {
        return delegate.getServerAliases(keyType, issuers);
    }
    
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine)
    {
        log.debug("chooseEngineClientAlias:Returning alias " + alias);
        return alias;
    }
    
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) 
    {
        return delegate.chooseEngineServerAlias(keyType, issuers, engine);
    }
}
