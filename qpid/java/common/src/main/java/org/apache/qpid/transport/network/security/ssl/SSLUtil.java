package org.apache.qpid.transport.network.security.ssl;

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
          Certificate cert = engine.getSession().getPeerCertificates()[0];
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
        
        SSLContextFactory sslContextFactory = new SSLContextFactory(settings.getTrustStorePath(),
                                                                    settings.getTrustStorePassword(),
                                                                    settings.getTrustStoreCertType(),
                                                                    settings.getKeyStorePath(),
                                                                    settings.getKeyStorePassword(),
                                                                    settings.getKeyStoreCertType());
        
        return sslContextFactory.buildServerContext();
        
    }
}
