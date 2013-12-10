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

import static java.lang.String.valueOf;

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

        synchronized (_lock)
        {
            _lock.notifyAll();
        }

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Client registered: " + clientName);
        }
    }


    public Collection<String> getClients()
    {
        return Collections.unmodifiableSet(_registeredClientNames);
    }

    /**
     * @return the number of clients that are still absent.
     */
    public int awaitClients(final int numberOfClientsToAwait, final long idleTimeout)
    {
        long startTime = System.currentTimeMillis();
        long deadlineForNextRegistration = deadline(idleTimeout);

        synchronized (_lock)
        {
            int numberOfClientsAbsent = numberAbsent(numberOfClientsToAwait);

            while(numberOfClientsAbsent > 0 && System.currentTimeMillis() < deadlineForNextRegistration)
            {
                try
                {
                    _lock.wait(idleTimeout);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return numberOfClientsAbsent;
                }

                int newNumberAbsent = numberAbsent(numberOfClientsToAwait);
                if(newNumberAbsent < numberOfClientsAbsent)
                {
                    // a registration was received since the last loop, so reset the timeout
                    deadlineForNextRegistration = deadline(idleTimeout);
                }

                numberOfClientsAbsent = newNumberAbsent;
            }

            int retVal = numberOfClientsAbsent < 0 ? 0 : numberOfClientsAbsent;
            logAwaitClients(numberOfClientsToAwait, idleTimeout, startTime, retVal);
            return retVal;
        }
    }


    private void logAwaitClients(int numberOfClientsToAwait, long idleTimeout, long startTime, int retVal)
    {
        LOGGER.debug(
                "awaitClients(numberOfClientsToAwait={}, idleTimeout={}) " +
                "returning numberOfClientsAbsent={} after {} ms",
                new Object[] {
                        valueOf(numberOfClientsToAwait),
                        valueOf(idleTimeout),
                        valueOf(retVal),
                        valueOf(System.currentTimeMillis() - startTime)});
    }

    private long deadline(final long idleTimeout)
    {
        return System.currentTimeMillis() + idleTimeout;
    }

    private int numberAbsent(int numberOfClientsToAwait)
    {
        return numberOfClientsToAwait - _registeredClientNames.size();
    }
}
