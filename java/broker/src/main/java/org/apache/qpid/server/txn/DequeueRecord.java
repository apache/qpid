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
import org.apache.qpid.server.exception.*;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 25-Apr-2007
 * Time: 17:13:07
 */
public class DequeueRecord implements TransactionRecord
{


    public void commit(MessageStore store, Xid xid)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException,
            MessageDoesntExistException
    {
        // do nothing
    }

    public void rollback(MessageStore store)
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void prepare(MessageStore store)
            throws
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}

