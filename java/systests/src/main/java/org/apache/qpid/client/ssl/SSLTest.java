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

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQTestConnection_0_10;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Session;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class SSLTest extends QpidBrokerTestCase
{
    private static final String KEYSTORE = "test-profiles/test_resources/ssl/java_client_keystore.jks";
    private static final String KEYSTORE_PASSWORD = "password";
    private static final String TRUSTSTORE = "test-profiles/test_resources/ssl/java_client_truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";
    private static final String CERT_ALIAS_APP1 = "app1";
    private static final String CERT_ALIAS_APP2 = "app2";

    @Override
    protected void setUp() throws Exception
    {
        if(isJavaBroker())
        {
            setTestClientSystemProperty("profile.use_ssl", "true");
            setConfigurationProperty("connector.ssl.enabled", "true");
            setConfigurationProperty("connector.ssl.sslOnly", "true");
            setConfigurationProperty("connector.ssl.wantClientAuth", "true");
        }

        // set the ssl system properties
        setSystemProperty("javax.net.ssl.keyStore", KEYSTORE);
        setSystemProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        setSystemProperty("javax.net.debug", "ssl");
        super.setUp();
    }

    public void testCreateSSLConnectionUsingConnectionURLParams() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            // Clear the ssl system properties
            setSystemProperty("javax.net.ssl.keyStore", null);
            setSystemProperty("javax.net.ssl.keyStorePassword", null);
            setSystemProperty("javax.net.ssl.trustStore", null);
            setSystemProperty("javax.net.ssl.trustStorePassword", null);
            
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s" +
            "?ssl='true'&ssl_verify_hostname='true'" + 
            "&key_store='%s'&key_store_password='%s'" +
            "&trust_store='%s'&trust_store_password='%s'" +
            "'";
            
            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT,
                    KEYSTORE,KEYSTORE_PASSWORD,TRUSTSTORE,TRUSTSTORE_PASSWORD);

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should be successful", con);
            Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE); 
            assertNotNull("create session should be successful", ssn);
        }        
    }

    public void testCreateSSLConnectionUsingSystemProperties() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {

            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s?ssl='true''";

            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT);
            
            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should be successful", con);
            Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE); 
            assertNotNull("create session should be successful", ssn);
        }        
    }

    public void testMultipleCertsInSingleStore() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_cert_alias='" + CERT_ALIAS_APP1 + "''";
            
            AMQTestConnection_0_10 con = new AMQTestConnection_0_10(url);      
            org.apache.qpid.transport.Connection transportCon = con.getConnection();
            String userID = transportCon.getSecurityLayer().getUserID();
            assertEquals("The correct certificate was not choosen","app1@acme.org",userID);
            con.close();
            
            url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_cert_alias='" + CERT_ALIAS_APP2 + "''";
            
            con = new AMQTestConnection_0_10(url);      
            transportCon = con.getConnection();
            userID = transportCon.getSecurityLayer().getUserID();
            assertEquals("The correct certificate was not choosen","app2@acme.org",userID);
            con.close();
        }        
    }
    
    public void testVerifyHostNameWithIncorrectHostname()
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://127.0.0.1:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_verify_hostname='true''";
            
            try
            {
                getConnection(new AMQConnectionURL(url));
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
    
    public void testVerifyLocalHost() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_verify_hostname='true''";

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should have been created", con);
        }
    }
    
    public void testVerifyLocalHostLocalDomain() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost.localdomain:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_verify_hostname='true''";

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should have been created", con);
        }        
    }

    public void testCreateSSLConnectionUsingConnectionURLParamsTrustStoreOnly() throws Exception
    {
        if (Boolean.getBoolean("profile.use_ssl"))
        {
            // Clear the ssl system properties
            setSystemProperty("javax.net.ssl.keyStore", null);
            setSystemProperty("javax.net.ssl.keyStorePassword", null);
            setSystemProperty("javax.net.ssl.trustStore", null);
            setSystemProperty("javax.net.ssl.trustStorePassword", null);
            
            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s" +
            "?ssl='true'&ssl_verify_hostname='true'" + 
            "&trust_store='%s'&trust_store_password='%s'" +
            "'";

            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT, TRUSTSTORE,TRUSTSTORE_PASSWORD);

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should be successful", con);
            Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE); 
            assertNotNull("create session should be successful", ssn);
        }        
    }
}
