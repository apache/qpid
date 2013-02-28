/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.auth.manager;

import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE_PASSWORD;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE_PASSWORD;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class ExternalAuthenticationTest extends QpidBrokerTestCase
{
    @Override
    protected void setUp() throws Exception
    {
        // not calling super.setUp() to avoid broker start-up
    }

    /**
     * Tests that when EXTERNAL authentication is used on the SSL port, clients presenting certificates are able to connect.
     * Also, checks that default authentication manager PrincipalDatabaseAuthenticationManager is used on non SSL port.
     */
    public void testExternalAuthenticationManagerOnSSLPort() throws Exception
    {
        setCommonBrokerSSLProperties(true);
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_SSL_PORT, Port.AUTHENTICATION_MANAGER, TestBrokerConfiguration.ENTRY_NAME_EXTERNAL_PROVIDER);
        super.setUp();

        setClientKeystoreProperties();
        setClientTrustoreProperties();

        try
        {
            getExternalSSLConnection(false);
        }
        catch (JMSException e)
        {
            fail("Should be able to create a connection to the SSL port: " + e.getMessage());
        }

        try
        {
            getConnection();
        }
        catch (JMSException e)
        {
            fail("Should be able to create a connection with credentials to the standard port: " + e.getMessage());
        }

    }

    /**
     * Tests that when EXTERNAL authentication manager is set as the default, clients presenting certificates are able to connect.
     * Also, checks a client with valid username and password but not using ssl is unable to connect to the non SSL port.
     */
    public void testExternalAuthenticationManagerAsDefault() throws Exception
    {
        setCommonBrokerSSLProperties(true);
        getBrokerConfiguration().setBrokerAttribute(Broker.DEFAULT_AUTHENTICATION_PROVIDER, TestBrokerConfiguration.ENTRY_NAME_EXTERNAL_PROVIDER);
        super.setUp();

        setClientKeystoreProperties();
        setClientTrustoreProperties();

        try
        {
            getConnection();
            fail("Connection should not succeed");
        }
        catch (JMSException e)
        {
            // pass
        }

        try
        {
            getExternalSSLConnection(false);
        }
        catch (JMSException e)
        {
            fail("Should be able to create a connection to the SSL port. " + e.getMessage());
        }
    }

    /**
     * Tests that when EXTERNAL authentication manager is set as the default, clients without certificates are unable to connect to the SSL port
     * even with valid username and password.
     */
    public void testExternalAuthenticationManagerWithoutClientKeyStore() throws Exception
    {
        setCommonBrokerSSLProperties(false);
        getBrokerConfiguration().setBrokerAttribute(Broker.DEFAULT_AUTHENTICATION_PROVIDER, TestBrokerConfiguration.ENTRY_NAME_EXTERNAL_PROVIDER);
        super.setUp();

        setClientTrustoreProperties();

        try
        {
            getExternalSSLConnection(true);
            fail("Connection should not succeed");
        }
        catch (JMSException e)
        {
            // pass
        }
    }

    private Connection getExternalSSLConnection(boolean includeUserNameAndPassword) throws Exception
    {
        String url = "amqp://%s@test/?brokerlist='tcp://localhost:%s?ssl='true'&sasl_mechs='EXTERNAL''";
        if (includeUserNameAndPassword)
        {
            url = String.format(url, "guest:guest", String.valueOf(QpidBrokerTestCase.DEFAULT_SSL_PORT));
        }
        else
        {
            url = String.format(url, ":", String.valueOf(QpidBrokerTestCase.DEFAULT_SSL_PORT));
        }
        return getConnection(new AMQConnectionURL(url));
    }

    private void setCommonBrokerSSLProperties(boolean needClientAuth) throws ConfigurationException
    {
        TestBrokerConfiguration config = getBrokerConfiguration();
        Map<String, Object> sslPortAttributes = new HashMap<String, Object>();
        sslPortAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        sslPortAttributes.put(Port.PORT, DEFAULT_SSL_PORT);
        sslPortAttributes.put(Port.NEED_CLIENT_AUTH, String.valueOf(needClientAuth));
        sslPortAttributes.put(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_SSL_PORT);
        config.addPortConfiguration(sslPortAttributes);

        Map<String, Object> externalAuthProviderAttributes = new HashMap<String, Object>();
        externalAuthProviderAttributes.put(AuthenticationManagerFactory.ATTRIBUTE_TYPE, ExternalAuthenticationManagerFactory.PROVIDER_TYPE);
        externalAuthProviderAttributes.put(AuthenticationProvider.NAME, TestBrokerConfiguration.ENTRY_NAME_EXTERNAL_PROVIDER);
        config.addAuthenticationProviderConfiguration(externalAuthProviderAttributes);
    }

    private void setClientKeystoreProperties()
    {
        setSystemProperty("javax.net.ssl.keyStore", KEYSTORE);
        setSystemProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
    }

    private void setClientTrustoreProperties()
    {
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        setSystemProperty("javax.net.debug", "ssl");
    }
}
