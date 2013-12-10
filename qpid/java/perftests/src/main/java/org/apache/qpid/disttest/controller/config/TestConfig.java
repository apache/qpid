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
 *
 */
package org.apache.qpid.disttest.controller.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.disttest.controller.CommandForClient;

public class TestConfig
{
    private final String _name;

    private final List<ClientConfig> _clients;

    private final List<QueueConfig> _queues;

    private final List<Map<String, String>> _iterations;

    public TestConfig()
    {
        _clients = Collections.emptyList();
        _queues = Collections.emptyList();
        _name = null;
        _iterations = Collections.emptyList();
    }

    public TestConfig(String name, ClientConfig[] clients, QueueConfig[] queues)
    {
        _clients = Arrays.asList(clients);
        _queues = Arrays.asList(queues);
        _name = name;
        _iterations = Collections.emptyList();
    }

    public List<String> getClientNames()
    {
        List<String> clientNames = new ArrayList<String>();
        for (ClientConfig clientConfig : _clients)
        {
            clientNames.add(clientConfig.getName());
        }
        return clientNames;
    }

    public int getTotalNumberOfClients()
    {
        return _clients.size();
    }

    public int getTotalNumberOfParticipants()
    {
        int numOfParticipants = 0;
        for (ClientConfig client : _clients)
        {
            numOfParticipants = numOfParticipants + client.getTotalNumberOfParticipants();
        }
        return numOfParticipants;
    }

    public List<CommandForClient> createCommands()
    {
        List<CommandForClient> commandsForClients = new ArrayList<CommandForClient>();
        for (ClientConfig client : _clients)
        {
            commandsForClients.addAll(client.createCommands());
        }

        return Collections.unmodifiableList(commandsForClients);
    }

    public List<QueueConfig> getQueues()
    {
        return Collections.unmodifiableList(_queues);
    }

    public String getName()
    {
        return _name;
    }

    public List<IterationValue> getIterationValues()
    {
        List<IterationValue> iterationValues = new ArrayList<IterationValue>();
        for (Map<String, String> iterationMap : _iterations)
        {
            iterationValues.add(new IterationValue(iterationMap));
        }

        return iterationValues;
    }

    public List<ClientConfig> getClients()
    {
        return Collections.unmodifiableList(_clients);
    }
}
