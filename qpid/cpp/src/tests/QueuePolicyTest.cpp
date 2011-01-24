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
#include <sstream>
#include "unit_test.h"
#include "test_tools.h"

#include "qpid/broker/QueuePolicy.h"
#include "qpid/client/QueueOptions.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/reply_exceptions.h"
#include "MessageUtils.h"
#include "BrokerFixture.h"

using namespace qpid::broker;
using namespace qpid::client;
using namespace qpid::framing;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(QueuePolicyTestSuite)

QueuedMessage createMessage(uint32_t size)
{
    QueuedMessage msg;
    msg.payload = MessageUtils::createMessage();
    MessageUtils::addContent(msg.payload, std::string (size, 'x'));
    return msg;
}


QPID_AUTO_TEST_CASE(testCount)
{
    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", 5, 0));
    BOOST_CHECK_EQUAL((uint64_t) 0, policy->getMaxSize());
    BOOST_CHECK_EQUAL((uint32_t) 5, policy->getMaxCount());

    BOOST_CHECK(!policy->monitorFlowControl());

    QueuedMessage msg = createMessage(10);
    for (size_t i = 0; i < 5; i++) {
        policy->tryEnqueue(msg.payload);
    }
    try {
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on enqueuing sixth message");
    } catch (const ResourceLimitExceededException&) {}

    policy->dequeued(msg);
    policy->tryEnqueue(msg.payload);

    try {
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on enqueuing sixth message (after dequeue)");
    } catch (const ResourceLimitExceededException&) {}
}

QPID_AUTO_TEST_CASE(testSize)
{
    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", 0, 50));
    QueuedMessage msg = createMessage(10);

    for (size_t i = 0; i < 5; i++) {
        policy->tryEnqueue(msg.payload);
    }
    try {
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on aggregate size exceeding 50. " << *policy);
    } catch (const ResourceLimitExceededException&) {}

    policy->dequeued(msg);
    policy->tryEnqueue(msg.payload);

    try {
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on aggregate size exceeding 50 (after dequeue). " << *policy);
    } catch (const ResourceLimitExceededException&) {}
}

QPID_AUTO_TEST_CASE(testBoth)
{
    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", 5, 50));
    try {
        QueuedMessage msg = createMessage(51);
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on single message exceeding 50. " << *policy);
    } catch (const ResourceLimitExceededException&) {}

    std::vector<QueuedMessage> messages;
    messages.push_back(createMessage(15));
    messages.push_back(createMessage(10));
    messages.push_back(createMessage(11));
    messages.push_back(createMessage(2));
    messages.push_back(createMessage(7));
    for (size_t i = 0; i < messages.size(); i++) {
        policy->tryEnqueue(messages[i].payload);
    }
    //size = 45 at this point, count = 5
    try {
        QueuedMessage msg = createMessage(5);
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on count exceeding 6. " << *policy);
    } catch (const ResourceLimitExceededException&) {}
    try {
        QueuedMessage msg = createMessage(10);
        policy->tryEnqueue(msg.payload);
        BOOST_FAIL("Policy did not fail on aggregate size exceeding 50. " << *policy);
    } catch (const ResourceLimitExceededException&) {}


    policy->dequeued(messages[0]);
    try {
        QueuedMessage msg = createMessage(20);
        policy->tryEnqueue(msg.payload);
    } catch (const ResourceLimitExceededException&) {
        BOOST_FAIL("Policy failed incorrectly after dequeue. " << *policy);
    }
}

QPID_AUTO_TEST_CASE(testSettings)
{
    //test reading and writing the policy from/to field table
    std::auto_ptr<QueuePolicy> a(QueuePolicy::createQueuePolicy("test", 101, 303));
    FieldTable settings;
    a->update(settings);
    std::auto_ptr<QueuePolicy> b(QueuePolicy::createQueuePolicy("test", settings));
    BOOST_CHECK_EQUAL(a->getMaxCount(), b->getMaxCount());
    BOOST_CHECK_EQUAL(a->getMaxSize(), b->getMaxSize());
}

QPID_AUTO_TEST_CASE(testRingPolicyCount)
{
    FieldTable args;
    std::auto_ptr<QueuePolicy> policy = QueuePolicy::createQueuePolicy("test", 5, 0, QueuePolicy::RING);
    policy->update(args);

    ProxySessionFixture f;
    std::string q("my-ring-queue");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    for (int i = 0; i < 10; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    client::Message msg;
    for (int i = 5; i < 10; i++) {
        BOOST_CHECK(f.subs.get(msg, q, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL((boost::format("%1%_%2%") % "Message" % (i+1)).str(), msg.getData());
    }
    BOOST_CHECK(!f.subs.get(msg, q));

    for (int i = 10; i < 20; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    for (int i = 15; i < 20; i++) {
        BOOST_CHECK(f.subs.get(msg, q, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL((boost::format("%1%_%2%") % "Message" % (i+1)).str(), msg.getData());
    }
    BOOST_CHECK(!f.subs.get(msg, q));
}

QPID_AUTO_TEST_CASE(testRingPolicySize)
{
    std::string hundredBytes = std::string(100, 'h');
    std::string fourHundredBytes = std::string (400, 'f');
    std::string thousandBytes = std::string(1000, 't');

    // Ring queue, 500 bytes maxSize

    FieldTable args;
    std::auto_ptr<QueuePolicy> policy = QueuePolicy::createQueuePolicy("test", 0, 500, QueuePolicy::RING);
    policy->update(args);

    ProxySessionFixture f;
    std::string q("my-ring-queue");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);

    // A. Send messages 0 .. 5, each 100 bytes

    client::Message m(hundredBytes, q); 
    
    for (int i = 0; i < 6; i++) {
        std::stringstream id;
        id << i;        
        m.getMessageProperties().setCorrelationId(id.str());
        f.session.messageTransfer(arg::content=m);
    }

    // should find 1 .. 5 on the queue, 0 is displaced by 5
    client::Message msg;
    for (int i = 1; i < 6; i++) {
        std::stringstream id;
        id << i;        
        BOOST_CHECK(f.subs.get(msg, q, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL(msg.getMessageProperties().getCorrelationId(), id.str());
    }
    BOOST_CHECK(!f.subs.get(msg, q));

    // B. Now make sure that one 400 byte message displaces four 100 byte messages

    // Send messages 0 .. 5, each 100 bytes
    for (int i = 0; i < 6; i++) {
        client::Message m(hundredBytes, q);
        std::stringstream id;
        id << i;        
        m.getMessageProperties().setCorrelationId(id.str());
        f.session.messageTransfer(arg::content=m);
    }

    // Now send one 400 byte message
    client::Message m2(fourHundredBytes, q);
    m2.getMessageProperties().setCorrelationId("6");
    f.session.messageTransfer(arg::content=m2);

    // expect to see 5, 6 on the queue
    for (int i = 5; i < 7; i++) {
        std::stringstream id;
        id << i;        
        BOOST_CHECK(f.subs.get(msg, q, qpid::sys::TIME_SEC));
        BOOST_CHECK_EQUAL(msg.getMessageProperties().getCorrelationId(), id.str());
    }
    BOOST_CHECK(!f.subs.get(msg, q));


    // C. Try sending a 1000-byte message, should fail - exceeds maxSize of queue

    client::Message m3(thousandBytes, q);
    m3.getMessageProperties().setCorrelationId("6");
    try {
        ScopedSuppressLogging sl;
        f.session.messageTransfer(arg::content=m3);
        BOOST_FAIL("Ooops - successfully added a 1000 byte message to a 512 byte ring queue ..."); 
    }
    catch (...) {
    }
            
}


QPID_AUTO_TEST_CASE(testStrictRingPolicy)
{
    FieldTable args;
    std::auto_ptr<QueuePolicy> policy = QueuePolicy::createQueuePolicy("test", 5, 0, QueuePolicy::RING_STRICT);
    policy->update(args);

    ProxySessionFixture f;
    std::string q("my-ring-queue");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    LocalQueue incoming;
    SubscriptionSettings settings(FlowControl::unlimited());
    settings.autoAck = 0; // no auto ack.
    Subscription sub = f.subs.subscribe(incoming, q, settings);
    for (int i = 0; i < 5; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    for (int i = 0; i < 5; i++) {
        BOOST_CHECK_EQUAL(incoming.pop().getData(), (boost::format("%1%_%2%") % "Message" % (i+1)).str());
    }
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected errors.
        f.session.messageTransfer(arg::content=client::Message("Message_6", q));
        BOOST_FAIL("expecting ResourceLimitExceededException.");
    } catch (const ResourceLimitExceededException&) {}
}

QPID_AUTO_TEST_CASE(testPolicyWithDtx)
{
    FieldTable args;
    std::auto_ptr<QueuePolicy> policy = QueuePolicy::createQueuePolicy("test", 5, 0, QueuePolicy::REJECT);
    policy->update(args);

    ProxySessionFixture f;
    std::string q("my-policy-queue");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    LocalQueue incoming;
    SubscriptionSettings settings(FlowControl::unlimited());
    settings.autoAck = 0; // no auto ack.
    Subscription sub = f.subs.subscribe(incoming, q, settings);
    f.session.dtxSelect();
    Xid tx1(1, "test-dtx-mgr", "tx1");
    f.session.dtxStart(arg::xid=tx1);
    for (int i = 0; i < 5; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    f.session.dtxEnd(arg::xid=tx1);
    f.session.dtxCommit(arg::xid=tx1, arg::onePhase=true);

    Xid tx2(1, "test-dtx-mgr", "tx2");
    f.session.dtxStart(arg::xid=tx2);
    for (int i = 0; i < 5; i++) {
        BOOST_CHECK_EQUAL(incoming.pop().getData(), (boost::format("%1%_%2%") % "Message" % (i+1)).str());
    }
    SequenceSet accepting=sub.getUnaccepted();
    f.session.messageAccept(accepting);
    f.session.dtxEnd(arg::xid=tx2);
    f.session.dtxPrepare(arg::xid=tx2);
    f.session.dtxRollback(arg::xid=tx2);
    f.session.messageRelease(accepting);

    Xid tx3(1, "test-dtx-mgr", "tx3");
    f.session.dtxStart(arg::xid=tx3);
    for (int i = 0; i < 5; i++) {
        incoming.pop();
    }
    accepting=sub.getUnaccepted();
    f.session.messageAccept(accepting);
    f.session.dtxEnd(arg::xid=tx3);
    f.session.dtxPrepare(arg::xid=tx3);

    Session other = f.connection.newSession();
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected errors.
        other.messageTransfer(arg::content=client::Message("Message_6", q));
        BOOST_FAIL("expecting ResourceLimitExceededException.");
    } catch (const ResourceLimitExceededException&) {}

    f.session.dtxCommit(arg::xid=tx3);
    //now retry and this time should succeed
    other = f.connection.newSession();
    other.messageTransfer(arg::content=client::Message("Message_6", q));
}

QPID_AUTO_TEST_CASE(testFlowToDiskWithNoStore)
{
    //Ensure that with no store loaded, we don't flow to disk but
    //fallback to rejecting messages
    QueueOptions args;
    args.setSizePolicy(FLOW_TO_DISK, 0, 5);

    ProxySessionFixture f;
    std::string q("my-queue");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    LocalQueue incoming;
    SubscriptionSettings settings(FlowControl::unlimited());
    settings.autoAck = 0; // no auto ack.
    Subscription sub = f.subs.subscribe(incoming, q, settings);
    for (int i = 0; i < 5; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    for (int i = 0; i < 5; i++) {
        BOOST_CHECK_EQUAL(incoming.pop().getData(), (boost::format("%1%_%2%") % "Message" % (i+1)).str());
    }
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected errors.
        f.session.messageTransfer(arg::content=client::Message("Message_6", q));
        BOOST_FAIL("expecting ResourceLimitExceededException.");
    } catch (const ResourceLimitExceededException&) {}
}

QPID_AUTO_TEST_CASE(testPolicyFailureOnCommit)
{
    FieldTable args;
    std::auto_ptr<QueuePolicy> policy = QueuePolicy::createQueuePolicy("test", 5, 0, QueuePolicy::REJECT);
    policy->update(args);

    ProxySessionFixture f;
    std::string q("q");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    f.session.txSelect();
    for (int i = 0; i < 10; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    ScopedSuppressLogging sl; // Suppress messages for expected errors.
    BOOST_CHECK_THROW(f.session.txCommit(), InternalErrorException);
}

QPID_AUTO_TEST_CASE(testCapacityConversion)
{
    FieldTable args;
    args.setString("qpid.max_count", "5");

    ProxySessionFixture f;
    std::string q("q");
    f.session.queueDeclare(arg::queue=q, arg::exclusive=true, arg::autoDelete=true, arg::arguments=args);
    for (int i = 0; i < 5; i++) {
        f.session.messageTransfer(arg::content=client::Message((boost::format("%1%_%2%") % "Message" % (i+1)).str(), q));
    }
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected errors.
        f.session.messageTransfer(arg::content=client::Message("Message_6", q));
        BOOST_FAIL("expecting ResourceLimitExceededException.");
    } catch (const ResourceLimitExceededException&) {}
}


QPID_AUTO_TEST_CASE(testFlowCount)
{
    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", 10, 0, QueuePolicy::REJECT,
                                                                     7, // flowStop
                                                                     5));   // flowResume
    BOOST_CHECK_EQUAL((uint32_t) 7, policy->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 5, policy->getFlowResumeCount());
    BOOST_CHECK_EQUAL((uint32_t) 0, policy->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint32_t) 0, policy->getFlowResumeSize());
    BOOST_CHECK(!policy->isFlowControlActive());
    BOOST_CHECK(policy->monitorFlowControl());

    QueuedMessage msg = createMessage(10);
    for (size_t i = 0; i < 6; i++) {
        policy->tryEnqueue(msg.payload);
        BOOST_CHECK(!policy->isFlowControlActive());
    }
    BOOST_CHECK(!policy->isFlowControlActive());  // 6 on queue
    policy->tryEnqueue(msg.payload);
    BOOST_CHECK(!policy->isFlowControlActive());  // 7 on queue

    policy->tryEnqueue(msg.payload);
    BOOST_CHECK(policy->isFlowControlActive());   // 8 on queue, ON
    policy->tryEnqueue(msg.payload);
    BOOST_CHECK(policy->isFlowControlActive());   // 9 on queue

    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 8 on queue
    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 7 on queue
    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 6 on queue
    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 5 on queue

    policy->dequeued(msg);
    BOOST_CHECK(!policy->isFlowControlActive());  // 4 on queue, OFF
}


QPID_AUTO_TEST_CASE(testFlowSize)
{
    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", 10, 0, QueuePolicy::REJECT,
                                                                     0, 0,     // flow-Count
                                                                     70,    // flowStopSize
                                                                     50));  // flowResumeSize
    BOOST_CHECK_EQUAL((uint32_t) 0, policy->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 0, policy->getFlowResumeCount());
    BOOST_CHECK_EQUAL((uint32_t) 70, policy->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint32_t) 50, policy->getFlowResumeSize());
    BOOST_CHECK(!policy->isFlowControlActive());
    BOOST_CHECK(policy->monitorFlowControl());

    QueuedMessage msg = createMessage(10);
    for (size_t i = 0; i < 6; i++) {
        policy->tryEnqueue(msg.payload);
        BOOST_CHECK(!policy->isFlowControlActive());
    }
    BOOST_CHECK(!policy->isFlowControlActive());  // 60 on queue
    policy->tryEnqueue(msg.payload);
    BOOST_CHECK(!policy->isFlowControlActive());  // 70 on queue

    QueuedMessage tinyMsg = createMessage(1);
    policy->tryEnqueue(tinyMsg.payload);
    BOOST_CHECK(policy->isFlowControlActive());   // 71 on queue, ON
    policy->tryEnqueue(msg.payload);
    BOOST_CHECK(policy->isFlowControlActive());   // 81 on queue

    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 71 on queue
    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 61 on queue
    policy->dequeued(msg);
    BOOST_CHECK(policy->isFlowControlActive());   // 51 on queue

    policy->dequeued(tinyMsg);
    BOOST_CHECK(policy->isFlowControlActive());   // 50 on queue
    policy->dequeued(tinyMsg);
    BOOST_CHECK(!policy->isFlowControlActive());  // 49 on queue, OFF
    policy->dequeued(msg);
    BOOST_CHECK(!policy->isFlowControlActive());  // 39 on queue
}

QPID_AUTO_TEST_CASE(testFlowArgs)
{
    FieldTable args;
    const uint64_t stop(0x2FFFFFFFF);
    const uint64_t resume(0x1FFFFFFFF);
    args.setInt(QueuePolicy::flowStopCountKey, 30);
    args.setInt(QueuePolicy::flowResumeCountKey, 21);
    args.setUInt64(QueuePolicy::flowStopSizeKey, stop);
    args.setUInt64(QueuePolicy::flowResumeSizeKey, resume);
    args.setUInt64(QueuePolicy::maxSizeKey, stop + 1);      // needed to pass stop < max validation

    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", args));

    BOOST_CHECK_EQUAL((uint32_t) 30, policy->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 21, policy->getFlowResumeCount());
    BOOST_CHECK_EQUAL(stop, policy->getFlowStopSize());
    BOOST_CHECK_EQUAL(resume, policy->getFlowResumeSize());
    BOOST_CHECK(!policy->isFlowControlActive());
    BOOST_CHECK(policy->monitorFlowControl());
}


QPID_AUTO_TEST_CASE(testFlowCombo)
{
    FieldTable args;
    args.setInt(QueuePolicy::flowStopCountKey, 10);
    args.setInt(QueuePolicy::flowResumeCountKey, 5);
    args.setUInt64(QueuePolicy::flowStopSizeKey, 200);
    args.setUInt64(QueuePolicy::flowResumeSizeKey, 100);

    QueuedMessage msg_1 = createMessage(1);
    QueuedMessage msg_10 = createMessage(10);
    QueuedMessage msg_50 = createMessage(50);
    QueuedMessage msg_100 = createMessage(100);

    std::auto_ptr<QueuePolicy> policy(QueuePolicy::createQueuePolicy("test", args));
    BOOST_CHECK(!policy->isFlowControlActive());        // count:0  size:0

    // verify flow control comes ON when only count passes its stop point.

    for (size_t i = 0; i < 10; i++) {
        policy->tryEnqueue(msg_10.payload);
        BOOST_CHECK(!policy->isFlowControlActive());
    }
    // count:10 size:100

    policy->tryEnqueue(msg_1.payload);  // count:11 size: 101  ->ON
    BOOST_CHECK(policy->isFlowControlActive());

    for (size_t i = 0; i < 6; i++) {
        policy->dequeued(msg_10);
        BOOST_CHECK(policy->isFlowControlActive());
    }
    // count:5 size: 41

    policy->dequeued(msg_1);        // count: 4 size: 40  ->OFF
    BOOST_CHECK(!policy->isFlowControlActive());

    for (size_t i = 0; i < 4; i++) {
        policy->dequeued(msg_10);
        BOOST_CHECK(!policy->isFlowControlActive());
    }
    // count:0 size:0

    // verify flow control comes ON when only size passes its stop point.

    policy->tryEnqueue(msg_100.payload);  // count:1 size: 100
    BOOST_CHECK(!policy->isFlowControlActive());

    policy->tryEnqueue(msg_50.payload);   // count:2 size: 150
    BOOST_CHECK(!policy->isFlowControlActive());

    policy->tryEnqueue(msg_50.payload);   // count:3 size: 200
    BOOST_CHECK(!policy->isFlowControlActive());

    policy->tryEnqueue(msg_1.payload);   // count:4 size: 201  ->ON
    BOOST_CHECK(policy->isFlowControlActive());

    policy->dequeued(msg_100);              // count:3 size:101
    BOOST_CHECK(policy->isFlowControlActive());

    policy->dequeued(msg_1);                // count:2 size:100
    BOOST_CHECK(policy->isFlowControlActive());

    policy->dequeued(msg_50);               // count:1 size:50  ->OFF
    BOOST_CHECK(!policy->isFlowControlActive());

    // verify flow control remains ON until both thresholds drop below their
    // resume point.

    for (size_t i = 0; i < 8; i++) {
        policy->tryEnqueue(msg_10.payload);
        BOOST_CHECK(!policy->isFlowControlActive());
    }
    // count:9 size:130

    policy->tryEnqueue(msg_10.payload);   // count:10 size: 140
    BOOST_CHECK(!policy->isFlowControlActive());

    policy->tryEnqueue(msg_1.payload);   // count:11 size: 141  ->ON
    BOOST_CHECK(policy->isFlowControlActive());

    policy->tryEnqueue(msg_100.payload);   // count:12 size: 241  (both thresholds crossed)
    BOOST_CHECK(policy->isFlowControlActive());

    // at this point: 9@10 + 1@50 + 1@100 + 1@1 == 12@241

    policy->dequeued(msg_50);               // count:11 size:191
    BOOST_CHECK(policy->isFlowControlActive());

    for (size_t i = 0; i < 9; i++) {
        policy->dequeued(msg_10);
        BOOST_CHECK(policy->isFlowControlActive());
    }
    // count:2 size:101
    policy->dequeued(msg_1);                // count:1 size:100
    BOOST_CHECK(policy->isFlowControlActive());   // still active due to size

    policy->dequeued(msg_100);               // count:0 size:0  ->OFF
    BOOST_CHECK(!policy->isFlowControlActive());
}


QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
