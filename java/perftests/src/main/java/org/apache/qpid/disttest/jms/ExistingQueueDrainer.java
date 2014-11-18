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

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.controller.config.QueueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import java.util.List;

public class ExistingQueueDrainer implements QueueCreator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExistingQueueDrainer.class);
    private static int _drainPollTimeout = Integer.getInteger(QUEUE_CREATOR_DRAIN_POLL_TIMEOUT, 500);

    @Override
    public void createQueues(Connection connection, Session session, List<QueueConfig> configs)
    {
    }

    @Override
    public void deleteQueues(Connection connection, Session session, List<QueueConfig> configs)
    {
        for (QueueConfig queueConfig : configs)
        {
            drainQueue(connection, queueConfig.getName());
        }
    }

    private void drainQueue(Connection connection, String queueName)
    {
        try
        {
            int counter = 0;
            while (queueContainsMessages(connection, queueName))
            {
                if (counter == 0)
                {
                    LOGGER.debug("Draining queue {}", queueName);
                }
                counter += drain(connection, queueName);
            }
            if (counter > 0)
            {
                LOGGER.info("Drained {} message(s) from queue {} ", counter, queueName);
            }
        }
        catch (JMSException e)
        {
            throw new DistributedTestException("Failed to drain queue " + queueName, e);
        }
    }

    private int drain(Connection connection, String queueName) throws JMSException
    {
        int counter = 0;
        Session session = null;
        try
        {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer messageConsumer = session.createConsumer(session.createQueue(queueName));
            try
            {
                while (messageConsumer.receive(_drainPollTimeout) != null)
                {
                    counter++;
                }
            }
            finally
            {
                messageConsumer.close();
            }
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
        }
        return counter;
    }

    private boolean queueContainsMessages(Connection connection, String queueName) throws JMSException
    {
        Session session = null;
        try
        {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueBrowser browser = null;
            try
            {
                browser = session.createBrowser(session.createQueue(queueName));
                return browser.getEnumeration().hasMoreElements();
            }
            finally
            {
                if (browser != null)
                {
                    browser.close();
                }
            }
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
        }
    }
}
