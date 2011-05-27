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

import org.apache.qpid.server.util.InternalBrokerBaseCase;

import java.security.Security;
import java.security.Provider;
import java.util.List;
import java.util.LinkedList;

/**
 * QPID-1390 : Test to validate that the AuthenticationManger can successfully unregister any new SASL providers when
 * The ApplicationRegistry is closed.
 *
 * This should be expanded as QPID-1399 is implemented.
 */
public class ApplicationRegistryShutdownTest extends InternalBrokerBaseCase
{

    Provider[] _defaultProviders;
    @Override
    public void setUp() throws Exception
    {
        // Get default providers
        _defaultProviders = Security.getProviders();

        //Startup the new broker and register the new providers
        super.setUp();
    }


    /**
     * QPID-1399 : Ensure that the Authentiction manager unregisters any SASL providers created during
     * ApplicationRegistry initialisation.
     *
     */
    public void testAuthenticationMangerCleansUp() throws Exception
    {

        // Get the providers after initialisation
        Provider[] providersAfterInitialisation = Security.getProviders();

        // Find the additions
        List additions = new LinkedList();
        for (Provider afterInit : providersAfterInitialisation)
        {
            boolean found = false;
            for (Provider defaultProvider : _defaultProviders)
            {
                if (defaultProvider == afterInit)
                {
                    found=true;
                    break;
                }
            }

            // Record added registies
            if (!found)
            {
                additions.add(afterInit);
            }
        }

        // Not using isEmpty as that is not in Java 5
        assertTrue("No new SASL mechanisms added by initialisation.", additions.size() != 0 );

        //Close the registry which will perform the close the AuthenticationManager
        getRegistry().close();

        //Validate that the SASL plugFins have been removed.
        Provider[] providersAfterClose = Security.getProviders();

        assertTrue("No providers unregistered", providersAfterInitialisation.length > providersAfterClose.length);

        //Ensure that the additions are not still present after close().
        for (Provider afterClose : providersAfterClose)
        {
            assertFalse("Added provider not unregistered", additions.contains(afterClose));
        }
    }





}
