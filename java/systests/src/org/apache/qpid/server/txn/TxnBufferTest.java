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

import junit.framework.JUnit4TestAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestableMemoryMessageStore;

import java.util.LinkedList;

public class TxnBufferTest
{
    private final LinkedList<MockOp> ops = new LinkedList<MockOp>();  

    @Before
    public void setup() throws Exception
    {
    }

    @Test
    public void commit() throws AMQException
    {
        MockStore store = new MockStore();

        TxnBuffer buffer = new TxnBuffer(store);
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        //check relative ordering
        MockOp op = new MockOp().expectPrepare().expectPrepare().expectCommit().expectCommit();
        buffer.enlist(op);
        buffer.enlist(op);
        buffer.enlist(new MockOp().expectPrepare().expectCommit());

        buffer.commit();

        validateOps();
        store.validate();
    }

    @Test
    public void rollback() throws AMQException
    {
        MockStore store = new MockStore();

        TxnBuffer buffer = new TxnBuffer(store);
        buffer.enlist(new MockOp().expectRollback());
        buffer.enlist(new MockOp().expectRollback());
        buffer.enlist(new MockOp().expectRollback());

        buffer.rollback();

        validateOps();
        store.validate();
    }

    @Test
    public void commitWithFailureDuringPrepare() throws AMQException
    {
        MockStore store = new MockStore();
        store.expectBegin().expectAbort();

        TxnBuffer buffer = new TxnBuffer(store);
        buffer.containsPersistentChanges();
        buffer.enlist(new MockOp().expectPrepare().expectUndoPrepare());
        buffer.enlist(new TxnTester(store));
        buffer.enlist(new MockOp().expectPrepare().expectUndoPrepare());
        buffer.enlist(new FailedPrepare());
        buffer.enlist(new MockOp());

        buffer.commit();        
        validateOps();
        store.validate();
    }

    @Test
    public void commitWithPersistance() throws AMQException
    {
        MockStore store = new MockStore();
        store.expectBegin().expectCommit();

        TxnBuffer buffer = new TxnBuffer(store);
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new MockOp().expectPrepare().expectCommit());
        buffer.enlist(new TxnTester(store));
        buffer.containsPersistentChanges();

        buffer.commit();
        validateOps();
        store.validate();
    }

    private void validateOps()
    {
        for(MockOp op : ops)
        {
            op.validate();
        }
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TxnBufferTest.class);
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

        public void prepare()
        {
            assertEquals(expected.removeLast(), PREPARE);
        }

        public void commit()
        {
            assertEquals(expected.removeLast(), COMMIT);
        }

        public void undoPrepare()
        {
            assertEquals(expected.removeLast(), UNDO_PREPARE);
        }

        public void rollback()
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

        public void beginTran() throws AMQException
        {
            assertEquals(expected.removeLast(), BEGIN);
            inTran = true;
        }
        
        public void commitTran() throws AMQException
        {
            assertEquals(expected.removeLast(), COMMIT);
            inTran = false;
        }
        
        public void abortTran() throws AMQException
        {
            assertEquals(expected.removeLast(), ABORT);
            inTran = false;
        }

        public boolean inTran()
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
        public void prepare() throws AMQException
        {
        }
        public void commit()
        {
        }
        public void undoPrepare()
        {
        }
        public void rollback()
        {
        }
    }

    class FailedPrepare extends NullOp
    {        
        public void prepare() throws AMQException
        {
            throw new AMQException("Fail!");
        }
    }

    class TxnTester extends NullOp
    {        
        private final MessageStore store;

        TxnTester(MessageStore store)
        {
            this.store = store;
        }

        public void prepare() throws AMQException
        {
            assertTrue("Expected prepare to be performed under txn", store.inTran());
        }

        public void commit()
        {
            assertTrue("Expected commit not to be performed under txn", !store.inTran());
        }
    }

}
