package org.apache.qpid.client.ssl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.test.utils.QpidTestCase;

public class SSLTest extends QpidTestCase
{      
    public void testCreateSSLContextFromConnectionURLParams()
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {   
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s" +
            "?ssl='true'&ssl_verify_hostname='true'" + 
            "&key_store='%s'&key_store_password='%s'" +
            "&trust_store='%s'&trust_store_password='%s'" +
            "'";
            
            String keyStore = System.getProperty("javax.net.ssl.keyStore");
            String keyStorePass = System.getProperty("javax.net.ssl.keyStorePassword");
            String trustStore = System.getProperty("javax.net.ssl.trustStore");
            String trustStorePass = System.getProperty("javax.net.ssl.trustStorePassword");
            
            url = String.format(url,System.getProperty("test.port.ssl"),
                    keyStore,keyStorePass,trustStore,trustStorePass);
            
            // temporarily set the trust/key store jvm args to something else
            // to ensure we only read from the connection URL param.
            System.setProperty("javax.net.ssl.trustStore","fessgsdgd");
            System.setProperty("javax.net.ssl.trustStorePassword","fessgsdgd");
            System.setProperty("javax.net.ssl.keyStore","fessgsdgd");
            System.setProperty("javax.net.ssl.keyStorePassword","fessgsdgd");
            try
            {
                AMQConnection con = new AMQConnection(url);
                Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE); 
            }
            catch (Exception e)
            {
                fail("SSL Connection should be successful");
            }
            finally
            {
                System.setProperty("javax.net.ssl.trustStore",trustStore);
                System.setProperty("javax.net.ssl.trustStorePassword",trustStorePass);
                System.setProperty("javax.net.ssl.keyStore",keyStore);
                System.setProperty("javax.net.ssl.keyStorePassword",keyStorePass);
            }
        }        
    }
    
    public void testVerifyHostName()
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://127.0.0.1:" + 
            System.getProperty("test.port.ssl") + 
            "?ssl='true'&ssl_verify_hostname='true''";
            
            try
            {
                AMQConnection con = new AMQConnection(url);
                fail("Hostname verification failed. No exception was thrown");
            }
            catch (Exception e)
            {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(bout));
                String strace = bout.toString();
                assertTrue("Correct exception not thrown",strace.contains("SSL hostname verification failed"));
            }
            
        }        
    }
    
    public void testVerifyLocalHost()
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            System.getProperty("test.port.ssl") + 
            "?ssl='true'&ssl_verify_hostname='true''";
            
            try
            {
                AMQConnection con = new AMQConnection(url);
            }
            catch (Exception e)
            {
                fail("Hostname verification should succeed");
            }            
        }        
    }
    
    public void testVerifyLocalHostLocalDomain()
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost.localdomain:" + 
            System.getProperty("test.port.ssl") + 
            "?ssl='true'&ssl_verify_hostname='true''";
            
            try
            {
                AMQConnection con = new AMQConnection(url);
            }
            catch (Exception e)
            {
                fail("Hostname verification should succeed");
            }
            
        }        
    }
}
