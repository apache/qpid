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
package org.apache.qpid.client.security;

import java.io.File;
import java.security.Provider;
import java.security.Security;

import org.apache.qpid.client.security.DynamicSaslRegistrar.ProviderRegistrationResult;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class DynamicSaslRegistrarTest extends QpidTestCase
{
    private Provider _registeredProvider;

    public void setUp() throws Exception
    {
        super.setUp();

        //If the client provider is already registered, remove it for the duration of the test
        _registeredProvider = DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME);
        if (_registeredProvider != null)
        {
            Security.removeProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME);
        }
    }

    public void tearDown() throws Exception
    {
        //Remove any provider left behind by the test.
        Security.removeProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME);
        try
        {
            //If the client provider was already registered before the test, restore it.
            if (_registeredProvider != null)
            {
                Security.insertProviderAt(_registeredProvider, 1);
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testRegisterDefaultProvider()
    {
        assertNull("Provider should not yet be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        ProviderRegistrationResult firstRegistrationResult = DynamicSaslRegistrar.registerSaslProviders();
        assertEquals("Unexpected registration result", ProviderRegistrationResult.SUCCEEDED, firstRegistrationResult);
        assertNotNull("Providers should now be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));
    }

    public void testRegisterDefaultProviderTwice()
    {
        assertNull("Provider should not yet be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        DynamicSaslRegistrar.registerSaslProviders();
        assertNotNull("Providers should now be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        ProviderRegistrationResult result = DynamicSaslRegistrar.registerSaslProviders();
        assertEquals("Unexpected registration result when trying to re-register", ProviderRegistrationResult.EQUAL_ALREADY_REGISTERED, result);
        assertNotNull("Providers should still be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));
    }

    @SuppressWarnings("serial")
    public void testRegisterDefaultProviderWhenAnotherIsAlreadyPresentWithDifferentFactories()
    {
        assertNull("Provider should not be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        //Add a test provider with the same name, version, info as the default client provider, but with different factory properties (none).
        Provider testProvider = new Provider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME,
                                             JCAProvider.QPID_CLIENT_SASL_PROVIDER_VERSION,
                                             JCAProvider.QPID_CLIENT_SASL_PROVIDER_INFO){};
        Security.addProvider(testProvider);
        assertSame("Test provider should be registered", testProvider, DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        //Try to register the default provider now that another with the same name etc (but different factories)
        //is already registered, expect it not to be registered as a result.
        ProviderRegistrationResult result = DynamicSaslRegistrar.registerSaslProviders();
        assertEquals("Unexpected registration result", ProviderRegistrationResult.DIFFERENT_ALREADY_REGISTERED, result);

        //Verify the test provider is still registered
        assertSame("Test provider should still be registered", testProvider, DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));
    }

    public void testRegisterWithNoFactories()
    {
        File emptyTempFile = TestFileUtils.createTempFile(this);

        assertNull("Provider should not be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        //Adjust the location of the properties file to point at an empty file, so no factories are found to register.
        setTestSystemProperty("amq.dynamicsaslregistrar.properties", emptyTempFile.getPath());

        //Try to register the default provider, expect it it not to be registered because there were no factories.
        ProviderRegistrationResult result = DynamicSaslRegistrar.registerSaslProviders();
        assertEquals("Unexpected registration result", ProviderRegistrationResult.NO_SASL_FACTORIES, result);

        assertNull("Provider should not be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));
    }

    public void testRegisterWithMissingFileGetsDefault()
    {
        //Create a temp file and then delete it, such that we get a path which doesn't exist
        File tempFile = TestFileUtils.createTempFile(this);
        assertTrue("Failed to delete file", tempFile.delete());

        assertNull("Provider should not be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));

        //Adjust the location of the properties file to point at non-existent file.
        setTestSystemProperty("amq.dynamicsaslregistrar.properties", tempFile.getPath());

        //Try to register the default provider, expect it to fall back to the default in the jar and succeed.
        ProviderRegistrationResult result = DynamicSaslRegistrar.registerSaslProviders();
        assertEquals("Unexpected registration result", ProviderRegistrationResult.SUCCEEDED, result);

        assertNotNull("Provider should be registered", DynamicSaslRegistrar.findProvider(JCAProvider.QPID_CLIENT_SASL_PROVIDER_NAME));
    }
}
