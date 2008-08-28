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
import org.apache.qpid.server.store.StoreContext;

/**
 * This provides the abstraction of an individual operation within a
 * transaction. It is used by the TxnBuffer class.
 */
public interface TxnOp
{
    /**
     * Do the part of the operation that updates persistent state
     */
    public void prepare(StoreContext context) throws AMQException;
    /**
     * Complete the operation started by prepare. Can now update in
     * memory state or make netork transfers.
     */
    public void commit(StoreContext context) throws AMQException;
    /**
     * This is not the same as rollback. Unfortunately the use of an
     * in memory reference count as a locking mechanism and a test for
     * whether a message should be deleted means that as things are,
     * handling an acknowledgement unavoidably alters both memory and
     * persistent state on prepare. This is needed to 'compensate' or
     * undo the in-memory change if the peristent update of later ops
     * fails.
     */
    public void undoPrepare();
    /**
     * Rolls back the operation.
     */
    public void rollback(StoreContext context) throws AMQException;
}
