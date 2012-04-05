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

    public void registerClient(String clientName)
    {
        final boolean alreadyContainsClient = !_registeredClientNames.add(clientName);
        if (alreadyContainsClient)
        {
            throw new DistributedTestException("Duplicate client name " + clientName);
        }

        LOGGER.info("Client registered: " + clientName);
    }

    public Collection<String> getClients()
    {
        return Collections.unmodifiableSet(_registeredClientNames);
    }

}
