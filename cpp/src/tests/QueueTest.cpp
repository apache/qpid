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
#include "MessageUtils.h"
#include "unit_test.h"
#include "test_tools.h"
#include "qpid/Exception.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/framing/DeliveryProperties.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/client/QueueOptions.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Timer.h"

#include <iostream>
#include <vector>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>

using namespace std;
using boost::intrusive_ptr;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::client;
using namespace qpid::framing;
using namespace qpid::sys;

namespace qpid {
namespace tests {
class TestConsumer : public virtual Consumer{
public:
    typedef boost::shared_ptr<TestConsumer> shared_ptr;

    QueueCursor lastCursor;
    Message lastMessage;
    bool received;
    TestConsumer(std::string name="test", bool acquire = true) : Consumer(name, acquire ? CONSUMER : BROWSER, ""), received(false) {};

    virtual bool deliver(const QueueCursor& cursor, const Message& message){
        lastCursor = cursor;
        lastMessage = message;
        received = true;
        return true;
    };
    void notify() {}
    void cancel() {}
    void acknowledged(const DeliveryRecord&) {}
    OwnershipToken* getSession() { return 0; }
};

class FailOnDeliver : public Deliverable
{
    Message msg;
public:
    FailOnDeliver() : msg(MessageUtils::createMessage()) {}
    void deliverTo(const boost::shared_ptr<Queue>& queue)
    {
        throw Exception(QPID_MSG("Invalid delivery to " << queue->getName()));
    }
    Message& getMessage() { return msg; }
};

QPID_AUTO_TEST_SUITE(QueueTestSuite)

QPID_AUTO_TEST_CASE(testBound){
    //test the recording of bindings, and use of those to allow a queue to be unbound
    string key("my-key");
    FieldTable args;

    Queue::shared_ptr queue(new Queue("my-queue"));
    ExchangeRegistry exchanges;
    //establish bindings from exchange->queue and notify the queue as it is bound:
    Exchange::shared_ptr exchange1 = exchanges.declare("my-exchange-1", "direct").first;
    exchange1->bind(queue, key, &args);
    queue->bound(exchange1->getName(), key, args);

    Exchange::shared_ptr exchange2 = exchanges.declare("my-exchange-2", "fanout").first;
    exchange2->bind(queue, key, &args);
    queue->bound(exchange2->getName(), key, args);

    Exchange::shared_ptr exchange3 = exchanges.declare("my-exchange-3", "topic").first;
    exchange3->bind(queue, key, &args);
    queue->bound(exchange3->getName(), key, args);

    //delete one of the exchanges:
    exchanges.destroy(exchange2->getName());
    exchange2.reset();

    //unbind the queue from all exchanges it knows it has been bound to:
    queue->unbind(exchanges);

    //ensure the remaining exchanges don't still have the queue bound to them:
    FailOnDeliver deliverable;
    exchange1->route(deliverable);
    exchange3->route(deliverable);
}

QPID_AUTO_TEST_CASE(testLVQ){

    QueueSettings settings;
    string key="key";
    settings.lvqKey = key;
    QueueFactory factory;
    Queue::shared_ptr q(factory.create("my-queue", settings));

    const char* values[] = { "a", "b", "c", "a"};
    for (size_t i = 0; i < sizeof(values)/sizeof(values[0]); ++i) {
        qpid::types::Variant::Map properties;
        properties[key] = values[i];
        q->deliver(MessageUtils::createMessage(properties, boost::lexical_cast<string>(i+1)));
    }
    BOOST_CHECK_EQUAL(q->getMessageCount(), 3u);

    TestConsumer::shared_ptr c(new TestConsumer("test", true));
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("2"), c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("3"), c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("4"), c->lastMessage.getContent());


    const char* values2[] = { "a", "b", "c"};
    for (size_t i = 0; i < sizeof(values2)/sizeof(values2[0]); ++i) {
        qpid::types::Variant::Map properties;
        properties[key] = values[i];
        q->deliver(MessageUtils::createMessage(properties, boost::lexical_cast<string>(i+5)));
    }
    BOOST_CHECK_EQUAL(q->getMessageCount(), 3u);

    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("5"), c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("6"), c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(std::string("7"), c->lastMessage.getContent());
}

QPID_AUTO_TEST_CASE(testLVQEmptyKey){

    QueueSettings settings;
    string key="key";
    settings.lvqKey = key;
    QueueFactory factory;
    Queue::shared_ptr q(factory.create("my-queue", settings));


    qpid::types::Variant::Map properties;
    properties["key"] = "a";
    q->deliver(MessageUtils::createMessage(properties, "one"));
    properties.clear();
    q->deliver(MessageUtils::createMessage(properties, "two"));
    BOOST_CHECK_EQUAL(q->getMessageCount(), 2u);
}

void addMessagesToQueue(uint count, Queue& queue, uint oddTtl = 200, uint evenTtl = 0)
{
    for (uint i = 0; i < count; i++) {
        Message m = MessageUtils::createMessage("exchange", "key", i % 2 ? oddTtl : evenTtl);
        queue.deliver(m);
    }
}

QPID_AUTO_TEST_CASE(testPurgeExpired) {
    Queue queue("my-queue");
    addMessagesToQueue(10, queue);
    BOOST_CHECK_EQUAL(queue.getMessageCount(), 10u);
    ::usleep(300*1000);
    queue.purgeExpired(0);
    BOOST_CHECK_EQUAL(queue.getMessageCount(), 5u);
}

QPID_AUTO_TEST_CASE(testQueueCleaner) {
    boost::shared_ptr<Poller> poller(new Poller);
    Thread runner(poller.get());
    Timer timer;
    QueueRegistry queues;
    Queue::shared_ptr queue = queues.declare("my-queue", QueueSettings()).first;
    addMessagesToQueue(10, *queue, 200, 400);
    BOOST_CHECK_EQUAL(queue->getMessageCount(), 10u);

    QueueCleaner cleaner(queues, poller, &timer);
    cleaner.start(100 * qpid::sys::TIME_MSEC);
    ::usleep(300*1000);
    BOOST_CHECK_EQUAL(queue->getMessageCount(), 5u);
    ::usleep(300*1000);
    BOOST_CHECK_EQUAL(queue->getMessageCount(), 0u);
    poller->shutdown();
    runner.join();
}
namespace {
int getIntProperty(const Message& message, const std::string& key)
{
    qpid::types::Variant v = message.getProperty(key);
    int i(0);
    if (!v.isVoid()) i = v;
    return i;
}
// helper for group tests
void verifyAcquire( Queue::shared_ptr queue,
                    TestConsumer::shared_ptr c,
                    std::deque<QueueCursor>& results,
                    const std::string& expectedGroup,
                    const int expectedId )
{
    bool success = queue->dispatch(c);
    BOOST_CHECK(success);
    if (success) {
        results.push_back(c->lastCursor);
        std::string group = c->lastMessage.getPropertyAsString("GROUP-ID");
        int id = getIntProperty(c->lastMessage, "MY-ID");
        BOOST_CHECK_EQUAL( group, expectedGroup );
        BOOST_CHECK_EQUAL( id, expectedId );
    }
}

Message createGroupMessage(int id, const std::string& group)
{
    qpid::types::Variant::Map properties;
    properties["GROUP-ID"] = group;
    properties["MY-ID"] = id;
    return MessageUtils::createMessage(properties);
}
}

QPID_AUTO_TEST_CASE(testGroupsMultiConsumer) {
    //
    // Verify that consumers of grouped messages own the groups once a message is acquired,
    // and release the groups once all acquired messages have been dequeued or requeued
    //
    QueueSettings settings;
    settings.shareGroups = 1;
    settings.groupKey = "GROUP-ID";
    QueueFactory factory;
    Queue::shared_ptr queue(factory.create("my_queue", settings));

    std::string groups[] = { std::string("a"), std::string("a"), std::string("a"),
                             std::string("b"), std::string("b"), std::string("b"),
                             std::string("c"), std::string("c"), std::string("c") };
    for (int i = 0; i < 9; ++i) {
        queue->deliver(createGroupMessage(i, groups[i]));
    }

    // Queue = a-0, a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
    // Owners= ---, ---, ---, ---, ---, ---, ---, ---, ---,

    BOOST_CHECK_EQUAL(uint32_t(9), queue->getMessageCount());

    TestConsumer::shared_ptr c1(new TestConsumer("C1"));
    TestConsumer::shared_ptr c2(new TestConsumer("C2"));

    queue->consume(c1);
    queue->consume(c2);

    std::deque<QueueCursor> dequeMeC1;
    std::deque<QueueCursor> dequeMeC2;


    verifyAcquire(queue, c1, dequeMeC1, "a", 0 );  // c1 now owns group "a" (acquire a-0)
    verifyAcquire(queue, c2, dequeMeC2, "b", 3 );  // c2 should now own group "b" (acquire b-3)

    // now let c1 complete the 'a-0' message - this should free the 'a' group
    queue->dequeue( 0, dequeMeC1.front() );
    dequeMeC1.pop_front();

    // Queue = a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
    // Owners= ---, ---, ^C2, ^C2, ^C2, ---, ---, ---

    // now c2 should pick up the next 'a-1', since it is oldest free
    verifyAcquire(queue, c2, dequeMeC2, "a", 1 ); // c2 should now own groups "a" and "b"

    // Queue = a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
    // Owners= ^C2, ^C2, ^C2, ^C2, ^C2, ---, ---, ---

    // c1 should only be able to snarf up the first "c" message now...
    verifyAcquire(queue, c1, dequeMeC1, "c", 6 );    // should skip to the first "c"

    // Queue = a-1, a-2, b-3, b-4, b-5, c-6, c-7, c-8...
    // Owners= ^C2, ^C2, ^C2, ^C2, ^C2, ^C1, ^C1, ^C1

    // hmmm... what if c2 now dequeues "b-3"?  (now only has a-1 acquired)
    queue->dequeue( 0, dequeMeC2.front() );
    dequeMeC2.pop_front();

    // Queue = a-1, a-2, b-4, b-5, c-6, c-7, c-8...
    // Owners= ^C2, ^C2, ---, ---, ^C1, ^C1, ^C1

    // b group is free, c is owned by c1 - c1's next get should grab 'b-4'
    verifyAcquire(queue, c1, dequeMeC1, "b", 4 );

    // Queue = a-1, a-2, b-4, b-5, c-6, c-7, c-8...
    // Owners= ^C2, ^C2, ^C1, ^C1, ^C1, ^C1, ^C1

    // c2 can now only grab a-2, and that's all
    verifyAcquire(queue, c2, dequeMeC2, "a", 2 );

    // now C2 can't get any more, since C1 owns "b" and "c" group...
    bool gotOne = queue->dispatch(c2);
    BOOST_CHECK( !gotOne );

    // hmmm... what if c1 now dequeues "c-6"?  (now only own's b-4)
    queue->dequeue( 0, dequeMeC1.front() );
    dequeMeC1.pop_front();

    // Queue = a-1, a-2, b-4, b-5, c-7, c-8...
    // Owners= ^C2, ^C2, ^C1, ^C1, ---, ---

    // c2 can now grab c-7
    verifyAcquire(queue, c2, dequeMeC2, "c", 7 );

    // Queue = a-1, a-2, b-4, b-5, c-7, c-8...
    // Owners= ^C2, ^C2, ^C1, ^C1, ^C2, ^C2

    // what happens if C-2 "requeues" a-1 and a-2?
    queue->release( dequeMeC2.front() );
    dequeMeC2.pop_front();
    queue->release( dequeMeC2.front() );
    dequeMeC2.pop_front();  // now just has c-7 acquired

    // Queue = a-1, a-2, b-4, b-5, c-7, c-8...
    // Owners= ---, ---, ^C1, ^C1, ^C2, ^C2

    // now c1 will grab a-1 and a-2...
    verifyAcquire(queue, c1, dequeMeC1, "a", 1 );
    verifyAcquire(queue, c1, dequeMeC1, "a", 2 );

    // Queue = a-1, a-2, b-4, b-5, c-7, c-8...
    // Owners= ^C1, ^C1, ^C1, ^C1, ^C2, ^C2

    // c2 can now acquire c-8 only
    verifyAcquire(queue, c2, dequeMeC2, "c", 8 );

    // and c1 can get b-5
    verifyAcquire(queue, c1, dequeMeC1, "b", 5 );

    // should be no more acquire-able for anyone now:
    gotOne = queue->dispatch(c1);
    BOOST_CHECK( !gotOne );
    gotOne = queue->dispatch(c2);
    BOOST_CHECK( !gotOne );

    // release all of C1's acquired messages, then cancel C1
    while (!dequeMeC1.empty()) {
        queue->release(dequeMeC1.front());
        dequeMeC1.pop_front();
    }
    queue->cancel(c1);

    // Queue = a-1, a-2, b-4, b-5, c-7, c-8...
    // Owners= ---, ---, ---, ---, ^C2, ^C2

    // b-4, a-1, a-2, b-5 all should be available, right?
    verifyAcquire(queue, c2, dequeMeC2, "a", 1 );

    while (!dequeMeC2.empty()) {
        queue->dequeue(0, dequeMeC2.front());
        dequeMeC2.pop_front();
    }

    // Queue = a-2, b-4, b-5
    // Owners= ---, ---, ---

    TestConsumer::shared_ptr c3(new TestConsumer("C3"));
    queue->consume(c3);
    std::deque<QueueCursor> dequeMeC3;

    verifyAcquire(queue, c3, dequeMeC3, "a", 2 );
    verifyAcquire(queue, c2, dequeMeC2, "b", 4 );

    // Queue = a-2, b-4, b-5
    // Owners= ^C3, ^C2, ^C2

    gotOne = queue->dispatch(c3);
    BOOST_CHECK( !gotOne );

    verifyAcquire(queue, c2, dequeMeC2, "b", 5 );

    while (!dequeMeC2.empty()) {
        queue->dequeue(0, dequeMeC2.front());
        dequeMeC2.pop_front();
    }

    // Queue = a-2,
    // Owners= ^C3,
    queue->deliver(createGroupMessage(9, "a"));

    // Queue = a-2, a-9
    // Owners= ^C3, ^C3

    gotOne = queue->dispatch(c2);
    BOOST_CHECK( !gotOne );

    queue->deliver(createGroupMessage(10, "b"));

    // Queue = a-2, a-9, b-10
    // Owners= ^C3, ^C3, ----

    verifyAcquire(queue, c2, dequeMeC2, "b", 10 );
    verifyAcquire(queue, c3, dequeMeC3, "a", 9 );

    gotOne = queue->dispatch(c3);
    BOOST_CHECK( !gotOne );

    queue->cancel(c2);
    queue->cancel(c3);
}


QPID_AUTO_TEST_CASE(testGroupsMultiConsumerDefaults) {
    //
    // Verify that the same default group name is automatically applied to messages that
    // do not specify a group name.
    //
    QueueSettings settings;
    settings.shareGroups = 1;
    settings.groupKey = "GROUP-ID";
    QueueFactory factory;
    Queue::shared_ptr queue(factory.create("my_queue", settings));

    for (int i = 0; i < 3; ++i) {
        qpid::types::Variant::Map properties;
        // no "GROUP-ID" header
        properties["MY-ID"] = i;
        queue->deliver(MessageUtils::createMessage(properties));
    }

    // Queue = 0, 1, 2

    BOOST_CHECK_EQUAL(uint32_t(3), queue->getMessageCount());

    TestConsumer::shared_ptr c1(new TestConsumer("C1"));
    TestConsumer::shared_ptr c2(new TestConsumer("C2"));

    queue->consume(c1);
    queue->consume(c2);

    std::deque<QueueCursor> dequeMeC1;
    std::deque<QueueCursor> dequeMeC2;

    queue->dispatch(c1);    // c1 now owns default group (acquired 0)
    dequeMeC1.push_back(c1->lastCursor);
    int id = getIntProperty(c1->lastMessage, "MY-ID");
    BOOST_CHECK_EQUAL( id, 0 );

    bool gotOne = queue->dispatch(c2);  // c2 should get nothing
    BOOST_CHECK( !gotOne );

    queue->dispatch(c1);    // c1 now acquires 1
    dequeMeC1.push_back(c1->lastCursor);
    id = getIntProperty(c1->lastMessage, "MY-ID");
    BOOST_CHECK_EQUAL( id, 1 );

    gotOne = queue->dispatch(c2);  // c2 should still get nothing
    BOOST_CHECK( !gotOne );

    while (!dequeMeC1.empty()) {
        queue->dequeue(0, dequeMeC1.front());
        dequeMeC1.pop_front();
    }

    // now default group should be available...
    queue->dispatch(c2);    // c2 now owns default group (acquired 2)
    id = c2->lastMessage.getProperty("MY-ID");
    BOOST_CHECK_EQUAL( id, 2 );

    gotOne = queue->dispatch(c1);  // c1 should get nothing
    BOOST_CHECK( !gotOne );

    queue->cancel(c1);
    queue->cancel(c2);
}

QPID_AUTO_TEST_CASE(testSetPositionFifo) {
    Queue::shared_ptr q(new Queue("my-queue", true));
    BOOST_CHECK_EQUAL(q->getPosition(), SequenceNumber(0));
    for (int i = 0; i < 10; ++i)
        q->deliver(MessageUtils::createMessage(qpid::types::Variant::Map(), boost::lexical_cast<string>(i+1)));

    // Verify the front of the queue
    TestConsumer::shared_ptr c(new TestConsumer("test", false)); // Don't acquire
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(1u, c->lastMessage.getSequence()); // Numbered from 1
    BOOST_CHECK_EQUAL("1", c->lastMessage.getContent());

    // Verify the back of the queue
    BOOST_CHECK_EQUAL(10u, q->getPosition());
    BOOST_CHECK_EQUAL(10u, q->getMessageCount());

    // Using setPosition to introduce a gap in sequence numbers.
    q->setPosition(15);
    BOOST_CHECK_EQUAL(10u, q->getMessageCount());
    BOOST_CHECK_EQUAL(15u, q->getPosition());
    q->deliver(MessageUtils::createMessage(qpid::types::Variant::Map(), "16"));

    q->seek(*c, Queue::MessagePredicate(), 9);
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(10u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("10", c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(16u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("16", c->lastMessage.getContent());

    // Using setPosition to trunkcate the queue
    q->setPosition(5);
    BOOST_CHECK_EQUAL(5u, q->getMessageCount());
    q->deliver(MessageUtils::createMessage(qpid::types::Variant::Map(), "6a"));
    c = boost::shared_ptr<TestConsumer>(new TestConsumer("test", false)); // Don't acquire
    q->seek(*c, Queue::MessagePredicate(), 4);
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(5u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("5", c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(6u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("6a", c->lastMessage.getContent());
    BOOST_CHECK(!q->dispatch(c)); // No more messages.
}

QPID_AUTO_TEST_CASE(testSetPositionLvq) {
    QueueSettings settings;
    string key="key";
    settings.lvqKey = key;
    QueueFactory factory;
    Queue::shared_ptr q(factory.create("my-queue", settings));

    const char* values[] = { "a", "b", "c", "a", "b", "c" };
    for (size_t i = 0; i < sizeof(values)/sizeof(values[0]); ++i) {
        qpid::types::Variant::Map properties;
        properties[key] = values[i];
        q->deliver(MessageUtils::createMessage(properties, boost::lexical_cast<string>(i+1)));
    }
    BOOST_CHECK_EQUAL(3u, q->getMessageCount());
    // Verify the front of the queue
    TestConsumer::shared_ptr c(new TestConsumer("test", false)); // Don't acquire
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(4u, c->lastMessage.getSequence()); // Numbered from 1
    BOOST_CHECK_EQUAL("4", c->lastMessage.getContent());
    // Verify the back of the queue
    BOOST_CHECK_EQUAL(6u, q->getPosition());

    q->setPosition(5);

    c = boost::shared_ptr<TestConsumer>(new TestConsumer("test", false)); // Don't acquire
    q->seek(*c, Queue::MessagePredicate(), 4);
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(5u, c->lastMessage.getSequence()); // Numbered from 1
    BOOST_CHECK(!q->dispatch(c));
}

QPID_AUTO_TEST_CASE(testSetPositionPriority) {
    QueueSettings settings;
    settings.priorities = 10;
    QueueFactory factory;
    Queue::shared_ptr q(factory.create("my-queue", settings));

    const int priorities[] = { 1, 2, 3, 2, 1, 3 };
    for (size_t i = 0; i < sizeof(priorities)/sizeof(priorities[0]); ++i) {
        qpid::types::Variant::Map properties;
        properties["priority"] = priorities[i];
        q->deliver(MessageUtils::createMessage(properties, boost::lexical_cast<string>(i+1)));
    }

    // Truncation removes messages in fifo order, not priority order.
    q->setPosition(3);
    TestConsumer::shared_ptr c(new TestConsumer("test", false)); // Browse in priority order
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(3u, c->lastMessage.getSequence());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(2u, c->lastMessage.getSequence());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(1u, c->lastMessage.getSequence());
    BOOST_CHECK(!q->dispatch(c));

    qpid::types::Variant::Map properties;
    properties["priority"] = 4;
    q->deliver(MessageUtils::createMessage(properties, "4a"));

    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(4u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("4a", c->lastMessage.getContent());

    // But consumers see priority order
    c.reset(new TestConsumer("test", true));
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(4u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("4a", c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(3u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("3", c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(2u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("2", c->lastMessage.getContent());
    BOOST_CHECK(q->dispatch(c));
    BOOST_CHECK_EQUAL(1u, c->lastMessage.getSequence());
    BOOST_CHECK_EQUAL("1", c->lastMessage.getContent());
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
