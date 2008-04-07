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
#include <TxBuffer.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <vector>

using namespace qpid::broker;

template <class T> void assertEqualVector(std::vector<T>& expected, std::vector<T>& actual){
    unsigned int i = 0;
    while(i < expected.size() && i < actual.size()){
        CPPUNIT_ASSERT_EQUAL(expected[i], actual[i]);
        i++;
    }
    CPPUNIT_ASSERT(i == expected.size());
    CPPUNIT_ASSERT(i == actual.size());
}

class TxBufferTest : public CppUnit::TestCase  
{
    class MockTxOp : public TxOp{
        enum op_codes {PREPARE=2, COMMIT=4, ROLLBACK=8};
        std::vector<int> expected;
        std::vector<int> actual;
        bool failOnPrepare;
    public:
        MockTxOp() : failOnPrepare(false) {}
        MockTxOp(bool _failOnPrepare) : failOnPrepare(_failOnPrepare) {}

        bool prepare(TransactionContext*) throw(){
            actual.push_back(PREPARE);
            return !failOnPrepare;
        }
        void commit()  throw(){
            actual.push_back(COMMIT);
        }
        void rollback()  throw(){
            actual.push_back(ROLLBACK);
        }
        MockTxOp& expectPrepare(){
            expected.push_back(PREPARE);
            return *this;
        }
        MockTxOp& expectCommit(){
            expected.push_back(COMMIT);
            return *this;
        }
        MockTxOp& expectRollback(){
            expected.push_back(ROLLBACK);
            return *this;
        }
        void check(){
            assertEqualVector(expected, actual);
        }
        ~MockTxOp(){}        
    };

    class MockTransactionalStore : public TransactionalStore{
        enum op_codes {BEGIN=2, COMMIT=4, ABORT=8};
        std::vector<int> expected;
        std::vector<int> actual;

        enum states {OPEN = 1, COMMITTED = 2, ABORTED = 3};
        int state;

        class TestTransactionContext : public TransactionContext{
            MockTransactionalStore* store;
        public:
            TestTransactionContext(MockTransactionalStore* _store) : store(_store) {}
            void commit(){
                if(store->state != OPEN) throw "txn already completed";
                store->state = COMMITTED;
            }

            void abort(){
                if(store->state != OPEN) throw "txn already completed";
                store->state = ABORTED;
            }
            ~TestTransactionContext(){}
        };


    public:
        MockTransactionalStore() : state(OPEN){}

        std::auto_ptr<TransactionContext> begin(){ 
            actual.push_back(BEGIN);
            std::auto_ptr<TransactionContext> txn(new TestTransactionContext(this));
            return txn;
        }
        void commit(TransactionContext* ctxt){
            actual.push_back(COMMIT);
            TestTransactionContext* txn(dynamic_cast<TestTransactionContext*>(ctxt));
            CPPUNIT_ASSERT(txn);
            txn->commit();
        }
        void abort(TransactionContext* ctxt){
            actual.push_back(ABORT);
            TestTransactionContext* txn(dynamic_cast<TestTransactionContext*>(ctxt));
            CPPUNIT_ASSERT(txn);
            txn->abort();
        }        
        MockTransactionalStore& expectBegin(){
            expected.push_back(BEGIN);
            return *this;
        }
        MockTransactionalStore& expectCommit(){
            expected.push_back(COMMIT);
            return *this;
        }
        MockTransactionalStore& expectAbort(){
            expected.push_back(ABORT);
            return *this;
        }
        void check(){
            assertEqualVector(expected, actual);
        }

        bool isCommitted(){
            return state == COMMITTED;
        }
        
        bool isAborted(){
            return state == ABORTED;
        }
        
        bool isOpen(){
            return state == OPEN;
        }
        ~MockTransactionalStore(){}
    };

    CPPUNIT_TEST_SUITE(TxBufferTest);
    CPPUNIT_TEST(testPrepareAndCommit);
    CPPUNIT_TEST(testFailOnPrepare);
    CPPUNIT_TEST(testRollback);
    CPPUNIT_TEST(testBufferIsClearedAfterRollback);
    CPPUNIT_TEST(testBufferIsClearedAfterCommit);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testPrepareAndCommit(){
        MockTransactionalStore store;
        store.expectBegin().expectCommit();

        MockTxOp opA;
        opA.expectPrepare().expectCommit();
        MockTxOp opB;
        opB.expectPrepare().expectPrepare().expectCommit().expectCommit();//opB enlisted twice to test reative order
        MockTxOp opC;
        opC.expectPrepare().expectCommit();

        TxBuffer buffer;
        buffer.enlist(&opA);
        buffer.enlist(&opB);
        buffer.enlist(&opB);//opB enlisted twice
        buffer.enlist(&opC);

        CPPUNIT_ASSERT(buffer.prepare(&store));
        buffer.commit();
        store.check();
        CPPUNIT_ASSERT(store.isCommitted());
        opA.check();
        opB.check();
        opC.check();
    }

    void testFailOnPrepare(){
        MockTransactionalStore store;
        store.expectBegin().expectAbort();

        MockTxOp opA;
        opA.expectPrepare();
        MockTxOp opB(true);
        opB.expectPrepare();
        MockTxOp opC;//will never get prepare as b will fail

        TxBuffer buffer;
        buffer.enlist(&opA);
        buffer.enlist(&opB);
        buffer.enlist(&opC);

        CPPUNIT_ASSERT(!buffer.prepare(&store));
        store.check();
        CPPUNIT_ASSERT(store.isAborted());
        opA.check();
        opB.check();
        opC.check();
    }

    void testRollback(){
        MockTxOp opA;
        opA.expectRollback();
        MockTxOp opB(true);
        opB.expectRollback();
        MockTxOp opC;
        opC.expectRollback();

        TxBuffer buffer;
        buffer.enlist(&opA);
        buffer.enlist(&opB);
        buffer.enlist(&opC);

        buffer.rollback();
        opA.check();
        opB.check();
        opC.check();
    }

    void testBufferIsClearedAfterRollback(){
        MockTxOp opA;
        opA.expectRollback();
        MockTxOp opB;
        opB.expectRollback();

        TxBuffer buffer;
        buffer.enlist(&opA);
        buffer.enlist(&opB);

        buffer.rollback();
        buffer.commit();//second call should not reach ops
        opA.check();
        opB.check();
    }

    void testBufferIsClearedAfterCommit(){
        MockTxOp opA;
        opA.expectCommit();
        MockTxOp opB;
        opB.expectCommit();

        TxBuffer buffer;
        buffer.enlist(&opA);
        buffer.enlist(&opB);

        buffer.commit();
        buffer.rollback();//second call should not reach ops
        opA.check();
        opB.check();
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TxBufferTest);

