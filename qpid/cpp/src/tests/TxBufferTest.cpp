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
#include "qpid/broker/TxBuffer.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <vector>
#include "TxMocks.h"

using namespace qpid::broker;
using boost::static_pointer_cast;

class TxBufferTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(TxBufferTest);
    CPPUNIT_TEST(testCommitLocal);
    CPPUNIT_TEST(testFailOnCommitLocal);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testFailOnPrepare);
    CPPUNIT_TEST(testRollback);
    CPPUNIT_TEST(testBufferIsClearedAfterRollback);
    CPPUNIT_TEST(testBufferIsClearedAfterCommit);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testCommitLocal(){
        MockTransactionalStore store;
        store.expectBegin().expectCommit();

        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectPrepare().expectCommit();
        MockTxOp::shared_ptr opB(new MockTxOp());
        opB->expectPrepare().expectPrepare().expectCommit().expectCommit();//opB enlisted twice to test relative order
        MockTxOp::shared_ptr opC(new MockTxOp());
        opC->expectPrepare().expectCommit();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));
        buffer.enlist(static_pointer_cast<TxOp>(opB));//opB enlisted twice
        buffer.enlist(static_pointer_cast<TxOp>(opC));

        CPPUNIT_ASSERT(buffer.commitLocal(&store));
        store.check();
        CPPUNIT_ASSERT(store.isCommitted());
        opA->check();
        opB->check();
        opC->check();
    }

    void testFailOnCommitLocal(){
        MockTransactionalStore store;
        store.expectBegin().expectAbort();

        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectPrepare().expectRollback();
        MockTxOp::shared_ptr opB(new MockTxOp(true));
        opB->expectPrepare().expectRollback();
        MockTxOp::shared_ptr opC(new MockTxOp());//will never get prepare as b will fail
        opC->expectRollback();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));
        buffer.enlist(static_pointer_cast<TxOp>(opC));

        CPPUNIT_ASSERT(!buffer.commitLocal(&store));
        CPPUNIT_ASSERT(store.isAborted());
        store.check();
        opA->check();
        opB->check();
        opC->check();
    }

    void testPrepare(){
        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectPrepare();
        MockTxOp::shared_ptr opB(new MockTxOp());
        opB->expectPrepare();
        MockTxOp::shared_ptr opC(new MockTxOp());
        opC->expectPrepare();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));
        buffer.enlist(static_pointer_cast<TxOp>(opC));

        CPPUNIT_ASSERT(buffer.prepare(0));
        opA->check();
        opB->check();
        opC->check();
    }

    void testFailOnPrepare(){
        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectPrepare();
        MockTxOp::shared_ptr opB(new MockTxOp(true));
        opB->expectPrepare();
        MockTxOp::shared_ptr opC(new MockTxOp());//will never get prepare as b will fail

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));
        buffer.enlist(static_pointer_cast<TxOp>(opC));

        CPPUNIT_ASSERT(!buffer.prepare(0));
        opA->check();
        opB->check();
        opC->check();
    }

    void testRollback(){
        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectRollback();
        MockTxOp::shared_ptr opB(new MockTxOp(true));
        opB->expectRollback();
        MockTxOp::shared_ptr opC(new MockTxOp());
        opC->expectRollback();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));
        buffer.enlist(static_pointer_cast<TxOp>(opC));

        buffer.rollback();
        opA->check();
        opB->check();
        opC->check();
    }

    void testBufferIsClearedAfterRollback(){
        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectRollback();
        MockTxOp::shared_ptr opB(new MockTxOp());
        opB->expectRollback();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));

        buffer.rollback();
        buffer.commit();//second call should not reach ops
        opA->check();
        opB->check();
    }

    void testBufferIsClearedAfterCommit(){
        MockTxOp::shared_ptr opA(new MockTxOp());
        opA->expectCommit();
        MockTxOp::shared_ptr opB(new MockTxOp());
        opB->expectCommit();

        TxBuffer buffer;
        buffer.enlist(static_pointer_cast<TxOp>(opA));
        buffer.enlist(static_pointer_cast<TxOp>(opB));

        buffer.commit();
        buffer.rollback();//second call should not reach ops
        opA->check();
        opB->check();
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TxBufferTest);

