/*
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
 */
package org.apache.qpid.disttest.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.qpid.test.utils.QpidTestCase;

public class ParticipatingClientsTest extends QpidTestCase
{
    private static final String CLIENT1_CONFIGURED_NAME = "CLIENT1_CONFIGURED_NAME";
    private static final String CLIENT2_CONFIGURED_NAME = "CLIENT2_CONFIGURED_NAME";

    private static final String CLIENT1_REGISTERED_NAME = "CLIENT1_REGISTERED_NAME";
    private static final String CLIENT2_REGISTERED_NAME = "CLIENT2_REGISTERED_NAME";
    private static final String CLIENT3_REGISTERED_NAME = "CLIENT3_REGISTERED_NAME";
    private ClientRegistry _clientRegistry;
    private List<String> _configuredClientNamesForTest;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _clientRegistry = mock(ClientRegistry.class);
    }

    public void testTooFewRegisteredClientsForTest()
    {
        _configuredClientNamesForTest = Arrays.asList(CLIENT1_CONFIGURED_NAME, CLIENT2_CONFIGURED_NAME);
        when(_clientRegistry.getClients()).thenReturn(Arrays.asList(CLIENT1_REGISTERED_NAME));

        try
        {
            new ParticipatingClients(_clientRegistry, _configuredClientNamesForTest);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }

    }


    public void testSelectOneClientFromPoolOfOne()
    {
        _configuredClientNamesForTest = Arrays.asList(CLIENT1_CONFIGURED_NAME);
        when(_clientRegistry.getClients()).thenReturn(Arrays.asList(CLIENT1_REGISTERED_NAME));

        ParticipatingClients clients = new ParticipatingClients(_clientRegistry, _configuredClientNamesForTest);
        assertBothWays(clients, CLIENT1_REGISTERED_NAME, CLIENT1_CONFIGURED_NAME);
    }

    public void testSelectTwoClientFromPoolOfMany()
    {
        _configuredClientNamesForTest = Arrays.asList(CLIENT1_CONFIGURED_NAME, CLIENT2_CONFIGURED_NAME);
        when(_clientRegistry.getClients()).thenReturn(Arrays.asList(CLIENT1_REGISTERED_NAME, CLIENT2_REGISTERED_NAME, CLIENT3_REGISTERED_NAME));

        ParticipatingClients clients = new ParticipatingClients(_clientRegistry, _configuredClientNamesForTest);

        assertBothWays(clients, CLIENT1_REGISTERED_NAME, CLIENT1_CONFIGURED_NAME);
        assertBothWays(clients, CLIENT2_REGISTERED_NAME, CLIENT2_CONFIGURED_NAME);
    }

    public void testGetUnrecognisedConfiguredName()
    {
        _configuredClientNamesForTest = Arrays.asList(CLIENT1_CONFIGURED_NAME);
        when(_clientRegistry.getClients()).thenReturn(Arrays.asList(CLIENT1_REGISTERED_NAME));

        ParticipatingClients clients = new ParticipatingClients(_clientRegistry, _configuredClientNamesForTest);

        testUnrecognisedClientConfiguredName(clients, "unknown");
        testUnrecognisedClientRegisteredName(clients, "unknown");
    }

    public void testGetRegisteredClientNames()
    {
        _configuredClientNamesForTest = Arrays.asList(CLIENT1_CONFIGURED_NAME);
        List<String> registeredNames = Arrays.asList(CLIENT1_REGISTERED_NAME);
        when(_clientRegistry.getClients()).thenReturn(registeredNames);

        ParticipatingClients clients = new ParticipatingClients(_clientRegistry, _configuredClientNamesForTest);

        Collection<String> registeredParticipatingNames = clients.getRegisteredNames();
        assertEquals(1, registeredParticipatingNames.size());
        assertTrue(registeredParticipatingNames.contains(CLIENT1_REGISTERED_NAME));
    }

    private void testUnrecognisedClientConfiguredName(ParticipatingClients clients, String unrecognisedClientConfiguredName)
    {
        try
        {
            clients.getRegisteredNameFromConfiguredName(unrecognisedClientConfiguredName);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

    private void testUnrecognisedClientRegisteredName(ParticipatingClients clients, String unrecognisedClientRegisteredName)
    {
        try
        {
            clients.getConfiguredNameFromRegisteredName(unrecognisedClientRegisteredName);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException e)
        {
            // PASS
        }
    }

    private void assertBothWays(ParticipatingClients clients, String registeredName, String configuredName)
    {
        assertEquals(registeredName, clients.getRegisteredNameFromConfiguredName(configuredName));
        assertEquals(configuredName, clients.getConfiguredNameFromRegisteredName(registeredName));
    }



}
