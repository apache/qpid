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
package org.apache.qpid.client.ssl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQTestConnection_0_10;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.Connection;

public class SSLTest extends QpidBrokerTestCase
{      
    
    @Override
    protected void setUp() throws Exception
    {
        System.setProperty("javax.net.debug", "ssl");
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception
    {
        System.setProperty("javax.net.debug", "");
        super.tearDown();
    }
        
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

    public void testMultipleCertsInSingleStore() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            System.getProperty("test.port.ssl") + 
            "?ssl='true'&ssl_cert_alias='app1''";
            
            AMQTestConnection_0_10 con = new AMQTestConnection_0_10(url);      
            Connection transportCon = con.getConnection();
            String userID = transportCon.getSecurityLayer().getUserID();
            assertEquals("The correct certificate was not choosen","app1@acme.org",userID);
            con.close();
            
            url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            System.getProperty("test.port.ssl") + 
            "?ssl='true'&ssl_cert_alias='app2''";
            
            con = new AMQTestConnection_0_10(url);      
            transportCon = con.getConnection();
            userID = transportCon.getSecurityLayer().getUserID();
            assertEquals("The correct certificate was not choosen","app2@acme.org",userID);
            con.close();
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
