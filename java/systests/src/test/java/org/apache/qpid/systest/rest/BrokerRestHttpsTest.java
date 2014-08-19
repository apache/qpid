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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class BrokerRestHttpsTest extends QpidRestTestCase
{
    @Override
    public void setUp() throws Exception
    {
        setSystemProperty("javax.net.debug", "ssl");
        super.setUp();
        setSystemProperty("javax.net.ssl.trustStore", TRUSTSTORE);
        setSystemProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
    }

    @Override
    protected void customizeConfiguration() throws IOException
    {
        super.customizeConfiguration();
        getRestTestHelper().setUseSsl(true);
        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(Port.PROTOCOLS, Collections.singleton(Protocol.HTTP));
        newAttributes.put(Port.TRANSPORTS, Collections.singleton(Transport.SSL));
        newAttributes.put(Port.KEY_STORE, TestBrokerConfiguration.ENTRY_NAME_SSL_KEYSTORE);
        getBrokerConfiguration().setObjectAttributes(Port.class,TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT,newAttributes);
    }

    public void testGetWithHttps() throws Exception
    {
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");

        Asserts.assertAttributesPresent(brokerDetails, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Broker.class),
                Broker.PROCESS_PID,
                Broker.CONFIDENTIAL_CONFIGURATION_ENCRYPTION_PROVIDER,
                ConfiguredObject.TYPE,
                ConfiguredObject.CREATED_BY,
                ConfiguredObject.CREATED_TIME,
                ConfiguredObject.LAST_UPDATED_BY,
                ConfiguredObject.LAST_UPDATED_TIME,
                ConfiguredObject.DESCRIPTION,
                ConfiguredObject.CONTEXT,
                ConfiguredObject.DESIRED_STATE);
    }
}
