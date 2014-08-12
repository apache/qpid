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
package org.apache.qpid.systest.disttest.controlleronly;

import static org.apache.qpid.systest.disttest.SystemTestConstants.COMMAND_RESPONSE_TIMEOUT;
import static org.apache.qpid.systest.disttest.SystemTestConstants.REGISTRATION_TIMEOUT;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.qpid.systest.disttest.ConfigFileTestHelper;
import org.apache.qpid.disttest.controller.Controller;
import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.jms.JmsMessageAdaptor;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedControllerTest extends DistributedTestSystemTestBase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedControllerTest.class);

    private static final String CLIENT1 = "client1";
    private Controller _controller = null;
    private Session _session = null;
    private Connection _connection = null;
    private Destination _controllerQueue = null;
    private TemporaryQueue _clientQueue = null;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _controllerQueue = (Destination) _context.lookup("controllerqueue");

        final ConnectionFactory connectionFactory = (ConnectionFactory) _context.lookup("connectionfactory");
        _connection = connectionFactory.createConnection();
        _connection.start();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _clientQueue = _session.createTemporaryQueue();

        _controller = new Controller(new ControllerJmsDelegate(_context), REGISTRATION_TIMEOUT, COMMAND_RESPONSE_TIMEOUT);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            if (_connection != null)
            {
                _connection.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testControllerSendsOneCommandToSingleClient() throws Exception
    {
        Config config = ConfigFileTestHelper.getConfigFromResource(getClass(), "distributedControllerTest.json");
        _controller.setConfig(config);

        sendRegistration(CLIENT1);
        _controller.awaitClientRegistrations();

        final ArrayBlockingQueue<Command> commandList = new ArrayBlockingQueue<Command>(4);
        final MessageConsumer clientConsumer = _session.createConsumer(_clientQueue);
        final AtomicReference<Exception> listenerException = new AtomicReference<Exception>();
        final MessageProducer producer = _session.createProducer(_controllerQueue);
        clientConsumer.setMessageListener(new MessageListener()
        {
            @Override
            public void onMessage(Message message)
            {
                try
                {
                    Command command = JmsMessageAdaptor.messageToCommand(message);
                    LOGGER.debug("Test client received " + command);
                    commandList.add(command);
                    producer.send(JmsMessageAdaptor.commandToMessage(_session, new Response(CLIENT1, command.getType())));
                }
                catch(Exception e)
                {
                    listenerException.set(e);
                }
            }
        });

        _controller.runAllTests();
        assertCommandType(CommandType.CREATE_CONNECTION, commandList);
        assertCommandType(CommandType.START_TEST, commandList);
        assertCommandType(CommandType.TEAR_DOWN_TEST, commandList);

        _controller.stopAllRegisteredClients();
        assertCommandType(CommandType.STOP_CLIENT, commandList);
        assertNull("Unexpected exception occured", listenerException.get());
        Command command = commandList.poll(1l, TimeUnit.SECONDS);
        assertNull("Unexpected command is received", command);
    }

    private void assertCommandType(CommandType expectedType, BlockingQueue<Command> commandList) throws InterruptedException
    {
        Command command = commandList.poll(1l, TimeUnit.SECONDS);
        assertNotNull("Command of type " + expectedType + " is not received", command);
        assertEquals("Unexpected command type", expectedType, command.getType());
    }

    private void sendRegistration(final String clientId) throws JMSException
    {
        final MessageProducer registrationProducer = _session.createProducer(_controllerQueue);

        final Command command = new RegisterClientCommand(clientId, _clientQueue.getQueueName());
        final Message registrationMessage = JmsMessageAdaptor.commandToMessage(_session, command);
        registrationProducer.send(registrationMessage);
        LOGGER.debug("sent registrationMessage: " + registrationMessage);
    }

}
