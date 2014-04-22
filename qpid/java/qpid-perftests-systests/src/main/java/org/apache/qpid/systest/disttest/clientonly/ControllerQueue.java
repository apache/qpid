/*
 *
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
package org.apache.qpid.systest.disttest.clientonly;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.Context;

import org.junit.Assert;

import org.apache.qpid.disttest.DistributedTestConstants;
import org.apache.qpid.disttest.jms.JmsMessageAdaptor;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;

/**
 * Helper for unit tests to simplify access to the Controller Queue.
 *
 * Implicitly creates the queue, so you must create a {@link ControllerQueue} object before
 * trying to use the underlying queue.
 */
public class ControllerQueue
{
    private MessageConsumer _controllerQueueMessageConsumer;
    private Session _controllerQueueSession;

    /**
     * Implicitly creates the queue, so you must create a {@link ControllerQueue} object before
     * trying to use the underlying queue.
     *
     * @param context used for looking up the controller queue {@link Destination}
     */
    public ControllerQueue(Connection connection, Context context) throws Exception
    {
        _controllerQueueSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination controllerQueue = (Destination) context.lookup(DistributedTestConstants.CONTROLLER_QUEUE_JNDI_NAME);
        _controllerQueueMessageConsumer = _controllerQueueSession.createConsumer(controllerQueue);
    }

    public <T extends Command> T getNext(long timeout) throws JMSException
    {
        final Message message = _controllerQueueMessageConsumer.receive(timeout);
        if(message == null)
        {
            return null;
        }

        return (T) JmsMessageAdaptor.messageToCommand(message);
    }

    public void addNextResponse(Map<CommandType, Command> responses) throws JMSException
    {
        Command nextResponse = getNext();
        responses.put(nextResponse.getType(), nextResponse);
    }

    @SuppressWarnings("unchecked")
    public <T extends Command> T getNext() throws JMSException
    {
        return (T)getNext(true);
    }

    public <T extends Command> T getNext(boolean assertMessageExists) throws JMSException
    {
        final Message message = _controllerQueueMessageConsumer.receive(2000);
        if(assertMessageExists)
        {
            Assert.assertNotNull("No message received from control queue", message);
        }

        if(message == null)
        {
            return null;
        }

        T command = (T) JmsMessageAdaptor.messageToCommand(message);

        return command;
    }

    public void close() throws Exception
    {
        _controllerQueueMessageConsumer.close();
        _controllerQueueSession.close();
    }
}
