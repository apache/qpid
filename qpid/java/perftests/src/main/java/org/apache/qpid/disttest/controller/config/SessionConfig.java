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

import javax.jms.Session;

import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateSessionCommand;

public class SessionConfig
{
    private static final List<ProducerConfig> EMPTY_PRODUCER_LIST = Collections.emptyList();
    private static final List<ConsumerConfig> EMPTY_CONSUMER_LIST = Collections.emptyList();

    private int _acknowledgeMode;
    private String _sessionName;
    private List<ProducerConfig> _producers;
    private List<ConsumerConfig> _consumers;

    // For Gson
    public SessionConfig()
    {
        this(null, Session.SESSION_TRANSACTED, EMPTY_CONSUMER_LIST, EMPTY_PRODUCER_LIST);
    }

    public SessionConfig(String sessionName, int acknowledgeMode, ProducerConfig...producers)
    {
        this(sessionName, acknowledgeMode, EMPTY_CONSUMER_LIST, Arrays.asList(producers));
    }

    public SessionConfig(String sessionName, int acknowledgeMode, ConsumerConfig... consumers)
    {
        this(sessionName, acknowledgeMode, Arrays.asList(consumers), EMPTY_PRODUCER_LIST);
    }

    public SessionConfig(String sessionName, int acknowledgeMode, List<ConsumerConfig> consumers, List<ProducerConfig> producers)
    {
        _sessionName = sessionName;
        _acknowledgeMode = acknowledgeMode;
        _consumers = consumers;
        _producers = producers;
    }

    public int getAcknowledgeMode()
    {
        return _acknowledgeMode;
    }

    public String getSessionName()
    {
        return _sessionName;
    }

    public List<ProducerConfig> getProducers()
    {
        return Collections.unmodifiableList(_producers);
    }

    public List<ConsumerConfig> getConsumers()
    {
        return Collections.unmodifiableList(_consumers);
    }

    public List<Command> createCommands(String connectionName)
    {
        List<Command> commands = new ArrayList<Command>();
        commands.add(createCommand(connectionName));
        for (ProducerConfig producer : _producers)
        {
            commands.add(producer.createCommand(_sessionName));
        }
        for (ConsumerConfig consumer : _consumers)
        {
            commands.add(consumer.createCommand(_sessionName));
        }
        return commands;
    }

    private CreateSessionCommand createCommand(String connectionName)
    {
        CreateSessionCommand command = new CreateSessionCommand();
        command.setAcknowledgeMode(_acknowledgeMode);
        command.setConnectionName(connectionName);
        command.setSessionName(_sessionName);
        return command;
    }

    public int getTotalNumberOfParticipants()
    {
        return _producers.size() + _consumers.size();
    }
}
