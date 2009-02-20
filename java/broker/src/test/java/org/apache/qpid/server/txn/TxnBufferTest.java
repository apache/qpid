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

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.transactionlog.TransactionLog;

import java.util.LinkedList;
import java.util.NoSuchElementException;

public class TxnBufferTest extends TestCase
{
    private final LinkedList<MockOp> ops = new LinkedList<MockOp>();

    public void testCommit() throws AMQException
    {
        MockStore store = new MockStore();

        TxnBuffer buffer = new TxnBuffer();
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        //check relative ordering
        MockOp op = new MockOp().expectPrepare().expectPrepare().expectCommit().expectCommit();
        buffer.enlist(op);
        buffer.enlist(op);
        buffer.enlist(new MockOp().expectPrepare().expectCommit());

        buffer.commit(null);

        validateOps();
        store.validate();
    }

    public void testRollback() throws AMQException
    {
        MockStore store = new MockStore();

        TxnBuffer buffer = new TxnBuffer();
        buffer.enlist(new MockOp().expectRollback());
        buffer.enlist(new MockOp().expectRollback());
        buffer.enlist(new MockOp().expectRollback());

        buffer.rollback(null);

        validateOps();
        store.validate();
    }

    public void testCommitWithFailureDuringPrepare() throws AMQException
    {
        MockStore store = new MockStore();
        store.beginTran(null);

        TxnBuffer buffer = new TxnBuffer();
        buffer.enlist(new StoreMessageOperation(store));
        buffer.enlist(new MockOp().expectPrepare().expectUndoPrepare());
        buffer.enlist(new TxnTester(store));
        buffer.enlist(new MockOp().expectPrepare().expectUndoPrepare());
        buffer.enlist(new FailedPrepare());
        buffer.enlist(new MockOp());

        try
        {
            buffer.commit(null);
        }
        catch (NoSuchElementException e)
        {
        
        }
    
        validateOps();
        store.validate();
    }

    public void testCommitWithPersistance() throws AMQException
    {
        MockStore store = new MockStore();
        store.beginTran(null);
        store.expectCommit();

        TxnBuffer buffer = new TxnBuffer();
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new StoreMessageOperation(store));
        buffer.enlist(new TxnTester(store));

        buffer.commit(null);
        validateOps();
        store.validate();
    }

    private void validateOps()
    {
        for (MockOp op : ops)
        {
            op.validate();
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TxnBufferTest.class);
    }

    class MockOp implements TxnOp
    {
        final Object PREPARE = "PREPARE";
        final Object COMMIT = "COMMIT";
        final Object UNDO_PREPARE = "UNDO_PREPARE";
        final Object ROLLBACK = "ROLLBACK";

        private final LinkedList expected = new LinkedList();

        MockOp()
        {
            ops.add(this);
        }

        public void prepare(StoreContext context)
        {
            assertEquals(expected.removeLast(), PREPARE);
        }

        public void commit(StoreContext context)
        {
            assertEquals(expected.removeLast(), COMMIT);
        }

        public void undoPrepare()
        {
            assertEquals(expected.removeLast(), UNDO_PREPARE);
        }

        public void rollback(StoreContext context)
        {
            assertEquals(expected.removeLast(), ROLLBACK);
        }

        private MockOp expect(Object optype)
        {
            expected.addFirst(optype);
            return this;
        }

        MockOp expectPrepare()
        {
            return expect(PREPARE);
        }

        MockOp expectCommit()
        {
            return expect(COMMIT);
        }

        MockOp expectUndoPrepare()
        {
            return expect(UNDO_PREPARE);
        }

        MockOp expectRollback()
        {
            return expect(ROLLBACK);
        }

        void validate()
        {
            assertEquals("Expected ops were not all invoked", new LinkedList(), expected);
        }

        void clear()
        {
            expected.clear();
        }
    }

    class MockStore extends TestableMemoryMessageStore
    {
        final Object BEGIN = "BEGIN";
        final Object ABORT = "ABORT";
        final Object COMMIT = "COMMIT";

        private final LinkedList expected = new LinkedList();
        private boolean inTran;

        public void beginTran(StoreContext context) throws AMQException
        {
            inTran = true;
        }

        public void commitTran(StoreContext context) throws AMQException
        {
            assertEquals(expected.removeLast(), COMMIT);
            inTran = false;
        }

        public void abortTran(StoreContext context) throws AMQException
        {
            assertEquals(expected.removeLast(), ABORT);
            inTran = false;
        }

        public boolean inTran(StoreContext context)
        {
            return inTran;
        }

        private MockStore expect(Object optype)
        {
            expected.addFirst(optype);
            return this;
        }

        MockStore expectBegin()
        {
            return expect(BEGIN);
        }

        MockStore expectCommit()
        {
            return expect(COMMIT);
        }

        MockStore expectAbort()
        {
            return expect(ABORT);
        }

        void clear()
        {
            expected.clear();
        }

        void validate()
        {
            assertEquals("Expected ops were not all invoked", new LinkedList(), expected);
        }
    }

    class NullOp implements TxnOp
    {
        public void prepare(StoreContext context) throws AMQException
        {
        }
        public void commit(StoreContext context)
        {
        }
        public void undoPrepare()
        {
        }
        public void rollback(StoreContext context)
        {
        }
    }

    class FailedPrepare extends NullOp
    {
        public void prepare() throws AMQException
        {
            throw new AMQException(null, "Fail!", null);
        }
    }

    class TxnTester extends NullOp
    {
        private final TransactionLog store;

        private final StoreContext context = new StoreContext();

        TxnTester(TransactionLog transactionLog)
        {
            this.store = transactionLog;
        }

        public void prepare() throws AMQException
        {
            assertTrue("Expected prepare to be performed under txn", store.inTran(context));
        }

        public void commit()
        {
            assertTrue("Expected commit not to be performed under txn", !store.inTran(context));
        }
    }

}
