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

package org.apache.qpid.server.store.berkeleydb.tuple;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.berkeleydb.entry.PreparedTransaction;

public class PreparedTransactionBinding extends TupleBinding<PreparedTransaction>
{
    @Override
    public PreparedTransaction entryToObject(TupleInput input)
    {
        MessageStore.Transaction.Record[] enqueues = readRecords(input);

        MessageStore.Transaction.Record[] dequeues = readRecords(input);

        return new PreparedTransaction(enqueues, dequeues);
    }

    private MessageStore.Transaction.Record[] readRecords(TupleInput input)
    {
        MessageStore.Transaction.Record[] records = new MessageStore.Transaction.Record[input.readInt()];
        for(int i = 0; i < records.length; i++)
        {
            records[i] = new RecordImpl(input.readString(), input.readLong());
        }
        return records;
    }

    @Override
    public void objectToEntry(PreparedTransaction preparedTransaction, TupleOutput output)
    {
        writeRecords(preparedTransaction.getEnqueues(), output);
        writeRecords(preparedTransaction.getDequeues(), output);

    }

    private void writeRecords(MessageStore.Transaction.Record[] records, TupleOutput output)
    {
        if(records == null)
        {
            output.writeInt(0);
        }
        else
        {
            output.writeInt(records.length);
            for(MessageStore.Transaction.Record record : records)
            {
                output.writeString(record.getQueue().getResourceName());
                output.writeLong(record.getMessage().getMessageNumber());
            }
        }
    }

    private static class RecordImpl implements MessageStore.Transaction.Record, TransactionLogResource, EnqueableMessage
    {

        private final String _queueName;
        private long _messageNumber;

        public RecordImpl(String queueName, long messageNumber)
        {
            _queueName = queueName;
            _messageNumber = messageNumber;
        }

        public TransactionLogResource getQueue()
        {
            return this;
        }

        public EnqueableMessage getMessage()
        {
            return this;
        }

        public long getMessageNumber()
        {
            return _messageNumber;
        }

        public boolean isPersistent()
        {
            return true;
        }

        public StoredMessage<?> getStoredMessage()
        {
            throw new UnsupportedOperationException();
        }

        public String getResourceName()
        {
            return _queueName;
        }
    }
}
