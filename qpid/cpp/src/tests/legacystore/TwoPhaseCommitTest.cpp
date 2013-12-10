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

#include "unit_test.h"
#include "MessageUtils.h"

#include "qpid/broker/Queue.h"
#include "qpid/broker/RecoveryManagerImpl.h"
#include "qpid/broker/PersistableObject.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/legacystore/MessageStoreImpl.h"
#include "qpid/legacystore/TxnCtxt.h"
#include "qpid/log/Logger.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Timer.h"

#include <iostream>

using namespace mrg::msgstore;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace std;


qpid::broker::Broker::Options opts;
qpid::broker::Broker br(opts);


QPID_AUTO_TEST_SUITE(TwoPhaseCommitTest)

#define SET_LOG_LEVEL(level) \
    qpid::log::Options opts(""); \
    opts.selectors.clear(); \
    opts.selectors.push_back(level); \
    qpid::log::Logger::instance().configure(opts);


const string test_filename("TwoPhaseCommitTest");
const char* tdp = getenv("TMP_DATA_DIR");
string test_dir(tdp && strlen(tdp) > 0 ? tdp : "/var/tmp/TwoPhaseCommitTest");

// === Helper fns ===

class TwoPhaseCommitTest
{

    class Strategy
    {
    public:
        virtual void init() = 0;
        virtual void run(TPCTransactionContext* txn) = 0;
        virtual void check(bool committed) = 0;
        virtual ~Strategy(){}
    };

    class Swap : public Strategy
    {
        TwoPhaseCommitTest* const test;
        const string messageId;
        Message msg;
    public:
        Swap(TwoPhaseCommitTest* const test_, const string& messageId_): test(test_), messageId(messageId_) {}
        void init(){ msg = test->deliver(messageId, test->queueA); }
        void run(TPCTransactionContext* txn) { test->swap(txn, test->queueA, test->queueB); }
        void check(bool committed) { test->swapCheck(committed, messageId,  test->queueA, test->queueB); }
    };

    class Enqueue : public Strategy
    {
        TwoPhaseCommitTest* const test;
	    Message msg1;
	    Message msg2;
	    Message msg3;
    public:
        Enqueue(TwoPhaseCommitTest* const test_): test(test_) {}
        void init() {}
        void run(TPCTransactionContext* txn) {
            msg1 = test->enqueue(txn, "Enqueue1", test->queueA);
            msg2 = test->enqueue(txn, "Enqueue2", test->queueA);
            msg3 = test->enqueue(txn, "Enqueue3", test->queueA);
        }
        void check(bool committed) {
            if (committed) {
                test->checkMsg(test->queueA, 3, "Enqueue1");
                test->checkMsg(test->queueA, 2, "Enqueue2");
                test->checkMsg(test->queueA, 1, "Enqueue3");
            }
            test->checkMsg(test->queueA, 0);
        }
    };

    class Dequeue : public Strategy
    {
        TwoPhaseCommitTest* const test;
	    Message msg1;
	    Message msg2;
	    Message msg3;
    public:
        Dequeue(TwoPhaseCommitTest* const test_): test(test_) {}
        void init() {
            msg1 = test->deliver("Dequeue1", test->queueA);
            msg2 = test->deliver("Dequeue2", test->queueA);
            msg3 = test->deliver("Dequeue3", test->queueA);
        }
        void run(TPCTransactionContext* txn) {
            test->dequeue(txn, test->queueA);
            test->dequeue(txn, test->queueA);
            test->dequeue(txn, test->queueA);
        }
        void check(bool committed) {
            if (!committed) {
                test->checkMsg(test->queueA, 3, "Dequeue1");
                test->checkMsg(test->queueA, 2, "Dequeue2");
                test->checkMsg(test->queueA, 1, "Dequeue3");
            }
            test->checkMsg(test->queueA, 0);
        }
    };

    class MultiQueueTxn : public Strategy
    {
        TwoPhaseCommitTest* const test;
	    Message msg1;
	    Message msg2;
        std::set<Queue::shared_ptr> queueset;
    public:
        MultiQueueTxn(TwoPhaseCommitTest* const test_): test(test_) {}
        virtual void init() {}
        virtual void run(TPCTransactionContext* txn) {
            queueset.insert(test->queueA);
            queueset.insert(test->queueB);
            msg1 = test->enqueue(txn, "Message1", queueset);
            msg2 = test->enqueue(txn, "Message2", queueset);
            queueset.clear();
        }
        virtual void check(bool committed) {
            TestMessageStore* sptr = static_cast<TestMessageStore*>(test->store.get());
            if (committed)
            {
                test->checkMsg(test->queueA, 2, "Message1");
                test->checkMsg(test->queueB, 2, "Message1");
                test->checkMsg(test->queueA, 1, "Message2");
                test->checkMsg(test->queueB, 1, "Message2");
            }
            test->checkMsg(test->queueA, 0);
            test->checkMsg(test->queueB, 0);
            // Check there are no remaining open txns in store
            BOOST_CHECK_EQUAL(u_int32_t(0), sptr->getRemainingTxns(*(test->queueA)));
            BOOST_CHECK_EQUAL(u_int32_t(0), sptr->getRemainingTxns(*(test->queueB)));
            BOOST_CHECK_EQUAL(u_int32_t(0), sptr->getRemainingPreparedListTxns());
        }
    };

    // Test txn context which has special setCompleteFailure() method which prevents entire "txn complete" process from hapenning
    class TestTPCTxnCtxt : public TPCTxnCtxt
    {
      public:
        TestTPCTxnCtxt(const std::string& _xid, IdSequence* _loggedtx) : TPCTxnCtxt(_xid, _loggedtx) {}
        void setCompleteFailure(const unsigned num_queues_rem, const bool complete_prepared_list) {
            // Remove queue members from back of impactedQueues until queues_rem reamin.
            // to end to simulate multi-queue txn complete failure.
            while (impactedQueues.size() > num_queues_rem) impactedQueues.erase(impactedQueues.begin());
            // If prepared list is not to be committed, set pointer to 0
            if (!complete_prepared_list) preparedXidStorePtr = 0;
        }
    };

    // Test store which has sepcial begin() which returns a TestTPCTxnCtxt, and a method to check for
    // reamining open transactions
    class TestMessageStore: public MessageStoreImpl
    {
      public:
        TestMessageStore(qpid::broker::Broker* br, const char* envpath = 0) : MessageStoreImpl(br, envpath) {}
        std::auto_ptr<qpid::broker::TPCTransactionContext> TMSbegin(const std::string& xid) {
            checkInit();
            IdSequence* jtx = &messageIdSequence;
            // pass sequence number for c/a
            return auto_ptr<TPCTransactionContext>(new TestTPCTxnCtxt(xid, jtx));
        }
        u_int32_t getRemainingTxns(const PersistableQueue& queue) {
            return static_cast<JournalImpl*>(queue.getExternalQueueStore())->get_open_txn_cnt();
        }
        u_int32_t getRemainingPreparedListTxns() {
            return tplStorePtr->get_open_txn_cnt();
        }
    };

    const string nameA;
    const string nameB;
    std::auto_ptr<MessageStoreImpl> store;
    std::auto_ptr<DtxManager> dtxmgr;
    std::auto_ptr<QueueRegistry> queues;
    std::auto_ptr<LinkRegistry> links;
    Queue::shared_ptr queueA;
    Queue::shared_ptr queueB;
    Message msg1;
    Message msg2;
    Message msg4;
    std::auto_ptr<TxBuffer> tx;

    void recoverPrepared(bool commit)
    {
        setup<MessageStoreImpl>();

        Swap swap(this, "RecoverPrepared");
        swap.init();
        std::auto_ptr<TPCTransactionContext> txn(store->begin("my-xid"));
        swap.run(txn.get());
        if (tx.get()) {
            tx->prepare(txn.get());
            tx.reset();
        }

        store->prepare(*txn);
        restart<MessageStoreImpl>();

        //check that the message is not available from either queue
        BOOST_CHECK_EQUAL((u_int32_t) 0, queueA->getMessageCount());
        BOOST_CHECK_EQUAL((u_int32_t) 0, queueB->getMessageCount());

        //commit/abort the txn - through the dtx manager, not directly on the store
        if (commit) {
            dtxmgr->commit("my-xid", false);
        } else {
            dtxmgr->rollback("my-xid");
        }

        swap.check(commit);
        restart<MessageStoreImpl>();
        swap.check(commit);
    }

    void testMultiQueueTxn(const unsigned num_queues_rem, const bool complete_prepared_list, const bool commit)
    {
        setup<TestMessageStore>();
        MultiQueueTxn mqtTest(this);
        mqtTest.init();
        std::auto_ptr<TPCTransactionContext> txn(static_cast<TestMessageStore*>(store.get())->begin("my-xid"));
        mqtTest.run(txn.get());
        if (tx.get()) {
            tx->prepare(txn.get());
            tx.reset();
        }
        store->prepare(*txn);

        // As the commits and aborts should happen through DtxManager, and it is too complex to
        // pass all these test params through, we bypass DtxManager and use the store directly.
        // This will prevent the queues from seeing committed txns, however. To test the success
        // or failure of
        static_cast<TestTPCTxnCtxt*>(txn.get())->setCompleteFailure(num_queues_rem, complete_prepared_list);
        if (commit)
            store->commit(*txn);
        else
            store->abort(*txn);
        restart<TestMessageStore>();
        mqtTest.check(commit);
    }

    void commit(Strategy& strategy)
    {
        setup<MessageStoreImpl>();
        strategy.init();

        std::auto_ptr<TPCTransactionContext> txn(store->begin("my-xid"));
        strategy.run(txn.get());
        if (tx.get()) {
            tx->prepare(txn.get());
            tx.reset();
        }
        store->prepare(*txn);
        store->commit(*txn);
        restart<MessageStoreImpl>();
        strategy.check(true);
    }

    void abort(Strategy& strategy, bool prepare)
    {
        setup<MessageStoreImpl>();
        strategy.init();

        std::auto_ptr<TPCTransactionContext> txn(store->begin("my-xid"));
        strategy.run(txn.get());
        if (tx.get()) {
            tx->prepare(txn.get());
            tx.reset();
        }
        if (prepare) store->prepare(*txn);
        store->abort(*txn);
        restart<MessageStoreImpl>();
        strategy.check(false);
    }

    void swap(TPCTransactionContext* txn, Queue::shared_ptr& from, Queue::shared_ptr& to)
    {
        QueueCursor c;
        Message msg1 = MessageUtils::get(*from, &c);//just dequeues in memory
        //move the message from one queue to the other as part of a
        //distributed transaction
        if (!tx.get()) tx = std::auto_ptr<TxBuffer>(new TxBuffer);
        to->deliver(msg1, tx.get());//note: need to enqueue it first to avoid message being deleted
        from->dequeue(txn, c);
    }

    void dequeue(TPCTransactionContext* txn, Queue::shared_ptr& queue)
    {
        QueueCursor c;
        Message msg2 = MessageUtils::get(*queue, &c);//just dequeues in memory
        queue->dequeue(txn, c);
    }

    Message enqueue(TPCTransactionContext* /*txn*/, const string& msgid, Queue::shared_ptr& queue)
    {
        Message msg = createMessage(msgid);
        if (!tx.get()) tx = std::auto_ptr<TxBuffer>(new TxBuffer);
        queue->deliver(msg, tx.get());
        return msg;
    }

    Message enqueue(TPCTransactionContext* /*txn*/, const string& msgid, std::set<Queue::shared_ptr>& queueset)
    {
        if (!tx.get()) tx = std::auto_ptr<TxBuffer>(new TxBuffer);
        Message msg = createMessage(msgid);
        for (std::set<Queue::shared_ptr>::iterator i = queueset.begin(); i != queueset.end(); i++) {
            (*i)->deliver(msg, tx.get());
        }
        return msg;
    }

    Message deliver(const string& msgid, Queue::shared_ptr& queue)
    {
        Message m = createMessage(msgid);
        queue->deliver(m);
        return m;
    }

    template <class T>
    void setup()
    {
        store = std::auto_ptr<T>(new T(&br));
        store->init(test_dir, 4, 1, true); // truncate store

        //create two queues:
        queueA = Queue::shared_ptr(new Queue(nameA, 0, store.get(), 0));
        queueA->create();
        queueB = Queue::shared_ptr(new Queue(nameB, 0, store.get(), 0));
        queueB->create();
    }

    Message createMessage(const string& id, const string& exchange="exchange", const string& key="routing_key")
    {
        Message msg = MessageUtils::createMessage(exchange, key, Uuid(), true, 0, id);
        return msg;
    }

    template <class T>
    void restart()
    {
        queueA.reset();
        queueB.reset();
        store.reset();
        queues.reset();
        links.reset();

        store = std::auto_ptr<T>(new T(&br));
        store->init(test_dir, 4, 1);
        sys::Timer t;
        ExchangeRegistry exchanges;
        queues = std::auto_ptr<QueueRegistry>(new QueueRegistry);
        links = std::auto_ptr<LinkRegistry>(new LinkRegistry);
        dtxmgr = std::auto_ptr<DtxManager>(new DtxManager(t));
        dtxmgr->setStore (store.get());
        RecoveredObjects ro;
        RecoveryManagerImpl recovery(*queues, exchanges, *links, *dtxmgr, br.getProtocolRegistry(), ro);
        store->recover(recovery);

        queueA = queues->find(nameA);
        queueB = queues->find(nameB);
    }

    void checkMsg(Queue::shared_ptr& queue, u_int32_t size, const string& msgid = "<none>")
    {
        BOOST_REQUIRE(queue);
        BOOST_CHECK_EQUAL(size, queue->getMessageCount());
        if (size > 0) {
            Message msg = MessageUtils::get(*queue);
            BOOST_REQUIRE(msg);
            BOOST_CHECK_EQUAL(msgid, MessageUtils::getCorrelationId(msg));
        }
    }

    void swapCheck(bool swapped, const string& msgid, Queue::shared_ptr& from, Queue::shared_ptr& to)
    {
        BOOST_REQUIRE(from);
        BOOST_REQUIRE(to);

        Queue::shared_ptr x; //the queue from which the message was swapped
        Queue::shared_ptr y; //the queue on which the message is expected to be

        if (swapped) {
            x = from;
            y = to;
        } else {
            x = to;
            y = from;
        }

        checkMsg(x, 0);
        checkMsg(y, 1, msgid);
        checkMsg(y, 0);
    }

public:
    TwoPhaseCommitTest() : nameA("queueA"), nameB("queueB") {}

    void testCommitEnqueue()
    {
        Enqueue enqueue(this);
        commit(enqueue);
    }

    void testCommitDequeue()
    {
        Dequeue dequeue(this);
        commit(dequeue);
    }

    void testCommitSwap()
    {
        Swap swap(this, "SwapMessageId");
        commit(swap);
    }

    void testPrepareAndAbortEnqueue()
    {
        Enqueue enqueue(this);
        abort(enqueue, true);
    }

    void testPrepareAndAbortDequeue()
    {
        Dequeue dequeue(this);
        abort(dequeue, true);
    }

    void testPrepareAndAbortSwap()
    {
        Swap swap(this, "SwapMessageId");
        abort(swap, true);
    }

    void testAbortNoPrepareEnqueue()
    {
        Enqueue enqueue(this);
        abort(enqueue, false);
    }

    void testAbortNoPrepareDequeue()
    {
        Dequeue dequeue(this);
        abort(dequeue, false);
    }

    void testAbortNoPrepareSwap()
    {
        Swap swap(this, "SwapMessageId");
        abort(swap, false);
    }

    void testRecoverPreparedThenCommitted()
    {
        recoverPrepared(true);
    }

    void testRecoverPreparedThenAborted()
    {
        recoverPrepared(false);
    }

    void testMultiQueueCommit()
    {
        testMultiQueueTxn(2, true, true);
    }

    void testMultiQueueAbort()
    {
        testMultiQueueTxn(2, true, false);
    }

    void testMultiQueueNoQueueCommitRecover()
    {
        testMultiQueueTxn(0, false, true);
    }

    void testMultiQueueNoQueueAbortRecover()
    {
        testMultiQueueTxn(0, false, false);
    }

    void testMultiQueueSomeQueueCommitRecover()
    {
        testMultiQueueTxn(1, false, true);
    }

    void testMultiQueueSomeQueueAbortRecover()
    {
        testMultiQueueTxn(1, false, false);
    }

    void testMultiQueueAllQueueCommitRecover()
    {
        testMultiQueueTxn(2, false, true);
    }

    void testMultiQueueAllQueueAbortRecover()
    {
        testMultiQueueTxn(2, false, false);
    }
};

TwoPhaseCommitTest tpct;

// === Test suite ===

QPID_AUTO_TEST_CASE(CommitEnqueue)
{
    SET_LOG_LEVEL("error+"); // This only needs to be set once.

    cout << test_filename << ".CommitEnqueue: " << flush;
    tpct.testCommitEnqueue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(CommitDequeue)
{
    cout << test_filename << ".CommitDequeue: " << flush;
    tpct.testCommitDequeue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(CommitSwap)
{
    cout << test_filename << ".CommitSwap: " << flush;
    tpct.testCommitSwap();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(PrepareAndAbortEnqueue)
{
    cout << test_filename << ".PrepareAndAbortEnqueue: " << flush;
    tpct.testPrepareAndAbortEnqueue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(PrepareAndAbortDequeue)
{
    cout << test_filename << ".PrepareAndAbortDequeue: " << flush;
    tpct.testPrepareAndAbortDequeue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(PrepareAndAbortSwap)
{
    cout << test_filename << ".PrepareAndAbortSwap: " << flush;
    tpct.testPrepareAndAbortSwap();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(AbortNoPrepareEnqueue)
{
    cout << test_filename << ".AbortNoPrepareEnqueue: " << flush;
    tpct.testAbortNoPrepareEnqueue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(AbortNoPrepareDequeue)
{
    cout << test_filename << ".AbortNoPrepareDequeue: " << flush;
    tpct.testAbortNoPrepareDequeue();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(AbortNoPrepareSwap)
{
    cout << test_filename << ".AbortNoPrepareSwap: " << flush;
    tpct.testAbortNoPrepareSwap();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(RecoverPreparedThenCommitted)
{
    cout << test_filename << ".RecoverPreparedThenCommitted: " << flush;
    tpct.testRecoverPreparedThenCommitted();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(RecoverPreparedThenAborted)
{
    cout << test_filename << ".RecoverPreparedThenAborted: " << flush;
    tpct.testRecoverPreparedThenAborted();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueCommit)
{
    cout << test_filename << ".MultiQueueCommit: " << flush;
    tpct.testMultiQueueCommit();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAbort)
{
    cout << test_filename << ".MultiQueueAbort: " << flush;
    tpct.testMultiQueueAbort();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueNoQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueNoQueueCommitRecover: " << flush;
    tpct.testMultiQueueNoQueueCommitRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueNoQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueNoQueueAbortRecover: " << flush;
    tpct.testMultiQueueNoQueueAbortRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueSomeQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueSomeQueueCommitRecover: " << flush;
    tpct.testMultiQueueSomeQueueCommitRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueSomeQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueSomeQueueAbortRecover: " << flush;
    tpct.testMultiQueueSomeQueueAbortRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAllQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueAllQueueCommitRecover: " << flush;
    tpct.testMultiQueueAllQueueCommitRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAllQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueAllQueueAbortRecover: " << flush;
    tpct.testMultiQueueAllQueueAbortRecover();
    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
