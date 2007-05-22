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

import org.apache.log4j.Logger;
import org.apache.qpid.server.messageStore.StorableQueue;
import org.apache.qpid.server.messageStore.StorableMessage;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.exception.InternalErrorException;
import org.apache.qpid.server.exception.UnknownXidException;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 16-May-2007
 * Time: 15:15:18
 */
public abstract class JDBCAbstractRecord implements TransactionRecord
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(JDBCEnqueueRecord.class);
    // The record types
    static public final int TYPE_DEQUEUE = 1;
    static public final int TYPE_ENQUEUE = 2;

    // the queue
    StorableQueue _queue;
    // the message
    StorableMessage _message;

    //========================================================================
    // Constructor
    //========================================================================
    public JDBCAbstractRecord(StorableMessage m, StorableQueue queue)
    {
        _queue = queue;
        _message = m;
    }

    public abstract int getType();
    public long getMessageID()
    {
        return _message.getMessageId();
    }

    public int getQueueID()
    {
        return _queue.getQueueID();
    }

     public void rollback(MessageStore store)
            throws
            InternalErrorException
     {

    }

    public void prepare(MessageStore store)
            throws
            InternalErrorException
    {
    }


      public abstract void rollback(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            UnknownXidException;

    public abstract void prepare(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            UnknownXidException;
}
