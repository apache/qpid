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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.transactionlog.TransactionLog;

import java.util.concurrent.atomic.AtomicLong;

public class MessageFactory
{
    private AtomicLong _messageId;
    private static MessageFactory INSTANCE;

    private enum State
    {
        RECOVER,
        OPEN
    }

    private State _state = State.RECOVER;

    private MessageFactory()
    {
        _messageId = new AtomicLong(0L);
    }

    public void start()
    {
        _state = State.OPEN;
    }

    /**
     * Only used by test as test suite is run in a single VM we need to beable to re-enable recovery mode.
     */    
    protected void enableRecover()
    {
        _state = State.RECOVER;
    }

    
    /**
     * Normal message creation path
     * @param transactionLog
     * @param persistent
     * @return
     */
    public AMQMessage createMessage(TransactionLog transactionLog, boolean persistent)
    {
        if (_state != State.OPEN)
        {
            _state = State.OPEN;
        }

        return createNextMessage(_messageId.incrementAndGet(), transactionLog, persistent);
    }

    /**
     * Used for message recovery only and so only creates persistent messages.
     * @param messageId the id that this message must have
     * @param transactionLog
     * @return
     */
    public AMQMessage createMessage(Long messageId, TransactionLog transactionLog)
    {
        if (_state != State.RECOVER)
        {
            throw new RuntimeException("Unable to create message by ID when not recovering");
        }

        long currentID = _messageId.get();
        if (messageId <= currentID)
        {
            throw new RuntimeException("Message IDs can only increase current id is:"
                                       + currentID + ". Requested:" + messageId);
        }
        else
        {
            _messageId.set(messageId);
        }

        return createNextMessage(messageId, transactionLog, true);
    }

    private AMQMessage createNextMessage(Long messageId, TransactionLog transactionLog, boolean persistent)
    {
        if (persistent)
        {
            return new PersistentAMQMessage(messageId, transactionLog);
        }
        else
        {
            return new TransientAMQMessage(messageId);
        }
    }

    public static MessageFactory getInstance()
    {
        if (INSTANCE == null)
        {
            INSTANCE = new MessageFactory();
        }

        return INSTANCE;
    }
}
