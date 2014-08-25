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

import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.KEYSTORE_PASSWORD;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE;
import static org.apache.qpid.test.utils.TestSSLConstants.TRUSTSTORE_PASSWORD;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.security.auth.manager.ExternalAuthenticationManager;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class BrokerRestHttpsClientCertAuthTest extends QpidRestTestCase
{

    @Override
    public void setUp() throws Exception
    {
        setSystemProperty("javax.net.debug", "ssl");
        super.setUp();
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        setSystemProperty("javax.net.ssl.keystore", KEYSTORE);
        setSystemProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASSWORD);

    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().setUseSslAuth(true);
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.HTTP));
        newAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        newAttributes.put(Port.KEY_STORE, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE);
        newAttributes.put(Port.TRUST_STORES, Collections.singleton(TestBrokerConfiguration.ENTRY_NAME_SSL_TRUSTSTORE));
        newAttributes.put(Port.NEED_CLIENT_AUTH,"true");


        Map<String, Object> externalProviderAttributes = new HashMap<String, Object>();
        externalProviderAttributes.put(AuthenticationProvider.TYPE, ExternalAuthenticationManager.PROVIDER_TYPE);
        externalProviderAttributes.put(AuthenticationProvider.NAME, EXTERNAL_AUTHENTICATION_PROVIDER);
        getBrokerConfiguration().addObjectConfiguration(AuthenticationProvider.class, externalProviderAttributes);

        // set password authentication provider on http port for the tests
        getBrokerConfiguration().setObjectAttribute(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER,
                                                    EXTERNAL_AUTHENTICATION_PROVIDER);

        getBrokerConfiguration().setObjectAttributes(Port.class, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, newAttributes);
    }

    public void testGetWithHttps() throws Exception
    {
        Map<String, Object> saslData = getRestTestHelper().getJsonAsMap("/service/sasl");

        Asserts.assertAttributesPresent(saslData, "user");
    }
}
