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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.qpid.disttest.DistributedTestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientRegistry
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRegistry.class);

    private final Set<String> _registeredClientNames = new ConcurrentSkipListSet<String>();

    private final Object _lock = new Object();

    public void registerClient(String clientName)
    {
        final boolean alreadyContainsClient = !_registeredClientNames.add(clientName);
        if (alreadyContainsClient)
        {
            throw new DistributedTestException("Duplicate client name " + clientName);
        }

        notifyAllWaiters();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Client registered: " + clientName);
        }
    }


    public Collection<String> getClients()
    {
        return Collections.unmodifiableSet(_registeredClientNames);
    }

    public int awaitClients(int numberOfClientsToAwait, long timeout)
    {
        final long endTime = System.currentTimeMillis() + timeout;

        int numberOfClientsAbsent = numberOfClientsToAwait - _registeredClientNames.size();
        long remainingTimeout = endTime - System.currentTimeMillis();

        while(numberOfClientsAbsent > 0 && remainingTimeout > 0)
        {
            synchronized (_lock)
            {
                try
                {
                    _lock.wait(remainingTimeout);
                }
                catch (InterruptedException e)
                {
                   Thread.currentThread().interrupt();
                }
            }

            numberOfClientsAbsent = numberOfClientsToAwait - _registeredClientNames.size();
            remainingTimeout = endTime - System.currentTimeMillis();
        }

        return numberOfClientsAbsent < 0 ? 0 : numberOfClientsAbsent;
    }

    private void notifyAllWaiters()
    {
        synchronized (_lock)
        {
            _lock.notifyAll();
        }
    }

}
