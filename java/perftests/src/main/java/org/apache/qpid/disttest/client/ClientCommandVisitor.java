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
package org.apache.qpid.disttest.client;

import org.apache.qpid.disttest.Visitor;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.disttest.message.StartTestCommand;
import org.apache.qpid.disttest.message.StopClientCommand;
import org.apache.qpid.disttest.message.TearDownTestCommand;

public class ClientCommandVisitor extends Visitor
{
    private final Client _client;
    private final ClientJmsDelegate _clientJmsDelegate;

    public ClientCommandVisitor(final Client client, final ClientJmsDelegate clientJmsDelegate)
    {
        super();
        _client = client;
        _clientJmsDelegate = clientJmsDelegate;
    }

    public void visit(final StopClientCommand command)
    {

        _client.stop();
    }

    public void visit(final NoOpCommand command)
    {
        // no-op
    }

    public void visit(final CreateConnectionCommand command)
    {
        _clientJmsDelegate.createConnection(command);
    }

    public void visit(final CreateSessionCommand command)
    {
        _clientJmsDelegate.createSession(command);
    }

    public void visit(final CreateProducerCommand command)
    {

        final ProducerParticipant participant = new ProducerParticipant(_clientJmsDelegate, command);
        _clientJmsDelegate.createProducer(command);
        final ParticipantExecutor executor = new ParticipantExecutor(participant);
        _client.addParticipantExecutor(executor);
    }

    public void visit(final CreateConsumerCommand command)
    {
        final ConsumerParticipant participant = new ConsumerParticipant(_clientJmsDelegate, command);
        _clientJmsDelegate.createConsumer(command);
        final ParticipantExecutor executor = new ParticipantExecutor(participant);
        _client.addParticipantExecutor(executor);
    }

    public void visit(final StartTestCommand command)
    {
        _client.startTest();
    }

    public void visit(final TearDownTestCommand command)
    {
        _client.tearDownTest();
    }

    public void visit(final CreateMessageProviderCommand command)
    {
        _clientJmsDelegate.createMessageProvider(command);
    }

}
