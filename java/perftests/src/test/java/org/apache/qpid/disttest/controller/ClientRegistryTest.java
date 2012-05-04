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

import junit.framework.TestCase;

import org.apache.qpid.disttest.DistributedTestException;

public class ClientRegistryTest extends TestCase
{
    private static final String CLIENT1_REGISTERED_NAME = "CLIENT1_REGISTERED_NAME";
    private static final String CLIENT2_REGISTERED_NAME = "CLIENT2_REGISTERED_NAME";
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

    public void testAwaitTwoClientWhenClientRegistersWhilstWaiting()
    {
        _clientRegistry.registerClient(CLIENT1_REGISTERED_NAME);
        registerClientLater(CLIENT2_REGISTERED_NAME, 50);

        int numberOfClientsAbsent = _clientRegistry.awaitClients(2, AWAIT_DELAY);
        assertEquals(0, numberOfClientsAbsent);
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
