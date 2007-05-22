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
import org.apache.qpid.server.messageStore.JDBCStore;
import org.apache.qpid.server.exception.*;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 16-May-2007
 * Time: 14:50:20
 */
public class JDBCEnqueueRecord extends  JDBCAbstractRecord
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(JDBCEnqueueRecord.class);

    //========================================================================
    // Constructor
    //========================================================================
    public JDBCEnqueueRecord(StorableMessage m, StorableQueue queue)
    {
        super(m, queue);
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
        store.enqueue(xid, _message, _queue);
    }

    public void rollback(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            UnknownXidException
    {
        if (!_message.isEnqueued())
        {
            // try to delete the message
            try
            {
                store.destroy(_message);
            } catch (Exception e)
            {
                throw new InternalErrorException("Problem when destoying message ", e);
            }
        }
    }

    public void prepare(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            UnknownXidException
    {
        try
        {
            if (!_message.isEnqueued() && !_message.isStaged())
            {
                store.stage(_message);
                store.appendContent(_message, _message.getData(), 0, _message.getData().length);
            }
        }
        catch (Exception e)
        {
            throw new InternalErrorException("Problem when persisting message ", e);
        }
    }

    public int getType()
    {
        return TYPE_ENQUEUE;
    }
}
