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
package org.apache.qpid.server.txn;

import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.messageStore.MessageStore;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 25-Apr-2007
 * Time: 14:12:17
 */
public interface TransactionRecord
{
    /**
        * Commit this record.
        *
        * @param store the store to be used during commit
        * @param xid   the xid of the tx branch
        * @throws org.apache.qpid.server.exception.InternalErrorException      in case of internal problem
        * @throws org.apache.qpid.server.exception.QueueDoesntExistException   the queue does not exist
        * @throws org.apache.qpid.server.exception.InvalidXidException         the xid is invalid
        * @throws org.apache.qpid.server.exception.UnknownXidException         the xid is unknonw
        * @throws org.apache.qpid.server.exception.MessageDoesntExistException the message does not exist
        */
       public abstract void commit(MessageStore store, Xid xid)
               throws
               InternalErrorException,
               QueueDoesntExistException,
               InvalidXidException,
               UnknownXidException,
               MessageDoesntExistException;

       /**
        * rollback this record
        *
        * @param store the store to be used
        * @throws InternalErrorException In case of internal error
        */
       public abstract void rollback(MessageStore store)
               throws
               InternalErrorException;

       /**
        * Prepare this record
        *
        * @param store the store to be used
        * @throws InternalErrorException In case of internal error
        */
       public abstract void prepare(MessageStore store)
               throws
               InternalErrorException;

}
