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

import static org.apache.qpid.disttest.controller.config.ConfigTestUtils.assertCommandEquals;
import static org.apache.qpid.disttest.controller.config.ConfigTestUtils.getCommand;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.test.utils.QpidTestCase;

public class ConnectionConfigTest extends QpidTestCase
{
    private static final String CONNECTION_FACTORY_NAME = "ConnectionFactoryName";
    private static final String CONNECTION_NAME = "ConnectionName";

    public void testConnectionConfigHasZeroArgConstructorForGson()
    {
        ConnectionConfig c = new ConnectionConfig();
        assertNotNull(c);
    }

    public void testCreateCommandsForConnectionAndChildren()
    {
        ConnectionConfig connectionConfig = createConnectionConfigWithChildCommands();

        List<Command> commands = connectionConfig.createCommands();
        assertEquals(3, commands.size());

        assertCommandEquals(commands, 0, CreateConnectionCommand.class);
        assertCommandEquals(commands, 1, NoOpCommand.class);
        assertCommandEquals(commands, 2, NoOpCommand.class);

        CreateConnectionCommand createConnectionCommand = getCommand(commands, 0);
        assertEquals(CONNECTION_NAME, createConnectionCommand.getConnectionName());
        assertEquals(CONNECTION_FACTORY_NAME, createConnectionCommand.getConnectionFactoryName());
    }

    public void testGetTotalNumberOfParticipants()
    {
        ConnectionConfig connectionConfig = createConnectionConfigWithTwoParticipants();
        assertEquals(2, connectionConfig.getTotalNumberOfParticipants());
    }

    private ConnectionConfig createConnectionConfigWithTwoParticipants()
    {
        SessionConfig sessionConfig1 = mock(SessionConfig.class);
        SessionConfig sessionConfig2 = mock(SessionConfig.class);

        when(sessionConfig1.getTotalNumberOfParticipants()).thenReturn(1);
        when(sessionConfig2.getTotalNumberOfParticipants()).thenReturn(1);

        ConnectionConfig connectionConfig = new ConnectionConfig(CONNECTION_NAME, CONNECTION_FACTORY_NAME, sessionConfig1, sessionConfig2);

        return connectionConfig;
    }

    private ConnectionConfig createConnectionConfigWithChildCommands()
    {
        SessionConfig sessionConfig = mock(SessionConfig.class);

        NoOpCommand cmd1 = mock(NoOpCommand.class);
        NoOpCommand cmd2 = mock(NoOpCommand.class);
        List<Command> commands = Arrays.asList((Command)cmd1, (Command)cmd2);
        when(sessionConfig.createCommands(CONNECTION_NAME)).thenReturn(commands);

        return new ConnectionConfig(CONNECTION_NAME, CONNECTION_FACTORY_NAME, sessionConfig);
    }

}
