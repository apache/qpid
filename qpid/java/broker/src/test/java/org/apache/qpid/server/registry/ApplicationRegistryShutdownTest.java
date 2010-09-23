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
package org.apache.qpid.server.registry;

import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.apache.qpid.server.util.TestApplicationRegistry;

/**
 * QPID-1390 : Test to validate that the AuthenticationManger succesfully unregisters any new SASL providers when
 * The ApplicationRegistry is closed.
 *
 * This should be expanded as QPID-1399 is implemented.
 */
public class ApplicationRegistryShutdownTest extends TestCase
{
    IApplicationRegistry _registry;

    public void setUp() throws Exception
    {
        _registry = new TestApplicationRegistry();
    }

    @Override
    public void tearDown() throws Exception
    {
        // Close in case there was an error during the test
    	ApplicationRegistry.remove();
    }

    /**
     * QPID-1399 : Ensure that the Authentiction manager unregisters any SASL providers created during
     * ApplicationRegistry initialisation.
     */
    public void testAuthenticationMangerCleansUp() throws Exception
    {
        // Get default providers
        List<Provider> before = Arrays.asList(Security.getProviders());

        // Register new providers
        ApplicationRegistry.initialise(_registry);

        // Get the providers after initialisation
        List<Provider> after = Arrays.asList(Security.getProviders());

        // Find the additions
        List<Provider> additions = new ArrayList<Provider>();
        for (Provider provider : after)
        {
            if (!before.contains(provider))
            {
	            additions.add(provider);
            }
        }

        assertFalse("No new SASL mechanisms added by initialisation.", additions.isEmpty());

        //Close the registry which will perform the close the AuthenticationManager
        ApplicationRegistry.remove(ApplicationRegistry.DEFAULT_INSTANCE);

        //Validate that the SASL plugins have been removed.
        List<Provider> closed = Arrays.asList(Security.getProviders());

        assertEquals("No providers unregistered", before.size(), closed.size());

        //Ensure that the additions are not still present after close().
        for (Provider provider : closed)
        {
            assertFalse("Added provider not unregistered: " + provider.getName(), additions.contains(provider));
        }
    }
}
