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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.qpid.transport.network.security.ssl.SSLUtil;

/**
 * Factory used to create SSLContexts. SSL needs to be configured
 * before this will work.
 * 
 */
public class SSLContextFactory {
	
	/**
	 * Path to the Java keystore file
	 */
	private String _keyStorePath;
	
	/**
	 * Password for the keystore
	 */
	private String _keyStorePassword;
	
	/**
	 * Cert type to use in keystore
	 */
	private String _keyStoreCertType;
	
	/**
     * Path to the Java truststore file
     */
    private String _trustStorePath;
    
    /**
     * Password for the truststore
     */
    private String _trustStorePassword;
    
    /**
     * Cert type to use in truststore
     */
    private String _trustStoreCertType;
    
	private KeyManager customKeyManager;
    
    public SSLContextFactory(String trustStorePath, String trustStorePassword,
            String trustStoreCertType) 
    {
        this(trustStorePath,trustStorePassword,trustStoreCertType,
                          trustStorePath,trustStorePassword,trustStoreCertType);
    }

    /**
	 * Create a factory instance
	 * @param keystorePath path to the Java keystore file
	 * @param keystorePassword password for the Java keystore
	 * @param certType certificate type
	 */
	public SSLContextFactory(String trustStorePath, String trustStorePassword, String trustStoreCertType,
            String keyStorePath, String keyStorePassword, String keyStoreCertType) 
	{

	    _trustStorePath = trustStorePath;
        _trustStorePassword = trustStorePassword;
                
        if (_trustStorePassword != null && _trustStorePassword.equals("none"))
        {
            _trustStorePassword = null;
        }
        _trustStoreCertType = trustStoreCertType;
        
	    _keyStorePath = keyStorePath;
		_keyStorePassword = keyStorePassword;
				
		if (_keyStorePassword != null && _keyStorePassword.equals("none"))
		{
			_keyStorePassword = null;
		}
		_keyStoreCertType = keyStoreCertType;
		
		if (_trustStorePath == null) {
			throw new IllegalArgumentException("A TrustStore path or KeyStore path must be specified");
		}
		if (_trustStoreCertType == null) {
			throw new IllegalArgumentException("Cert type must be specified");
		}
	}
	
	public SSLContextFactory(String trustStorePath, String trustStorePassword, String trustStoreCertType,
	                         KeyManager customKeyManager) 
    {

        _trustStorePath = trustStorePath;
        _trustStorePassword = trustStorePassword;
                
        if (_trustStorePassword != null && _trustStorePassword.equals("none"))
        {
            _trustStorePassword = null;
        }
        _trustStoreCertType = trustStoreCertType;
        
        if (_trustStorePath == null) {
            throw new IllegalArgumentException("A TrustStore path or KeyStore path must be specified");
        }
        if (_trustStoreCertType == null) {
            throw new IllegalArgumentException("Cert type must be specified");
        }
        
        this.customKeyManager = customKeyManager;
    }
	
	
	/**
	 * Builds a SSLContext appropriate for use with a server
	 * @return SSLContext
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */

	public SSLContext buildServerContext() throws GeneralSecurityException, IOException
	{
        KeyStore ts = SSLUtil.getInitializedKeyStore(_trustStorePath,_trustStorePassword);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(_trustStoreCertType);
        tmf.init(ts);
        
        // Initialize the SSLContext to work with our key managers.
        SSLContext sslContext = SSLContext.getInstance("TLS");
        
        if (customKeyManager != null)
        {
            sslContext.init(new KeyManager[]{customKeyManager},
                            tmf.getTrustManagers(), null);
            
        }
        else
        {
            // Create keystore
            KeyStore ks = SSLUtil.getInitializedKeyStore(_keyStorePath,_keyStorePassword);
            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(_keyStoreCertType);
            kmf.init(ks, _keyStorePassword.toCharArray());

            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);    
        }
        
        return sslContext;		
	}
	
	/**
	 * Creates a SSLContext factory appropriate for use with a client
	 * @return SSLContext
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public SSLContext buildClientContext() throws GeneralSecurityException, IOException
	{
		KeyStore ks = SSLUtil.getInitializedKeyStore(_trustStorePath,_trustStorePassword);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(_trustStoreCertType);
        tmf.init(ks);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;		
	}
	
}
