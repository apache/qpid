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

#include "qpid/legacystore/MessageStoreImpl.h"
#include <iostream>
#include "MessageUtils.h"
#include "qpid/legacystore/StoreException.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/RecoveryManagerImpl.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/log/Statement.h"
#include "qpid/log/Logger.h"
#include "qpid/sys/Timer.h"

using namespace mrg::msgstore;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace std;

namespace {
qpid::broker::Broker::Options opts;
qpid::broker::Broker br(opts);
}

QPID_AUTO_TEST_SUITE(TransactionalTest)

#define SET_LOG_LEVEL(level) \
    qpid::log::Options opts(""); \
    opts.selectors.clear(); \
    opts.selectors.push_back(level); \
    qpid::log::Logger::instance().configure(opts);

const string test_filename("TransactionalTest");
const char* tdp = getenv("TMP_DATA_DIR");
const string test_dir(tdp && strlen(tdp) > 0 ? tdp : "/tmp/TransactionalTest");

// Test txn context which has special setCompleteFailure() method which prevents entire "txn complete" process from hapenning
class TestTxnCtxt : public TxnCtxt
{
  public:
    TestTxnCtxt(IdSequence* _loggedtx) : TxnCtxt(_loggedtx) {}
    void setCompleteFailure(const unsigned num_queues_rem) {
        // Remove queue members from back of impactedQueues until queues_rem reamin.
        // to end to simulate multi-queue txn complete failure.
        while (impactedQueues.size() > num_queues_rem) impactedQueues.erase(impactedQueues.begin());
    }
    void resetPreparedXidStorePtr() { preparedXidStorePtr = 0; }
};

// Test store which has special begin() which returns a TestTPCTxnCtxt, and a method to check for
// remaining open transactions.
// begin(), commit(), and abort() all hide functions in MessageStoreImpl. To avoid the compiler
// warnings/errors these are renamed with a 'TMS' prefix.
class TestMessageStore: public MessageStoreImpl
{
  public:
    TestMessageStore(qpid::broker::Broker* br, const char* envpath = 0) : MessageStoreImpl(br, envpath) {}
    std::auto_ptr<qpid::broker::TransactionContext> TMSbegin() {
        checkInit();
        // pass sequence number for c/a
        return auto_ptr<TransactionContext>(new TestTxnCtxt(&messageIdSequence));
    }
    void TMScommit(TransactionContext& ctxt, const bool complete_prepared_list) {
        checkInit();
        TxnCtxt* txn(check(&ctxt));
        if (!txn->isTPC()) {
            localPrepare(dynamic_cast<TxnCtxt*>(txn));
            if (!complete_prepared_list) dynamic_cast<TestTxnCtxt*>(txn)->resetPreparedXidStorePtr();
        }
        completed(*dynamic_cast<TxnCtxt*>(txn), true);
    }
    void TMSabort(TransactionContext& ctxt, const bool complete_prepared_list)
    {
        checkInit();
        TxnCtxt* txn(check(&ctxt));
        if (!txn->isTPC()) {
            localPrepare(dynamic_cast<TxnCtxt*>(txn));
            if (!complete_prepared_list) dynamic_cast<TestTxnCtxt*>(txn)->resetPreparedXidStorePtr();
        }
        completed(*dynamic_cast<TxnCtxt*>(txn), false);
    }
};

// === Helper fns ===

const string nameA("queueA");
const string nameB("queueB");
//const Uuid messageId(true);
std::auto_ptr<MessageStoreImpl> store;
std::auto_ptr<QueueRegistry> queues;
Queue::shared_ptr queueA;
Queue::shared_ptr queueB;

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

template <class T>
void restart()
{
    queueA.reset();
    queueB.reset();
    queues.reset();
    store.reset();

    store = std::auto_ptr<T>(new T(&br));
    store->init(test_dir, 4, 1);
    queues = std::auto_ptr<QueueRegistry>(new QueueRegistry);
    ExchangeRegistry exchanges;
    LinkRegistry links;
    sys::Timer t;
    DtxManager mgr(t);
    mgr.setStore (store.get());
    RecoveryManagerImpl recovery(*queues, exchanges, links, mgr, br.getProtocolRegistry());
    store->recover(recovery);

    queueA = queues->find(nameA);
    queueB = queues->find(nameB);
}

Message createMessage(const string& id, const string& exchange="exchange", const string& key="routing_key")
{
    return MessageUtils::createMessage(exchange, key, Uuid(), true, 0, id);
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

void swap(bool commit)
{
    setup<MessageStoreImpl>();

    //create message and enqueue it onto first queue:
    Message msgA = createMessage("Message", "exchange", "routing_key");
    queueA->deliver(msgA);

    QueueCursor cursorB;
    Message msgB = MessageUtils::get(*queueA, &cursorB);
    BOOST_REQUIRE(msgB);
    //move the message from one queue to the other as a transaction
    std::auto_ptr<TransactionContext> txn = store->begin();
    TxBuffer tx;
    queueB->deliver(msgB, &tx);//note: need to enqueue it first to avoid message being deleted

    queueA->dequeue(txn.get(), cursorB);
    tx.prepare(txn.get());
    if (commit) {
        store->commit(*txn);
    } else {
        store->abort(*txn);
    }

    restart<MessageStoreImpl>();

    // Check outcome
    BOOST_REQUIRE(queueA);
    BOOST_REQUIRE(queueB);

    Queue::shared_ptr x;//the queue from which the message was swapped
    Queue::shared_ptr y;//the queue on which the message is expected to be

    if (commit) {
        x = queueA;
        y = queueB;
    } else {
        x = queueB;
        y = queueA;
    }

    checkMsg(x, 0);
    checkMsg(y, 1, "Message");
    checkMsg(y, 0);
}

void testMultiQueueTxn(const unsigned num_queues_rem, const bool complete_prepared_list, const bool commit)
{
    setup<TestMessageStore>();
    TestMessageStore* tmsp = static_cast<TestMessageStore*>(store.get());
    std::auto_ptr<TransactionContext> txn(tmsp->TMSbegin());
    TxBuffer tx;

    //create two messages and enqueue them onto both queues:
    Message msgA = createMessage("MessageA", "exchange", "routing_key");
    queueA->deliver(msgA, &tx);
    queueB->deliver(msgA, &tx);
    Message msgB = createMessage("MessageB", "exchange", "routing_key");
    queueA->deliver(msgB, &tx);
    queueB->deliver(msgB, &tx);

    tx.prepare(txn.get());
    static_cast<TestTxnCtxt*>(txn.get())->setCompleteFailure(num_queues_rem);
    if (commit)
        tmsp->TMScommit(*txn, complete_prepared_list);
    else
        tmsp->TMSabort(*txn, complete_prepared_list);
    restart<TestMessageStore>();

    // Check outcome
    if (commit)
    {
        checkMsg(queueA, 2, "MessageA");
        checkMsg(queueB, 2, "MessageA");
        checkMsg(queueA, 1, "MessageB");
        checkMsg(queueB, 1, "MessageB");
    }
    checkMsg(queueA, 0);
    checkMsg(queueB, 0);
}

// === Test suite ===

QPID_AUTO_TEST_CASE(Commit)
{
    SET_LOG_LEVEL("error+"); // This only needs to be set once.

    cout << test_filename << ".Commit: " << flush;
    swap(true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(Abort)
{
    cout << test_filename << ".Abort: " << flush;
    swap(false);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueCommit)
{
    cout << test_filename << ".MultiQueueCommit: " << flush;
    testMultiQueueTxn(2, true, true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAbort)
{
    cout << test_filename << ".MultiQueueAbort: " << flush;
    testMultiQueueTxn(2, true, false);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueNoQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueNoQueueCommitRecover: " << flush;
    testMultiQueueTxn(0, false, true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueNoQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueNoQueueAbortRecover: " << flush;
    testMultiQueueTxn(0, false, false);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueSomeQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueSomeQueueCommitRecover: " << flush;
    testMultiQueueTxn(1, false, true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueSomeQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueSomeQueueAbortRecover: " << flush;
    testMultiQueueTxn(1, false, false);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAllQueueCommitRecover)
{
    cout << test_filename << ".MultiQueueAllQueueCommitRecover: " << flush;
    testMultiQueueTxn(2, false, true);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(MultiQueueAllQueueAbortRecover)
{
    cout << test_filename << ".MultiQueueAllQueueAbortRecover: " << flush;
    testMultiQueueTxn(2, false, false);
    cout << "ok" << endl;
}

QPID_AUTO_TEST_CASE(LockedRecordTest)
{
    cout << test_filename << ".LockedRecordTest: " << flush;

    setup<MessageStoreImpl>();
    queueA->deliver(createMessage("Message", "exchange", "routingKey"));
    std::auto_ptr<TransactionContext> txn = store->begin();

    QueueCursor cursor;
    Message msg = MessageUtils::get(*queueA, &cursor);
    queueA->dequeue(txn.get(), cursor);

    try {
        store->dequeue(0, msg.getPersistentContext(), *queueA);
        BOOST_ERROR("Did not throw JERR_MAP_LOCKED exception as expected.");
    }
    catch (const mrg::msgstore::StoreException& e) {
        if (std::strstr(e.what(), "JERR_MAP_LOCKED") == 0)
            BOOST_ERROR("Unexpected StoreException: " << e.what());
    }
    catch (const std::exception& e) {
        BOOST_ERROR("Unexpected exception: " << e.what());
    }
    store->commit(*txn);
    checkMsg(queueA, 0);

    cout << "ok" << endl;
}

QPID_AUTO_TEST_SUITE_END()
