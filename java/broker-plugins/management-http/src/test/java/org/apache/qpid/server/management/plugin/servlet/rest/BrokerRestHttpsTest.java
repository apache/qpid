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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.model.Broker;

public class BrokerRestHttpsTest extends QpidRestTestCase
{
    private static final String TRUSTSTORE = "test-profiles/test_resources/ssl/java_client_truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";

    @Override
    public void setUp() throws Exception
    {
        setSystemProperty("javax.net.debug", "ssl");
        super.setUp();
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
    }

    @Override
    protected void customizeConfiguration() throws ConfigurationException, IOException
    {
        getRestTestHelper().setUseSsl(true);
        setConfigurationProperty("management.enabled", "true");
        setConfigurationProperty("management.http.enabled", "false");
        setConfigurationProperty("management.https.enabled", "true");
        setConfigurationProperty("management.https.port", Integer.toString(getRestTestHelper().getHttpPort()));
    }

    public void testGetWithHttps() throws Exception
    {
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");

        Asserts.assertAttributesPresent(brokerDetails, Broker.AVAILABLE_ATTRIBUTES, Broker.BYTES_RETAINED,
                Broker.PROCESS_PID, Broker.SUPPORTED_STORE_TYPES, Broker.CREATED, Broker.TIME_TO_LIVE, Broker.UPDATED);
    }
}
