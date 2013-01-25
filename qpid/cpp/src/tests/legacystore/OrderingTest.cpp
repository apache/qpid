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
#include <qpid/broker/Queue.h>
#include <qpid/broker/RecoveryManagerImpl.h>
#include <qpid/framing/AMQHeaderBody.h>
#include "qpid/log/Logger.h"
#include "qpid/sys/Timer.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace mrg::msgstore;

qpid::broker::Broker::Options opts;
qpid::broker::Broker br(opts);

QPID_AUTO_TEST_SUITE(OrderingTest)

#define SET_LOG_LEVEL(level) \
    qpid::log::Options opts(""); \
    opts.selectors.clear(); \
    opts.selectors.push_back(level); \
    qpid::log::Logger::instance().configure(opts);

const std::string test_filename("OrderingTest");
const char* tdp = getenv("TMP_DATA_DIR");
const std::string test_dir(tdp && strlen(tdp) > 0 ? tdp : "/tmp/OrderingTest");

// === Helper fns ===

const std::string name("OrderingQueue");
std::auto_ptr<MessageStoreImpl> store;
QueueRegistry queues;
Queue::shared_ptr queue;
std::queue<Uuid> ids;

class TestConsumer :  public Consumer
{
    public:

    TestConsumer(Queue::shared_ptr q, std::queue<Uuid>& i) : Consumer("test", CONSUMER), queue(q), ids(i) {};

    bool deliver(const QueueCursor& cursor, const Message& message)
    {
        queue->dequeue(0, cursor);
        BOOST_CHECK_EQUAL(ids.front(), MessageUtils::getMessageId(message));
        ids.pop();
        return true;
    };
    void notify() {}
    void cancel() {}
    void acknowledged(const DeliveryRecord&) {}
    OwnershipToken* getSession() { return 0; }
  private:
    Queue::shared_ptr queue;
    std::queue<Uuid>& ids;
};
boost::shared_ptr<TestConsumer> consumer;

void setup()
{
    store = std::auto_ptr<MessageStoreImpl>(new MessageStoreImpl(&br));
    store->init(test_dir, 4, 1, true); // truncate store

    queue = Queue::shared_ptr(new Queue(name, 0, store.get(), 0));
    queue->create();
    consumer = boost::shared_ptr<TestConsumer>(new TestConsumer(queue, ids));
}

void push()
{
    Uuid messageId(true);
    ids.push(messageId);

    Message msg = MessageUtils::createMessage("exchange", "routing_key", messageId, true, 0);

    queue->deliver(msg);
}

bool pop()
{
    return queue->dispatch(consumer);
}

void restart()
{
    queue.reset();
    store.reset();

    store = std::auto_ptr<MessageStoreImpl>(new MessageStoreImpl(&br));
    store->init(test_dir, 4, 1);
    ExchangeRegistry exchanges;
    LinkRegistry links;
    sys::Timer t;
    DtxManager mgr(t);
    mgr.setStore (store.get());
    RecoveryManagerImpl recoveryMgr(queues, exchanges, links, mgr, br.getProtocolRegistry());
    store->recover(recoveryMgr);

    queue = queues.find(name);
    consumer = boost::shared_ptr<TestConsumer>(new TestConsumer(queue, ids));
}

void check()
{
    BOOST_REQUIRE(queue);
    BOOST_CHECK_EQUAL((u_int32_t) ids.size(), queue->getMessageCount());
    while (pop()) ;//keeping popping 'till all messages are dequeued
    BOOST_CHECK_EQUAL((u_int32_t) 0, queue->getMessageCount());
    BOOST_CHECK_EQUAL((size_t) 0, ids.size());
}


// === Test suite ===

QPID_AUTO_TEST_CASE(Basic)
{
    SET_LOG_LEVEL("error+"); // This only needs to be set once.

    std::cout << test_filename << ".Basic: " << std::flush;
    setup();
    //push on 10 messages
    for (int i = 0; i < 10; i++) push();
    restart();
    check();
    std::cout << "ok" << std::endl;
}

QPID_AUTO_TEST_CASE(Cycle)
{
    std::cout << test_filename << ".Cycle: " << std::flush;
    setup();
    //push on 10 messages:
    for (int i = 0; i < 10; i++) push();
    //pop 5:
    for (int i = 0; i < 5; i++) pop();
    //push on another 5:
    for (int i = 0; i < 5; i++) push();
    restart();
    check();
    std::cout << "ok" << std::endl;
}

QPID_AUTO_TEST_SUITE_END()
