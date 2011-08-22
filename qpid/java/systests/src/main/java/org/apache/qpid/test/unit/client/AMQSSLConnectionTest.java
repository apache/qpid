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

package org.apache.qpid.test.unit.client;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;

public class AMQSSLConnectionTest extends AMQConnectionTest
{
    private static final String KEYSTORE = TEST_RESOURCES_DIR + "/ssl/java_client_keystore.jks";
    private static final String KEYSTORE_PASSWORD = "password";
    private static final String TRUSTSTORE = TEST_RESOURCES_DIR + "/ssl/java_client_truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password";
    
    @Override
    protected void setUp() throws Exception 
    {
        setTestClientSystemProperty("profile.use_ssl", "true");
        setConfigurationProperty("connector.ssl.enabled", "true");
        setConfigurationProperty("connector.ssl.sslOnly", "true");
        super.setUp();
    }

    protected void createConnection() throws Exception
    {
        
        final String sslPrototypeUrl = "amqp://guest:guest@test/?brokerlist='tcp://localhost:%s" +
        "?ssl='true'&ssl_verify_hostname='false'" + 
        "&key_store='%s'&key_store_password='%s'" +
        "&trust_store='%s'&trust_store_password='%s'" +
        "'";

        final String url = String.format(sslPrototypeUrl,System.getProperty("test.port.ssl"),
                KEYSTORE,KEYSTORE_PASSWORD,TRUSTSTORE,TRUSTSTORE_PASSWORD);
        
        _connection = (AMQConnection) getConnection(new AMQConnectionURL(url));
    }
}
