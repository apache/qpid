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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.NamingException;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.Visitor;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

    private final ClientJmsDelegate _clientJmsDelegate;

    private final CountDownLatch _latch = new CountDownLatch(1);
    private Visitor _visitor;
    private final AtomicReference<ClientState> _state;
    private ParticipantExecutorRegistry _participantRegistry = new ParticipantExecutorRegistry();

    public Client(final ClientJmsDelegate delegate) throws NamingException
    {
        _clientJmsDelegate = delegate;
        _state = new AtomicReference<ClientState>(ClientState.CREATED);
        _visitor = new ClientCommandVisitor(this, _clientJmsDelegate);
    }

    /**
     * Register with the controller
     */
    public void start()
    {
        _clientJmsDelegate.setInstructionListener(this);
        _clientJmsDelegate.sendRegistrationMessage();
        _state.set(ClientState.READY);
    }

    public void stop()
    {
        _clientJmsDelegate.sendResponseMessage(new Response(_clientJmsDelegate.getClientName(), CommandType.STOP_CLIENT, null));
        _state.set(ClientState.STOPPED);
        _latch.countDown();
    }

    public void addParticipantExecutor(final ParticipantExecutor participant)
    {
        _participantRegistry.add(participant);
    }

    public void waitUntilStopped()
    {
        waitUntilStopped(0);
    }

    public void waitUntilStopped(final long timeout)
    {
        try
        {
            if (timeout == 0)
            {
                _latch.await();
            }
            else
            {
                _latch.await(timeout, TimeUnit.MILLISECONDS);
            }
        }
        catch (final InterruptedException ie)
        {
            Thread.currentThread().interrupt();
        }

        _clientJmsDelegate.destroy();
    }

    public void processInstruction(final Command command)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Client " + getClientName() + " received command: " + command);
        }
        String responseMessage = null;
        try
        {
            command.accept(_visitor);
        }
        catch (final Exception e)
        {
            LOGGER.error("Error processing instruction", e);
            responseMessage = e.getMessage();
        }
        finally
        {
            if (_state.get() != ClientState.STOPPED)
            {
                _clientJmsDelegate.sendResponseMessage(new Response(_clientJmsDelegate.getClientName(), command.getType(), responseMessage));
            }
        }
    }

    public ClientState getState()
    {
        return _state.get();
    }

    public String getClientName()
    {
        return _clientJmsDelegate.getClientName();
    }

    public void setClientCommandVisitor(final ClientCommandVisitor visitor)
    {
        _visitor = visitor;
    }

    public void startTest()
    {
        if (_state.compareAndSet(ClientState.READY, ClientState.RUNNING_TEST))
        {
            try
            {
                _clientJmsDelegate.startConnections();
                for (final ParticipantExecutor executor : _participantRegistry.executors())
                {
                    executor.start(this);
                }
            }
            catch (final Exception e)
            {
                try
                {
                    tearDownTest();
                }
                catch (final Exception e2)
                {
                    // ignore
                }
                throw new DistributedTestException("Error starting test: " + _clientJmsDelegate.getClientName(), e);
            }
        }
        else
        {
            throw new DistributedTestException("Client '" + _clientJmsDelegate.getClientName()
                            + "' is not in READY state:" + _state.get());
        }
    }

    public void tearDownTest()
    {
        if (_state.compareAndSet(ClientState.RUNNING_TEST, ClientState.READY))
        {
            LOGGER.debug("Tearing down test on client: " + _clientJmsDelegate.getClientName());

            _clientJmsDelegate.tearDownTest();
        }
        else
        {
            throw new DistributedTestException("Client '" + _clientJmsDelegate.getClientName() + "' is not in RUNNING_TEST state! Ignoring tearDownTest");
        }


        _participantRegistry.clear();
    }

    public void sendResults(ParticipantResult testResult)
    {
        _clientJmsDelegate.sendResponseMessage(testResult);
        LOGGER.debug("Sent test results " + testResult);
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("clientJmsDelegate", _clientJmsDelegate).toString();
    }

    void setParticipantRegistry(ParticipantExecutorRegistry participantRegistry)
    {
        _participantRegistry = participantRegistry;
    }

}
