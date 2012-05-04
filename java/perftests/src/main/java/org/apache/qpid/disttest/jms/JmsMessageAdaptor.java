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
package org.apache.qpid.disttest.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.qpid.disttest.DistributedTestConstants;
import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.json.JsonHandler;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateMessageProviderCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.CreateResponderCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StartTestCommand;
import org.apache.qpid.disttest.message.StopClientCommand;
import org.apache.qpid.disttest.message.TearDownTestCommand;

public class JmsMessageAdaptor
{
    public static Message commandToMessage(final Session session, final Command command)
    {
        Message jmsMessage = null;
        try
        {
            jmsMessage = session.createMessage();
            jmsMessage.setStringProperty(DistributedTestConstants.MSG_COMMAND_PROPERTY, command.getType().name());
            final JsonHandler jsonHandler = new JsonHandler();
            jmsMessage.setStringProperty(DistributedTestConstants.MSG_JSON_PROPERTY, jsonHandler.marshall(command));
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to convert command " + command + " to JMS Message", jmse);
        }

        return jmsMessage;
    }

    public static Command messageToCommand(final Message jmsMessage)
    {
        Command command = null;
        try
        {
            final CommandType commandType = CommandType.valueOf(jmsMessage
                            .getStringProperty(DistributedTestConstants.MSG_COMMAND_PROPERTY));
            final JsonHandler jsonHandler = new JsonHandler();
            command = jsonHandler.unmarshall(jmsMessage.getStringProperty(DistributedTestConstants.MSG_JSON_PROPERTY),
                            getCommandClassFromType(commandType));
        }
        catch (final JMSException jmse)
        {
            throw new DistributedTestException("Unable to convert JMS message " + jmsMessage + " to command object",
                            jmse);
        }
        return command;
    }

    static Class<? extends Command> getCommandClassFromType(final CommandType type)
    {
        switch (type)
        {
            case CREATE_CONNECTION:
                return CreateConnectionCommand.class;
            case CREATE_SESSION:
                return CreateSessionCommand.class;
            case CREATE_PRODUCER:
                return CreateProducerCommand.class;
            case CREATE_CONSUMER:
                return CreateConsumerCommand.class;
            case CREATE_RESPONDER:
                return CreateResponderCommand.class;
            case NO_OP:
                return NoOpCommand.class;
            case REGISTER_CLIENT:
                return RegisterClientCommand.class;
            case STOP_CLIENT:
                return StopClientCommand.class;
            case RESPONSE:
                return Response.class;
            case START_TEST:
                return StartTestCommand.class;
            case TEAR_DOWN_TEST:
                return TearDownTestCommand.class;
            case PARTICIPANT_RESULT:
                return ParticipantResult.class;
            case CONSUMER_PARTICIPANT_RESULT:
                return ConsumerParticipantResult.class;
            case PRODUCER_PARTICIPANT_RESULT:
                return ProducerParticipantResult.class;
            case CREATE_MESSAGE_PROVIDER:
                return CreateMessageProviderCommand.class;
            default:
                throw new DistributedTestException("No class defined for type: " + type);
        }
    }
}
