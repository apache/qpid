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

import static org.apache.qpid.disttest.controller.config.ConfigTestUtils.assertCommandForClient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.client.property.SimplePropertyValue;
import org.apache.qpid.disttest.controller.CommandForClient;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.test.utils.QpidTestCase;

public class ClientConfigTest extends QpidTestCase
{
    private static final String CLIENT1 = "client1";

    public void testClientConfigHasZeroArgConstructorForGson()
    {
        ClientConfig c = new ClientConfig();
        assertNotNull(c);
    }

    public void testCreateCommands()
    {
        ClientConfig clientConfig = createClientConfigWithConnectionConfigReturningChildCommands();

        List<CommandForClient> commands = clientConfig.createCommands();
        assertEquals(2, commands.size());

        assertCommandForClient(commands, 0, CLIENT1, NoOpCommand.class);
        assertCommandForClient(commands, 1, CLIENT1, NoOpCommand.class);
    }

    public void testCreateCommandsForMessageProvider()
    {
        ClientConfig clientConfig = createClientConfigWithMessageProviderConfigReturningCommands();

        List<CommandForClient> commands = clientConfig.createCommands();
        assertEquals(1, commands.size());

        assertCommandForClient(commands, 0, CLIENT1, CreateMessageProviderCommand.class);
    }

    public void testGetTotalNumberOfParticipants()
    {
        ClientConfig clientConfig = createClientConfigWithTwoParticipants();
        assertEquals(2, clientConfig.getTotalNumberOfParticipants());
    }

    private ClientConfig createClientConfigWithConnectionConfigReturningChildCommands()
    {
        ConnectionConfig connectionConfig = mock(ConnectionConfig.class);

        List<Command> commands = Arrays.asList((Command)new NoOpCommand(), (Command)new NoOpCommand());
        when(connectionConfig.createCommands()).thenReturn(commands);

        return new ClientConfig(CLIENT1, connectionConfig);
    }

    private ClientConfig createClientConfigWithMessageProviderConfigReturningCommands()
    {
        Map<String, PropertyValue> messageProperties = new HashMap<String, PropertyValue>();
        messageProperties.put("test", new SimplePropertyValue("testValue"));
        MessageProviderConfig config = new MessageProviderConfig("test", messageProperties);

        List<MessageProviderConfig> providerConfigs = new ArrayList<MessageProviderConfig>();
        providerConfigs.add(config);

        return new ClientConfig(CLIENT1, new ArrayList<ConnectionConfig>(), providerConfigs);
    }

    private ClientConfig createClientConfigWithTwoParticipants()
    {
        ConnectionConfig connectionConfig1 = mock(ConnectionConfig.class);
        ConnectionConfig connectionConfig2 = mock(ConnectionConfig.class);

        when(connectionConfig1.getTotalNumberOfParticipants()).thenReturn(1);
        when(connectionConfig2.getTotalNumberOfParticipants()).thenReturn(1);

        ClientConfig clientConfig = new ClientConfig(CLIENT1, connectionConfig1, connectionConfig2);
        return clientConfig;
    }
}
