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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;

public class SSLUtil
{
    private static final Logger log = Logger.get(SSLUtil.class);
    
    public static void verifyHostname(SSLEngine engine,String hostnameExpected)
    {
        try
        {
          Certificate cert = engine.getSession().getPeerCertificates()[0];
          Principal p = ((X509Certificate)cert).getSubjectDN();
          String dn = p.getName();
          String hostname = null; 
          
          if (dn.contains("CN="))
          {
              hostname = dn.substring(3,
                      dn.indexOf(",") == -1? dn.length(): dn.indexOf(","));
          }   
          
          if (log.isDebugEnabled())
          {
              log.debug("Hostname expected : " + hostnameExpected);
              log.debug("Distinguished Name for server certificate : " + dn);
              log.debug("Host Name obtained from DN : " + hostname);
          }
          
          if (hostname != null && !(hostname.equalsIgnoreCase(hostnameExpected) ||
                  hostname.equalsIgnoreCase(hostnameExpected + ".localdomain")))
          {
              throw new TransportException("SSL hostname verification failed." +
                                           " Expected : " + hostnameExpected +
                                           " Found in cert : " + hostname);
          }
          
        }
        catch(SSLPeerUnverifiedException e)
        {
            log.warn("Exception received while trying to verify hostname",e);
            // For some reason the SSL engine sets the handshake status to FINISH twice
            // in succession. The first time the peer certificate 
            // info is not available. The second time it works !
            // Therefore have no choice but to ignore the exception here.
        }
    }
    
    public static String retriveIdentity(SSLEngine engine)
    {
        StringBuffer id = new StringBuffer(); 
        try
        {
          Certificate cert = engine.getSession().getLocalCertificates()[0];
          Principal p = ((X509Certificate)cert).getSubjectDN();
          String dn = p.getName();
                    
          if (dn.contains("CN="))
          {
              id.append(dn.substring(3,
                      dn.indexOf(",") == -1? dn.length(): dn.indexOf(",")));
          } 
          
          if (dn.contains("DC="))
          {
              id.append("@");
              int c = 0;
              for (String toks : dn.split(","))
              {
                  if (toks.contains("DC"))
                  {
                     if (c > 0) {id.append(".");}                         
                     id.append(toks.substring(
                             toks.indexOf("=")+1, 
                             toks.indexOf(",") == -1? toks.length(): toks.indexOf(",")));
                     c++;
                  }
              }
          }
        }
        catch(Exception e)
        {
            log.info("Exception received while trying to retrive client identity from SSL cert",e);
        }
        
        log.debug("Extracted Identity from client certificate : " + id);
        return id.toString();
    }
    
    public static SSLContext createSSLContext(ConnectionSettings settings) throws Exception
    {
        SSLContextFactory sslContextFactory;
        
        if (settings.getCertAlias() == null)
        {
            sslContextFactory = 
                new SSLContextFactory(settings.getTrustStorePath(),
                                      settings.getTrustStorePassword(),
                                      settings.getTrustStoreCertType(),
                                      settings.getKeyStorePath(),
                                      settings.getKeyStorePassword(),
                                      settings.getKeyStoreCertType());

        } else
        {
            sslContextFactory = 
                new SSLContextFactory(settings.getTrustStorePath(),
                                      settings.getTrustStorePassword(),
                                      settings.getTrustStoreCertType(),
                    new QpidClientX509KeyManager(settings.getCertAlias(),
                                                     settings.getKeyStorePath(),
                                                     settings.getKeyStorePassword(),
                                                     settings.getKeyStoreCertType()));
            
            log.debug("Using custom key manager");
        }

        return sslContextFactory.buildServerContext();
        
    }
    
    public static KeyStore getInitializedKeyStore(String storePath, String storePassword) throws GeneralSecurityException, IOException
    {
        KeyStore ks = KeyStore.getInstance("JKS");
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
            if (in == null)
            {
                throw new IOException("Unable to load keystore resource: " + storePath);
            }
            ks.load(in, storePassword.toCharArray());
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
