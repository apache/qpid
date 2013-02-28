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
package org.apache.qpid.systest.rest;

import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE_PASSWORD;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class BasicAuthRestTest extends QpidRestTestCase
{
    private static final String USERNAME = "admin";

    @Override
    public void setUp() throws Exception
    {
        setSystemProperty("javax.net.debug", "ssl");

        //don't call super method, we will configure the broker in the test before doing so
    }

    @Override
    protected void customizeConfiguration() throws ConfigurationException, IOException
    {
        //do nothing, we will configure this locally
    }

    private void configure(boolean useSsl) throws ConfigurationException, IOException
    {
        getRestTestHelper().setUseSsl(useSsl);
        if (useSsl)
        {
            getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.PROTOCOLS, Collections.singleton(Protocol.HTTPS));
        }
        super.customizeConfiguration();
    }

    private void verifyGetBrokerAttempt(int responseCode) throws IOException
    {
        HttpURLConnection conn = getRestTestHelper().openManagementConnection("/rest/broker", "GET");
        assertEquals(responseCode, conn.getResponseCode());
    }

    public void testDefaultEnabledWithHttps() throws Exception
    {
        configure(true);
        super.setUp();
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

        // Try the attempt with authentication, it should succeed because
        // BASIC auth is enabled by default on secure connections.
        getRestTestHelper().setUsernameAndPassword(USERNAME, USERNAME);
        verifyGetBrokerAttempt(HttpServletResponse.SC_OK);
    }

    public void testDefaultDisabledWithHttp() throws Exception
    {
        configure(false);
        super.setUp();

        // Try the attempt with authentication, it should fail because
        // BASIC auth is disabled by default on non-secure connections.
        getRestTestHelper().setUsernameAndPassword(USERNAME, USERNAME);
        verifyGetBrokerAttempt(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public void testEnablingForHttp() throws Exception
    {
        configure(false);

        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "httpBasicAuthenticationEnabled", true);
        super.setUp();

        // Try the attempt with authentication, it should succeed because
        // BASIC auth is now enabled on non-secure connections.
        getRestTestHelper().setUsernameAndPassword(USERNAME, USERNAME);
        verifyGetBrokerAttempt(HttpServletResponse.SC_OK);
    }

    public void testDisablingForHttps() throws Exception
    {
        configure(true);
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "httpsBasicAuthenticationEnabled", false);
        super.setUp();
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);

        // Try the attempt with authentication, it should fail because
        // BASIC auth is now disabled on secure connections.
        getRestTestHelper().setUsernameAndPassword(USERNAME, USERNAME);
        verifyGetBrokerAttempt(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
