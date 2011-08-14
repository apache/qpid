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
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.QueueSession;

import javax.jms.JMSException;
import javax.jms.Queue;

public class QueueSessionImpl extends SessionImpl implements QueueSession
{
    protected QueueSessionImpl(final ConnectionImpl connection, final AcknowledgeMode acknowledgeMode)
    {
        super(connection, acknowledgeMode);
    }

    public QueueReceiverImpl createReceiver(final Queue queue) throws JMSException
    {
        return createReceiver(queue, null);
    }

    public QueueReceiverImpl createReceiver(final Queue queue, final String selector) throws JMSException
    {
        // TODO - assert queue is a queueimpl and throw relevant JMS Exception
        final QueueReceiverImpl messageConsumer;
        synchronized(getClientSession().getEndpoint().getLock())
        {
            messageConsumer = new QueueReceiverImpl((QueueImpl)queue, this, selector, false);
            addConsumer(messageConsumer);
        }
        return messageConsumer;

    }

    public QueueSenderImpl createSender(final Queue queue) throws JMSException
    {
        return null;  //TODO
    }
}
