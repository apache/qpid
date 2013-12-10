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

import java.util.Collections;
import java.util.List;

import javax.jms.Session;

import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.test.utils.QpidTestCase;

public class SessionConfigTest extends QpidTestCase
{
    private static final String CONNECTION_NAME = "conn1";
    private static final String SESSION = "session1";

    public void testSessionHasZeroArgConstructorForGson()
    {
        SessionConfig s = new SessionConfig();
        assertNotNull(s);
    }

    public void testCreateCommandsForSessionAndChildren()
    {
        SessionConfig sessionConfig = createSessionConfigWithChildCommands();

        List<Command> commands = sessionConfig.createCommands(CONNECTION_NAME);
        assertEquals(3, commands.size());

        assertCommandEquals(commands, 0, CreateSessionCommand.class);
        assertCommandEquals(commands, 1, CreateProducerCommand.class);
        assertCommandEquals(commands, 2, CreateConsumerCommand.class);

        CreateSessionCommand createSessionCommand = getCommand(commands, 0);
        assertEquals(Session.AUTO_ACKNOWLEDGE, createSessionCommand.getAcknowledgeMode());
        assertEquals(SESSION, createSessionCommand.getSessionName());
        assertEquals(CONNECTION_NAME, createSessionCommand.getConnectionName());
    }

    public void testGetTotalNumberOfParticipants()
    {
        SessionConfig sessionConfig = createSessionConfigWithOneConsumerAndOneProducer();
        assertEquals(2, sessionConfig.getTotalNumberOfParticipants());
    }

    private SessionConfig createSessionConfigWithOneConsumerAndOneProducer()
    {
        return createSessionConfigWithChildCommands();
    }

    private SessionConfig createSessionConfigWithChildCommands()
    {
        ProducerConfig producerConfig = mock(ProducerConfig.class);
        ConsumerConfig consumerConfig = mock(ConsumerConfig.class);

        when(producerConfig.createCommand(SESSION)).thenReturn(mock(CreateProducerCommand.class));
        when(consumerConfig.createCommand(SESSION)).thenReturn(mock(CreateConsumerCommand.class));

        return new SessionConfig(SESSION,
                                 Session.AUTO_ACKNOWLEDGE,
                                 Collections.singletonList(consumerConfig),
                                 Collections.singletonList(producerConfig));
    }


}
