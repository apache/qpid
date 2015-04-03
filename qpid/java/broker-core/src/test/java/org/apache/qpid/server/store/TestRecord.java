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
package org.apache.qpid.server.store;

import java.util.UUID;

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.store.Transaction.EnqueueRecord;

public class TestRecord implements EnqueueRecord, Transaction.DequeueRecord, MessageEnqueueRecord
{
    private TransactionLogResource _queue;
    private EnqueueableMessage _message;

    public TestRecord(TransactionLogResource queue, EnqueueableMessage message)
    {
        super();
        _queue = queue;
        _message = message;
    }

    @Override
    public TransactionLogResource getResource()
    {
        return _queue;
    }

    @Override
    public EnqueueableMessage getMessage()
    {
        return _message;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_message == null) ? 0 : new Long(_message.getMessageNumber()).hashCode());
        result = prime * result + ((_queue == null) ? 0 : _queue.getId().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (!(obj instanceof EnqueueRecord))
        {
            return false;
        }
        EnqueueRecord other = (EnqueueRecord) obj;
        if (_message == null && other.getMessage() != null)
        {
            return false;
        }
        if (_queue == null && other.getResource() != null)
        {
            return false;
        }
        if (_message.getMessageNumber() != other.getMessage().getMessageNumber())
        {
            return false;
        }
        return _queue.getId().equals(other.getResource().getId());
    }

    @Override
    public MessageEnqueueRecord getEnqueueRecord()
    {
        return this;
    }

    @Override
    public UUID getQueueId()
    {
        return _queue.getId();
    }

    @Override
    public long getMessageNumber()
    {
        return _message.getMessageNumber();
    }
}
