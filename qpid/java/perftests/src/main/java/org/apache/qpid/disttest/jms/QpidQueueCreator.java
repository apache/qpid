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
 */
package org.apache.qpid.disttest.jms;

import java.util.List;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.controller.config.QueueConfig;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidQueueCreator implements QueueCreator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QpidQueueCreator.class);
    private static final FieldTable EMPTY_QUEUE_BIND_ARGUMENTS = new FieldTable();
    private static int _drainPollTimeout = Integer.getInteger(QUEUE_CREATOR_DRAIN_POLL_TIMEOUT, 500);

    @Override
    public void createQueues(Connection connection, Session session, List<QueueConfig> configs)
    {
        AMQSession<?, ?> amqSession = (AMQSession<?, ?>)session;
        for (QueueConfig queueConfig : configs)
        {
            createQueue(amqSession, queueConfig);
        }
    }

    @Override
    public void deleteQueues(Connection connection, Session session, List<QueueConfig> configs)
    {
        AMQSession<?, ?> amqSession = (AMQSession<?, ?>)session;
        for (QueueConfig queueConfig : configs)
        {
            AMQDestination destination = createAMQDestination(amqSession, queueConfig);

            // drainQueue method is added because deletion of queue with a lot
            // of messages takes time and might cause the timeout exception
            drainQueue(connection, destination);

            deleteQueue(amqSession, destination.getAMQQueueName());
        }
    }

    private AMQDestination createAMQDestination(AMQSession<?, ?> amqSession, QueueConfig queueConfig)
    {
        try
        {
            return (AMQDestination) amqSession.createQueue(queueConfig.getName());
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to create amq destionation object:" + queueConfig, e);
        }
    }

    private long getQueueDepth(AMQSession<?, ?> amqSession, AMQDestination destination)
    {
        try
        {
            long queueDepth = amqSession.getQueueDepth(destination);
            return queueDepth;
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to query queue depth:" + destination, e);
        }
    }

    private void drainQueue(Connection connection, AMQDestination destination)
    {
        Session noAckSession = null;
        try
        {
            LOGGER.debug("About to drain the queue {}", destination.getQueueName());
            noAckSession = connection.createSession(false, org.apache.qpid.jms.Session.NO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = noAckSession.createConsumer(destination);

            long currentQueueDepth = getQueueDepth((AMQSession<?,?>)noAckSession, destination);
            int counter = 0;
            while (currentQueueDepth > 0)
            {
                LOGGER.info("Queue {} has {} message(s)", destination.getQueueName(), currentQueueDepth);

                while(messageConsumer.receive(_drainPollTimeout) != null)
                {
                    counter++;
                }

                currentQueueDepth = getQueueDepth((AMQSession<?,?>)noAckSession, destination);
            }
            LOGGER.info("Drained {} message(s) from queue {} ", counter, destination.getQueueName());
            messageConsumer.close();
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to drain queue:" + destination, e);
        }
        finally
        {
            if (noAckSession != null)
            {
                try
                {
                    noAckSession.close();
                }
                catch (JMSException e)
                {
                    throw new DistributedTestException("Failed to close n/a session:" + noAckSession, e);
                }
            }
        }
    }

    private void createQueue(AMQSession<?, ?> session, QueueConfig queueConfig)
    {
        try
        {
            AMQDestination destination = (AMQDestination) session.createQueue(queueConfig.getName());
            boolean autoDelete = false;
            boolean exclusive = false;
            session.createQueue(destination.getAMQQueueName(), autoDelete,
                    queueConfig.isDurable(), exclusive, queueConfig.getAttributes());
            session.bindQueue(destination.getAMQQueueName(), destination.getRoutingKey(),
                    EMPTY_QUEUE_BIND_ARGUMENTS, destination.getExchangeName(),
                    destination, autoDelete);

            LOGGER.debug("Created queue {}", queueConfig);
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to create queue:" + queueConfig, e);
        }
    }

    private void deleteQueue(AMQSession<?, ?> session, AMQShortString queueName)
    {
        try
        {
            // The Qpid AMQSession API currently makes the #deleteQueue method protected and the
            // raw protocol method public.  This should be changed then we should switch the below to
            // use #deleteQueue.
            session.sendQueueDelete(queueName);
            LOGGER.debug("Deleted queue {}", queueName);
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Failed to delete queue:" + queueName, e);
        }
    }
}
