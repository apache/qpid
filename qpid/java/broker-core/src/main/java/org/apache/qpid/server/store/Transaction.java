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

import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.util.FutureResult;

public interface Transaction
{

    /**
     * Places a message onto a specified queue, in a given transactional context.
     *
     *
     *  @param queue     The queue to place the message on.
     * @param message
     */
    MessageEnqueueRecord enqueueMessage(TransactionLogResource queue, EnqueueableMessage message);

    void dequeueMessage(MessageEnqueueRecord enqueueRecord);

    /**
     * Commits all operations performed within a given transactional context.
     *
     */
    void commitTran();

    /**
     * Commits all operations performed within a given transactional context.
     *
     */
    FutureResult commitTranAsync();

    /**
     * Abandons all operations performed within a given transactional context.
     *
     */
    void abortTran();


    interface EnqueueRecord
    {
        TransactionLogResource getResource();
        EnqueueableMessage getMessage();
    }

    interface DequeueRecord
    {
        MessageEnqueueRecord getEnqueueRecord();
    }

    void removeXid(StoredXidRecord record);


    StoredXidRecord recordXid(long format, byte[] globalId, byte[] branchId, EnqueueRecord[] enqueues,
                   DequeueRecord[] dequeues);

    interface StoredXidRecord
    {
        long getFormat();
        byte[] getGlobalId();
        byte[] getBranchId();

    }
}
