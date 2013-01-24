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

import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE_PASSWORD;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE_PASSWORD;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.AMQTestConnection_0_10;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SSLTest extends QpidBrokerTestCase
{
    private static final String CERT_ALIAS_APP1 = "app1";
    private static final String CERT_ALIAS_APP2 = "app2";

    @Override
    protected void setUp() throws Exception
    {
        setSystemProperty("javax.net.debug", "ssl");

        setSslStoreSystemProperties();

        //We dont call super.setUp, the tests start the broker after deciding
        //whether to run and then configuring it appropriately
    }

    public void testCreateSSLConnectionUsingConnectionURLParams() throws Exception
    {
        if (shouldPerformTest())
        {
            clearSslStoreSystemProperties();
            
            //Start the broker (NEEDing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, true, false);
            super.setUp();

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

    /**
     * Create an SSL connection using the SSL system properties for the trust and key store, but using
     * the {@link ConnectionURL} ssl='true' option to indicate use of SSL at a Connection level,
     * without specifying anything at the {@link ConnectionURL#OPTIONS_BROKERLIST} level.
     */
    public void testSslConnectionOption() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (NEEDing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, true, false);
            super.setUp();

            //Create URL enabling SSL at the connection rather than brokerlist level
            String url = "amqp://guest:guest@test/?ssl='true'&brokerlist='tcp://localhost:%s'";
            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT);

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should be successful", con);
            Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE);
            assertNotNull("create session should be successful", ssn);
        }
    }

    /**
     * Create an SSL connection using the SSL system properties for the trust and key store, but using
     * the {@link ConnectionURL} ssl='true' option to indicate use of SSL at a Connection level,
     * overriding the false setting at the {@link ConnectionURL#OPTIONS_BROKERLIST} level.
     */
    public void testSslConnectionOptionOverridesBrokerlistOption() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (NEEDing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, true, false);
            super.setUp();

            //Create URL enabling SSL at the connection, overriding the false at the brokerlist level
            String url = "amqp://guest:guest@test/?ssl='true'&brokerlist='tcp://localhost:%s?ssl='false''";
            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT);

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should be successful", con);
            Session ssn = con.createSession(false,Session.AUTO_ACKNOWLEDGE);
            assertNotNull("create session should be successful", ssn);
        }
    }

    public void testCreateSSLConnectionUsingSystemProperties() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (NEEDing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, true, false);
            super.setUp();

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
        if (shouldPerformTest())
        {
            //Start the broker (NEEDing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, true, false);
            super.setUp();

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
    
    public void testVerifyHostNameWithIncorrectHostname() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (WANTing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, false, true);
            super.setUp();

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
                verifyExceptionCausesContains(e, "SSL hostname verification failed");
            }
        }        
    }

    private void verifyExceptionCausesContains(Exception e, String expectedString)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(bout));
        String strace = bout.toString();
        assertTrue("Correct exception not thrown", strace.contains(expectedString));
    }
    
    public void testVerifyLocalHost() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (WANTing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, false, true);
            super.setUp();

            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_verify_hostname='true''";

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should have been created", con);
        }
    }
    
    public void testVerifyLocalHostLocalDomain() throws Exception
    {
        if (shouldPerformTest())
        {
            //Start the broker (WANTing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, false, true);
            super.setUp();

            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost.localdomain:" + 
            QpidBrokerTestCase.DEFAULT_SSL_PORT + 
            "?ssl='true'&ssl_verify_hostname='true''";

            Connection con = getConnection(new AMQConnectionURL(url));
            assertNotNull("connection should have been created", con);
        }        
    }

    public void testCreateSSLConnectionUsingConnectionURLParamsTrustStoreOnly() throws Exception
    {
        if (shouldPerformTest())
        {
            clearSslStoreSystemProperties();

            //Start the broker (WANTing client certificate authentication)
            configureJavaBrokerIfNecessary(true, true, false, true);
            super.setUp();

            
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

    /**
     * Verifies that when the broker is configured to NEED client certificates,
     * a client which doesn't supply one fails to connect.
     */
    public void testClientCertMissingWhilstNeeding() throws Exception
    {
        missingClientCertWhileNeedingOrWantingTestImpl(true, false, false);
    }

    /**
     * Verifies that when the broker is configured to WANT client certificates,
     * a client which doesn't supply one succeeds in connecting.
     */
    public void testClientCertMissingWhilstWanting() throws Exception
    {
        missingClientCertWhileNeedingOrWantingTestImpl(false, true, true);
    }

    /**
     * Verifies that when the broker is configured to WANT and NEED client certificates
     * that a client which doesn't supply one fails to connect.
     */
    public void testClientCertMissingWhilstWantingAndNeeding() throws Exception
    {
        missingClientCertWhileNeedingOrWantingTestImpl(true, true, false);
    }

    private void missingClientCertWhileNeedingOrWantingTestImpl(boolean needClientCerts,
                            boolean wantClientCerts, boolean shouldSucceed) throws Exception
    {
        if (shouldPerformTest())
        {
            clearSslStoreSystemProperties();

            //Start the broker
            configureJavaBrokerIfNecessary(true, true, needClientCerts, wantClientCerts);
            super.setUp();

            String url = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s" +
            "?ssl='true'&trust_store='%s'&trust_store_password='%s''";

            url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT,TRUSTSTORE,TRUSTSTORE_PASSWORD);
            try
            {
                Connection con = getConnection(new AMQConnectionURL(url));
                if(!shouldSucceed)
                {
                    fail("Connection succeeded, expected exception was not thrown");
                }
                else
                {
                    //Use the connection to verify it works
                    con.createSession(true, Session.SESSION_TRANSACTED);
                }
            }
            catch(JMSException e)
            {
                if(shouldSucceed)
                {
                    _logger.error("Caught unexpected exception",e);
                    fail("Connection failed, unexpected exception thrown");
                }
                else
                {
                    //expected
                    verifyExceptionCausesContains(e, "Caused by: javax.net.ssl.SSLException:");
                }
            }
        }
    }

    private boolean shouldPerformTest()
    {
        // We run the SSL tests on all the Java broker profiles
        if(isJavaBroker())
        {
            setTestClientSystemProperty(PROFILE_USE_SSL, "true");
        }

        return Boolean.getBoolean(PROFILE_USE_SSL);
    }

    private void configureJavaBrokerIfNecessary(boolean sslEnabled, boolean sslOnly, boolean needClientAuth, boolean wantClientAuth) throws ConfigurationException
    {
        if(isJavaBroker())
        {
            Map<String, Object> sslPortAttributes = new HashMap<String, Object>();
            sslPortAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
            sslPortAttributes.put(Port.PORT, DEFAULT_SSL_PORT);
            sslPortAttributes.put(Port.NEED_CLIENT_AUTH, needClientAuth);
            sslPortAttributes.put(Port.WANT_CLIENT_AUTH, wantClientAuth);
            sslPortAttributes.put(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_SSL_PORT);
            getBrokerConfiguration().addPortConfiguration(sslPortAttributes);
        }
    }

    private void setSslStoreSystemProperties()
    {
        setSystemProperty("javax.net.ssl.keyStore", KEYSTORE);
        setSystemProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
    }

    private void clearSslStoreSystemProperties()
    {
        setSystemProperty("javax.net.ssl.keyStore", null);
        setSystemProperty("javax.net.ssl.keyStorePassword", null);
        setSystemProperty("javax.net.ssl.trustStore", null);
        setSystemProperty("javax.net.ssl.trustStorePassword", null);
    }
}
