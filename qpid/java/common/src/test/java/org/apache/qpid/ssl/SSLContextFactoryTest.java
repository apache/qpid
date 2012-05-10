/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.ssl;

import org.apache.qpid.test.utils.QpidTestCase;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;

public class SSLContextFactoryTest extends QpidTestCase
{
    private static final String BROKER_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_broker_keystore.jks";
    private static final String CLIENT_KEYSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_client_keystore.jks";
    private static final String CLIENT_TRUSTSTORE_PATH = TEST_RESOURCES_DIR + "/ssl/java_client_truststore.jks";
    private static final String STORE_PASSWORD = "password";
    private static final String STORE_TYPE = "JKS";
    private static final String DEFAULT_KEY_MANAGER_ALGORITHM = KeyManagerFactory.getDefaultAlgorithm();
    private static final String DEFAULT_TRUST_MANAGER_ALGORITHM = TrustManagerFactory.getDefaultAlgorithm();
    private static final String CERT_ALIAS_APP1 = "app1";

    public void testBuildServerContext() throws Exception
    {
        SSLContext context = SSLContextFactory.buildServerContext(BROKER_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_KEY_MANAGER_ALGORITHM);
        assertNotNull("SSLContext should not be null", context);
    }

    public void testBuildServerContextWithIncorrectPassword() throws Exception
    {
        try
        {
            SSLContextFactory.buildServerContext(BROKER_KEYSTORE_PATH, "sajdklsad", STORE_TYPE, DEFAULT_KEY_MANAGER_ALGORITHM);
            fail("Exception was not thrown due to incorrect password");
        }
        catch (IOException e)
        {
            //expected
        }
    }
    
    public void testTrustStoreDoesNotExist() throws Exception
    {
        try
        {
            SSLContextFactory.buildClientContext("/path/to/nothing", STORE_PASSWORD, STORE_TYPE, DEFAULT_TRUST_MANAGER_ALGORITHM, CLIENT_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_KEY_MANAGER_ALGORITHM, null);
            fail("Exception was not thrown due to incorrect path");
        }
        catch (IOException e)
        {
            //expected
        }
    }

    public void testBuildClientContextForSSLEncryptionOnly() throws Exception
    {
        SSLContext context = SSLContextFactory.buildClientContext(CLIENT_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_TRUST_MANAGER_ALGORITHM, null, null, null, null, null);
        assertNotNull("SSLContext should not be null", context);
    }

    public void testBuildClientContextWithForClientAuth() throws Exception
    {
        SSLContext context = SSLContextFactory.buildClientContext(CLIENT_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_TRUST_MANAGER_ALGORITHM, CLIENT_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_KEY_MANAGER_ALGORITHM, null);
        assertNotNull("SSLContext should not be null", context);
    }
    
    public void testBuildClientContextWithForClientAuthWithCertAlias() throws Exception
    {
        SSLContext context = SSLContextFactory.buildClientContext(CLIENT_TRUSTSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_TRUST_MANAGER_ALGORITHM, CLIENT_KEYSTORE_PATH, STORE_PASSWORD, STORE_TYPE, DEFAULT_KEY_MANAGER_ALGORITHM, CERT_ALIAS_APP1);
        assertNotNull("SSLContext should not be null", context);
    }
}
