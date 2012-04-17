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

import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateConnectionCommand;

public class ConnectionConfig
{
    private String _name;
    private List<SessionConfig> _sessions;
    private String _factory;

    // For Gson
    public ConnectionConfig()
    {
        super();
        _sessions = Collections.emptyList();
    }

    public ConnectionConfig(String name, String factory, SessionConfig... sessions)
    {
        super();
        _name = name;
        _factory = factory;
        _sessions = Arrays.asList(sessions);

    }

    public List<SessionConfig> getSessions()
    {
        return Collections.unmodifiableList(_sessions);
    }

    public String getName()
    {
        return _name;
    }

    public List<Command> createCommands()
    {
        List<Command> commands = new ArrayList<Command>();
        commands.add(createCommand());
        for (SessionConfig sessionConfig : _sessions)
        {
            commands.addAll(sessionConfig.createCommands(_name));
        }
        return commands;
    }

    private CreateConnectionCommand createCommand()
    {
        CreateConnectionCommand command = new CreateConnectionCommand();
        command.setConnectionName(_name);
        command.setConnectionFactoryName(_factory);
        return command;
    }

    public int getTotalNumberOfParticipants()
    {
        int numOfParticipants = 0;

        for (SessionConfig sessionConfig : _sessions)
        {
            numOfParticipants = numOfParticipants + sessionConfig.getTotalNumberOfParticipants();
        }
        return numOfParticipants;
    }
}
