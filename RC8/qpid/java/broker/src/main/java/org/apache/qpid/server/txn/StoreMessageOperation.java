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
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

/**
 * A transactional operation to store messages in an underlying persistent store. When this operation
 * commits it will do everything to ensure that all messages are safely committed to persistent
 * storage.
 */
public class StoreMessageOperation implements TxnOp
{
    private final MessageStore _messsageStore;

    public StoreMessageOperation(MessageStore messageStore)
    {
        _messsageStore = messageStore;
    }

    public void prepare(StoreContext context) throws AMQException
    {
    }

    public void undoPrepare()
    {
    }

    public void commit(StoreContext context) throws AMQException
    {
        _messsageStore.commitTran(context);
    }

    public void rollback(StoreContext context) throws AMQException
    {
        _messsageStore.abortTran(context);
    }
}
