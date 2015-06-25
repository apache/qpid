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
#include "test_tools.h"
#include "MessagingFixture.h"
#include "qpid/broker/BrokerObserver.h"
#include "qpid/broker/TransactionObserver.h"
#include "qpid/broker/TxBuffer.h"
#include "qpid/broker/Queue.h"
#include "qpid/ha/types.h"

#include <boost/bind.hpp>
#include <boost/function.hpp>
#include <boost/lexical_cast.hpp>
#include <iostream>
#include <vector>

namespace qpid {
namespace tests {

using framing::SequenceSet;
using messaging::Message;
using boost::shared_ptr;

using namespace boost::assign;
using namespace boost;
using namespace broker;
using namespace std;
using namespace messaging;
using namespace types;

QPID_AUTO_TEST_SUITE(TransactionalObserverTest)

Message msg(string content) { return Message(content); }

struct MockTransactionObserver : public TransactionObserver {
    bool prep;
    vector<string> events;

    MockTransactionObserver(bool prep_=true) : prep(prep_) {}

    void record(const string& e) { events.push_back(e); }

    void enqueue(const shared_ptr<Queue>& q,  const broker::Message& m) {
        record("enqueue "+q->getName()+" "+m.getContent());
    }
    void dequeue(const Queue::shared_ptr& q, SequenceNumber p, SequenceNumber r) {
        record("dequeue "+q->getName()+" "+
               lexical_cast<string>(p)+" "+lexical_cast<string>(r));
    }
    bool prepare() { record("prepare"); return prep; }
    void commit() { record("commit"); }
    void rollback() {record("rollback"); }
};

struct MockBrokerObserver : public BrokerObserver {
    bool prep;
    shared_ptr<MockTransactionObserver> tx;

    MockBrokerObserver(bool prep_=true) : prep(prep_) {}

    void startTx(const intrusive_ptr<TxBuffer>& buffer) {
        if (!tx) { // Don't overwrite first tx with automatically started second tx.
            tx.reset(new MockTransactionObserver(prep));
            buffer->setObserver(tx);
        }
    }
};

Session simpleTxTransaction(MessagingFixture& fix) {
    fix.session.createSender("q1;{create:always}").send(msg("foo")); // Not in TX
    // Transaction with 1 enqueue and 1 dequeue.
    Session txSession = fix.connection.createTransactionalSession();
    BOOST_CHECK_EQUAL("foo", txSession.createReceiver("q1").fetch().getContent());
    txSession.acknowledge();
    txSession.createSender("q2;{create:always}").send(msg("bar"));
    return txSession;
}

QPID_AUTO_TEST_CASE(testTxCommit) {
    MessagingFixture fix;
    shared_ptr<MockBrokerObserver> brokerObserver(new MockBrokerObserver);
    fix.broker->getBrokerObservers().add(brokerObserver);
    Session txSession = simpleTxTransaction(fix);
    txSession.commit();
    // Note on ordering: observers see enqueues as they happen, but dequeues just
    // before prepare.
    BOOST_CHECK_EQUAL(
        list_of<string>("enqueue q2 bar")("dequeue q1 1 0")("prepare")("commit"),
        brokerObserver->tx->events
    );
}

QPID_AUTO_TEST_CASE(testTxFail) {
    MessagingFixture fix;
    shared_ptr<MockBrokerObserver> brokerObserver(new MockBrokerObserver(false));
    fix.broker->getBrokerObservers().add(brokerObserver);
    Session txSession = simpleTxTransaction(fix);
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected error.
        txSession.commit();
        BOOST_FAIL("Expected exception");
    } catch(...) {}

    BOOST_CHECK_EQUAL(
        list_of<string>("enqueue q2 bar")("dequeue q1 1 0")("prepare")("rollback"),
        brokerObserver->tx->events
    );
}

QPID_AUTO_TEST_CASE(testTxRollback) {
    MessagingFixture fix;
    shared_ptr<MockBrokerObserver> brokerObserver(new MockBrokerObserver(false));
    fix.broker->getBrokerObservers().add(brokerObserver);
    Session txSession = simpleTxTransaction(fix);
    txSession.rollback();
    // Note: The dequeue does not appear here. This is because TxAccepts
    // (i.e. dequeues) are not enlisted until SemanticState::commit and are
    // never enlisted if the transaction is rolled back.
    BOOST_CHECK_EQUAL(
        list_of<string>("enqueue q2 bar")("rollback"),
        brokerObserver->tx->events
    );
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
