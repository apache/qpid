/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.server.txn;

import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.messageStore.StorableMessage;
import org.apache.qpid.server.messageStore.StorableQueue;
import org.apache.qpid.server.exception.*;
import org.apache.log4j.Logger;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 03-May-2007
 * Time: 14:00:04
 */
public class MemoryEnqueueRecord implements TransactionRecord
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(MemoryDequeueRecord.class);

    // the queue
    StorableQueue _queue;
    // the message
    StorableMessage _message;

    //========================================================================
    // Constructor
    //========================================================================
    public MemoryEnqueueRecord(StorableMessage m, StorableQueue queue)
    {
        _queue = queue;
        _message = m;
    }
    //========================================================================
    // Interface TransactionRecord
    //========================================================================

    public void commit(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException,
            MessageDoesntExistException
    {
        store.enqueue(null, _message, _queue);
    }

    public void rollback(MessageStore store)
            throws
            InternalErrorException
    {
        // do nothing
    }

    public void prepare(MessageStore store)
            throws
            InternalErrorException
    {
        // do nothing 
    }
}
