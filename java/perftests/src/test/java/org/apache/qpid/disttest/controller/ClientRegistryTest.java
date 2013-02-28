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

import java.util.Timer;
import java.util.TimerTask;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.test.utils.QpidTestCase;

public class ClientRegistryTest extends QpidTestCase
{
    private static final String CLIENT1_REGISTERED_NAME = "CLIENT1_REGISTERED_NAME";
    private static final String CLIENT2_REGISTERED_NAME = "CLIENT2_REGISTERED_NAME";
    private static final String CLIENT3_REGISTERED_NAME = "CLIENT3_REGISTERED_NAME";

    private static final int AWAIT_DELAY = 100;

    private ClientRegistry _clientRegistry = new ClientRegistry();

    public void testRegisterClient()
    {
        assertEquals(0, _clientRegistry.getClients().size());

        _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);
        assertEquals(1, _clientRegistry.getClients().size());

    }

    public void testRejectsDuplicateClientNames()
    {
        _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);
        try
        {
            _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);
            fail("Should have thrown an exception");
        }
        catch (final DistributedTestException e)
        {
            // pass
        }
    }

    public void testAwaitOneClientWhenClientNotRegistered()
    {
        int numberOfClientsAbsent = _clientRegistry.awaitClients(1, AWAIT_DELAY);
        assertEquals(1, numberOfClientsAbsent);
    }

    public void testAwaitOneClientWhenClientAlreadyRegistered()
    {
        _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);

        int numberOfClientsAbsent = _clientRegistry.awaitClients(1, AWAIT_DELAY);
        assertEquals(0, numberOfClientsAbsent);
    }

    public void testAwaitTwoClientsWhenClientRegistersWhilstWaiting()
    {
        _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);
        registerClientLater(CLIENT2_REGISTERED_NAME, 50);

        int numberOfClientsAbsent = _clientRegistry.awaitClients(2, AWAIT_DELAY);
        assertEquals(0, numberOfClientsAbsent);
    }

    public void testAwaitTimeoutForPromptRegistrations()
    {
        registerClientsLaterAndAssertResult("Clients registering every 100ms should be within 600ms timeout",
                new int[] {300, 400, 500},
                600,
                0);
    }

    public void testAwaitTimeoutForWhenThirdRegistrationIsLate()
    {
        registerClientsLaterAndAssertResult("Third client registering tardily should exceed timeout",
                new int[] {300, 400, 1500},
                600,
                1);
    }

    public void testAwaitTimeoutWhenSecondAndThirdRegistrationsAreLate()
    {
        registerClientsLaterAndAssertResult("Second and third clients registering tardily should exceed timeout",
                new int[] {300, 1500, 1500},
                600,
                2);
    }

    private void registerClientsLaterAndAssertResult(String message, int[] registrationDelays, int timeout, int expectedNumberOfAbsentees)
    {
        registerClientLater(CLIENT1_REGISTERED_NAME, registrationDelays[0]);
        registerClientLater(CLIENT2_REGISTERED_NAME, registrationDelays[1]);
        registerClientLater(CLIENT3_REGISTERED_NAME, registrationDelays[2]);

        int numberOfClientsAbsent = _clientRegistry.awaitClients(3, timeout);

        assertEquals(message, expectedNumberOfAbsentees, numberOfClientsAbsent);
    }

    private void registerClientLater(final String clientName, long delayInMillis)
    {
        doLater(new TimerTask()
        {
            @Override
            public void run()
            {
                _clientRegistry.registerClient(clientName);
            }
        }, delayInMillis);
    }

    private void doLater(TimerTask task, long delayInMillis)
    {
        Timer timer = new Timer();
        timer.schedule(task, delayInMillis);
    }
}
