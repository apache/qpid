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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Factory used to create SSLContexts. SSL needs to be configured
 * before this will work.
 * 
 */
public class SSLContextFactory {
	
	/**
	 * Path to the Java keystore file
	 */
	private String _keystorePath;
	
	/**
	 * Password for the keystore
	 */
	private String _keystorePassword;
	
	/**
	 * Cert type to use
	 */
	private String _certType;
	
	/**
	 * Create a factory instance
	 * @param keystorePath path to the Java keystore file
	 * @param keystorePassword password for the Java keystore
	 * @param certType certificate type
	 */
	public SSLContextFactory(String keystorePath, String keystorePassword,
			String certType) 
	{
		_keystorePath = keystorePath;
		_keystorePassword = keystorePassword;
		if (_keystorePassword.equals("none"))
		{
			_keystorePassword = null;
		}
		_certType = certType;
		if (keystorePath == null) {
			throw new IllegalArgumentException("Keystore path must be specified");
		}
		if (certType == null) {
			throw new IllegalArgumentException("Cert type must be specified");
		}
	}
	
	/**
	 * Builds a SSLContext appropriate for use with a server
	 * @return SSLContext
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public SSLContext buildServerContext() throws GeneralSecurityException, IOException
	{
        // Create keystore
		KeyStore ks = getInitializedKeyStore();

        // Set up key manager factory to use our key store
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(_certType);
        kmf.init(ks, _keystorePassword.toCharArray());

        // Initialize the SSLContext to work with our key managers.
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(_certType);
        tmf.init(ks);
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

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
		KeyStore ks = getInitializedKeyStore();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(_certType);
        tmf.init(ks);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;		
	}
	
	private KeyStore getInitializedKeyStore() throws GeneralSecurityException, IOException
	{
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream in = null;
        try
        {
        	File f = new File(_keystorePath);
        	if (f.exists())
        	{
        		in = new FileInputStream(f);
        	}
        	else 
        	{
        		in = Thread.currentThread().getContextClassLoader().getResourceAsStream(_keystorePath);
        	}
            if (in == null)
            {
                throw new IOException("Unable to load keystore resource: " + _keystorePath);
            }
            ks.load(in, _keystorePassword.toCharArray());
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
}
