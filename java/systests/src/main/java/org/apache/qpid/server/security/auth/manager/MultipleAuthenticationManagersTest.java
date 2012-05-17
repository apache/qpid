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

import javax.jms.Connection;
import javax.jms.JMSException;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class MultipleAuthenticationManagersTest extends QpidBrokerTestCase
{
    private static final String KEYSTORE = "test-profiles/test_resources/ssl/java_client_keystore.jks";
    private static final String KEYSTORE_PASSWORD = "password";
    private static final String TRUSTSTORE = "test-profiles/test_resources/ssl/java_client_truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    @Override
    protected void setUp() throws Exception
    {
        setConfigurationProperty("connector.ssl.enabled", "true");
        setConfigurationProperty("connector.ssl.sslOnly", "false");
        setConfigurationProperty("security.anonymous-auth-manager", "");
        setConfigurationProperty("security.default-auth-manager", "PrincipalDatabaseAuthenticationManager");
        setConfigurationProperty("security.port-mappings.port-mapping.port", String.valueOf(QpidBrokerTestCase.DEFAULT_SSL_PORT));
        setConfigurationProperty("security.port-mappings.port-mapping.auth-manager", "AnonymousAuthenticationManager");

        // set the ssl system properties
        setSystemProperty("javax.net.ssl.keyStore", KEYSTORE);
        setSystemProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        setSystemProperty("javax.net.debug", "ssl");
        super.setUp();
    }

    private Connection getAnonymousSSLConnection() throws Exception
    {
        String url = "amqp://:@test/?brokerlist='tcp://localhost:%s?ssl='true''";

        url = String.format(url,QpidBrokerTestCase.DEFAULT_SSL_PORT);

        return new AMQConnection(url);

    }

    private Connection getAnonymousConnection() throws Exception
    {
        String url = "amqp://:@test/?brokerlist='tcp://localhost:%s'";

        url = String.format(url,QpidBrokerTestCase.DEFAULT_PORT);

        return new AMQConnection(url);

    }


    public void testMultipleAuthenticationManagers() throws Exception
    {
        try
        {
            Connection conn = getConnection();
            assertNotNull("Connection unexpectedly null", conn);
        }
        catch(JMSException e)
        {
            fail("Should be able to create a connection with credentials to the standard port. " + e.getMessage());
        }

        try
        {
            Connection conn = getAnonymousSSLConnection();
            assertNotNull("Connection unexpectedly null", conn);
        }
        catch(JMSException e)
        {
            fail("Should be able to create a anonymous connection to the SSL port. " + e.getMessage());
        }

        try
        {
            Connection conn = getAnonymousConnection();
            fail("Should not be able to create anonymous connection to the standard port");
        }
        catch(AMQException e)
        {
            // pass
        }

    }
}
