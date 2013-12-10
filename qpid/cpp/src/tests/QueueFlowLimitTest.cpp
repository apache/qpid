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
#include <deque>
#include "unit_test.h"
#include "test_tools.h"

#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/sys/Time.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/FieldValue.h"
#include "MessageUtils.h"
#include "BrokerFixture.h"

using namespace qpid::broker;
using namespace qpid::framing;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(QueueFlowLimitTestSuite)

namespace {

class TestFlow : public QueueFlowLimit
{
public:
    TestFlow(uint32_t flowStopCount, uint32_t flowResumeCount,
             uint64_t flowStopSize, uint64_t flowResumeSize) :
        QueueFlowLimit("", flowStopCount, flowResumeCount, flowStopSize, flowResumeSize)
    {}
    virtual ~TestFlow() {}

    static TestFlow *createTestFlow(const qpid::framing::FieldTable& settings)
    {
        FieldTable::ValuePtr v;

        v = settings.get(flowStopCountKey);
        uint32_t flowStopCount = (v) ? (uint32_t)v->get<int64_t>() : 0;
        v = settings.get(flowResumeCountKey);
        uint32_t flowResumeCount = (v) ? (uint32_t)v->get<int64_t>() : 0;
        v = settings.get(flowStopSizeKey);
        uint64_t flowStopSize = (v) ? (uint64_t)v->get<int64_t>() : 0;
        v = settings.get(flowResumeSizeKey);
        uint64_t flowResumeSize = (v) ? (uint64_t)v->get<int64_t>() : 0;

        return new TestFlow(flowStopCount, flowResumeCount, flowStopSize, flowResumeSize);
    }

    static boost::shared_ptr<qpid::broker::QueueFlowLimit> getQueueFlowLimit(const qpid::framing::FieldTable& arguments)
    {
        QueueSettings settings;
        settings.populate(arguments, settings.storeSettings);
        return QueueFlowLimit::createLimit("", settings);
    }
};

Message createMessage(uint32_t size)
{
    static uint32_t seqNum;
    //Need to compute what data size is required to make a given
    //overall size (use one byte of content in test message to ensure
    //content frame is added)
    Message test = MessageUtils::createMessage(qpid::types::Variant::Map(), std::string("x"));
    size_t min = test.getMessageSize() - 1;
    if (min > size) throw qpid::Exception("Can't create message that small!");
    Message msg = MessageUtils::createMessage(qpid::types::Variant::Map(), std::string (size - min, 'x'));
    msg.setSequence(++seqNum);//this doesn't affect message size
    return msg;
}
}

QPID_AUTO_TEST_CASE(testFlowCount)
{
    FieldTable args;
    args.setInt(QueueFlowLimit::flowStopCountKey, 7);
    args.setInt(QueueFlowLimit::flowResumeCountKey, 5);

    std::auto_ptr<TestFlow> flow(TestFlow::createTestFlow(args));

    BOOST_CHECK_EQUAL((uint32_t) 7, flow->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 5, flow->getFlowResumeCount());
    BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowResumeSize());
    BOOST_CHECK(!flow->isFlowControlActive());
    BOOST_CHECK(flow->monitorFlowControl());

    std::deque<Message> msgs;
    for (size_t i = 0; i < 6; i++) {
        msgs.push_back(createMessage(100));
        flow->enqueued(msgs.back());
        BOOST_CHECK(!flow->isFlowControlActive());
    }
    BOOST_CHECK(!flow->isFlowControlActive());  // 6 on queue
    msgs.push_back(createMessage(100));
    flow->enqueued(msgs.back());
    BOOST_CHECK(!flow->isFlowControlActive());  // 7 on queue
    msgs.push_back(createMessage(100));
    flow->enqueued(msgs.back());
    BOOST_CHECK(flow->isFlowControlActive());   // 8 on queue, ON
    msgs.push_back(createMessage(100));
    flow->enqueued(msgs.back());
    BOOST_CHECK(flow->isFlowControlActive());   // 9 on queue, no change to flow control

    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 8 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 7 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 6 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 5 on queue, no change

    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());  // 4 on queue, OFF
}

QPID_AUTO_TEST_CASE(testFlowSize)
{
    FieldTable args;
    args.setUInt64(QueueFlowLimit::flowStopSizeKey, 700);
    args.setUInt64(QueueFlowLimit::flowResumeSizeKey, 460);

    std::auto_ptr<TestFlow> flow(TestFlow::createTestFlow(args));

    BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowResumeCount());
    BOOST_CHECK_EQUAL((uint32_t) 700, flow->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint32_t) 460, flow->getFlowResumeSize());
    BOOST_CHECK(!flow->isFlowControlActive());
    BOOST_CHECK(flow->monitorFlowControl());

    std::deque<Message> msgs;
    for (size_t i = 0; i < 6; i++) {
        msgs.push_back(createMessage(100));
        flow->enqueued(msgs.back());
        BOOST_CHECK(!flow->isFlowControlActive());
    }
    BOOST_CHECK(!flow->isFlowControlActive());  // 600 on queue
    BOOST_CHECK_EQUAL(6u, flow->getFlowCount());
    BOOST_CHECK_EQUAL(600u, flow->getFlowSize());

    Message msg_50 = createMessage(50);
    flow->enqueued(msg_50);
    BOOST_CHECK(!flow->isFlowControlActive());  // 650 on queue
    Message tinyMsg_1 = createMessage(40);
    flow->enqueued(tinyMsg_1);
    BOOST_CHECK(!flow->isFlowControlActive());   // 690 on queue

    Message tinyMsg_2 = createMessage(40);
    flow->enqueued(tinyMsg_2);
    BOOST_CHECK(flow->isFlowControlActive());   // 730 on queue, ON
    msgs.push_back(createMessage(100));
    flow->enqueued(msgs.back());
    BOOST_CHECK(flow->isFlowControlActive());   // 830 on queue
    BOOST_CHECK_EQUAL(10u, flow->getFlowCount());
    BOOST_CHECK_EQUAL(830u, flow->getFlowSize());

    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 730 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 630 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // 530 on queue

    flow->dequeued(tinyMsg_1);
    BOOST_CHECK(flow->isFlowControlActive());   // 490 on queue
    flow->dequeued(tinyMsg_2);
    BOOST_CHECK(!flow->isFlowControlActive());   // 450 on queue, OFF

    flow->dequeued(msg_50);
    BOOST_CHECK(!flow->isFlowControlActive());  // 400 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());  // 300 on queue
    flow->dequeued(msgs.front());
    msgs.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());  // 200 on queue
    BOOST_CHECK_EQUAL(2u, flow->getFlowCount());
    BOOST_CHECK_EQUAL(200u, flow->getFlowSize());
}

QPID_AUTO_TEST_CASE(testFlowArgs)
{
    FieldTable args;
    const uint64_t stop(0x2FFFFFFFFull);
    const uint64_t resume(0x1FFFFFFFFull);
    args.setInt(QueueFlowLimit::flowStopCountKey, 30);
    args.setInt(QueueFlowLimit::flowResumeCountKey, 21);
    args.setUInt64(QueueFlowLimit::flowStopSizeKey, stop);
    args.setUInt64(QueueFlowLimit::flowResumeSizeKey, resume);

    std::auto_ptr<TestFlow> flow(TestFlow::createTestFlow(args));

    BOOST_CHECK_EQUAL((uint32_t) 30, flow->getFlowStopCount());
    BOOST_CHECK_EQUAL((uint32_t) 21, flow->getFlowResumeCount());
    BOOST_CHECK_EQUAL(stop, flow->getFlowStopSize());
    BOOST_CHECK_EQUAL(resume, flow->getFlowResumeSize());
    BOOST_CHECK(!flow->isFlowControlActive());
    BOOST_CHECK(flow->monitorFlowControl());
}


QPID_AUTO_TEST_CASE(testFlowCombo)
{
    FieldTable args;
    args.setInt(QueueFlowLimit::flowStopCountKey, 10);
    args.setInt(QueueFlowLimit::flowResumeCountKey, 5);
    args.setUInt64(QueueFlowLimit::flowStopSizeKey, 2000);
    args.setUInt64(QueueFlowLimit::flowResumeSizeKey, 1000);

    std::deque<Message> msgs_50;
    std::deque<Message> msgs_100;
    std::deque<Message> msgs_500;
    std::deque<Message> msgs_1000;

    Message msg;

    std::auto_ptr<TestFlow> flow(TestFlow::createTestFlow(args));
    BOOST_CHECK(!flow->isFlowControlActive());        // count:0  size:0

    // verify flow control comes ON when only count passes its stop point.

    for (size_t i = 0; i < 10; i++) {
        msgs_100.push_back(createMessage(100));
        flow->enqueued(msgs_100.back());
        BOOST_CHECK(!flow->isFlowControlActive());
    }
    // count:10 size:1000

    msgs_50.push_back(createMessage(50));
    flow->enqueued(msgs_50.back());  // count:11 size: 1050  ->ON
    BOOST_CHECK(flow->isFlowControlActive());

    for (size_t i = 0; i < 6; i++) {
        flow->dequeued(msgs_100.front());
        msgs_100.pop_front();
        BOOST_CHECK(flow->isFlowControlActive());
    }
    // count:5 size: 450

    flow->dequeued(msgs_50.front());        // count: 4 size: 400  ->OFF
    msgs_50.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());

    for (size_t i = 0; i < 4; i++) {
        flow->dequeued(msgs_100.front());
        msgs_100.pop_front();
        BOOST_CHECK(!flow->isFlowControlActive());
    }
    // count:0 size:0

    // verify flow control comes ON when only size passes its stop point.

    msgs_1000.push_back(createMessage(1000));
    flow->enqueued(msgs_1000.back());  // count:1 size: 1000
    BOOST_CHECK(!flow->isFlowControlActive());

    msgs_500.push_back(createMessage(500));
    flow->enqueued(msgs_500.back());   // count:2 size: 1500
    BOOST_CHECK(!flow->isFlowControlActive());

    msgs_500.push_back(createMessage(500));
    flow->enqueued(msgs_500.back());   // count:3 size: 2000
    BOOST_CHECK(!flow->isFlowControlActive());

    msgs_50.push_back(createMessage(50));
    flow->enqueued(msgs_50.back());   // count:4 size: 2050  ->ON
    BOOST_CHECK(flow->isFlowControlActive());

    flow->dequeued(msgs_1000.front());              // count:3 size:1050
    msgs_1000.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());

    flow->dequeued(msgs_50.front());                // count:2 size:1000
    msgs_50.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());

    flow->dequeued(msgs_500.front());               // count:1 size:500  ->OFF
    msgs_500.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());

    // verify flow control remains ON until both thresholds drop below their
    // resume point.

    for (size_t i = 0; i < 8; i++) {
        msgs_100.push_back(createMessage(100));
        flow->enqueued(msgs_100.back());
        BOOST_CHECK(!flow->isFlowControlActive());
    }
    // count:9 size:1300

    msgs_100.push_back(createMessage(100));
    flow->enqueued(msgs_100.back());              // count:10 size: 1400
    BOOST_CHECK(!flow->isFlowControlActive());

    msgs_50.push_back(createMessage(50));
    flow->enqueued(msgs_50.back());               // count:11 size: 1450  ->ON
    BOOST_CHECK(flow->isFlowControlActive());

    msgs_1000.push_back(createMessage(1000));
    flow->enqueued(msgs_1000.back());     // count:12 size: 2450  (both thresholds crossed)
    BOOST_CHECK(flow->isFlowControlActive());

    // at this point: 9@100 + 1@500 + 1@1000 + 1@50 == 12@2450

    flow->dequeued(msgs_500.front());               // count:11 size:1950
    msgs_500.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());

    for (size_t i = 0; i < 9; i++) {
        flow->dequeued(msgs_100.front());
        msgs_100.pop_front();
        BOOST_CHECK(flow->isFlowControlActive());
    }
    // count:2 size:1050
    flow->dequeued(msgs_50.front());                // count:1 size:1000
    msgs_50.pop_front();
    BOOST_CHECK(flow->isFlowControlActive());   // still active due to size

    flow->dequeued(msgs_1000.front());               // count:0 size:0  ->OFF
    msgs_1000.pop_front();
    BOOST_CHECK(!flow->isFlowControlActive());
}


QPID_AUTO_TEST_CASE(testFlowDefaultArgs)
{
    QueueFlowLimit::setDefaults(2950001, // max queue byte count
                                80,     // 80% stop threshold
                                70);    // 70% resume threshold
    FieldTable args;
    boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
    BOOST_CHECK(flow);

    BOOST_CHECK_EQUAL((uint64_t) 2360001, flow->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint64_t) 2065000, flow->getFlowResumeSize());
    BOOST_CHECK_EQUAL( 0u, flow->getFlowStopCount());
    BOOST_CHECK_EQUAL( 0u, flow->getFlowResumeCount());
    BOOST_CHECK(!flow->isFlowControlActive());
    BOOST_CHECK(flow->monitorFlowControl());
}


QPID_AUTO_TEST_CASE(testFlowOverrideArgs)
{
    QueueFlowLimit::setDefaults(0, // max queue byte count
                                80,     // 80% stop threshold
                                70);    // 70% resume threshold
    {
        FieldTable args;
        args.setInt(QueueFlowLimit::flowStopCountKey, 35000);
        args.setInt(QueueFlowLimit::flowResumeCountKey, 30000);
//	args.setInt(QueueFlowLimit::flowStopSizeKey, 0);

        boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
        BOOST_CHECK(flow);

        BOOST_CHECK_EQUAL((uint32_t) 35000, flow->getFlowStopCount());
        BOOST_CHECK_EQUAL((uint32_t) 30000, flow->getFlowResumeCount());
        BOOST_CHECK_EQUAL((uint64_t) 0, flow->getFlowStopSize());
        BOOST_CHECK_EQUAL((uint64_t) 0, flow->getFlowResumeSize());
        BOOST_CHECK(!flow->isFlowControlActive());
        BOOST_CHECK(flow->monitorFlowControl());
    }
    {
        FieldTable args;
        args.setInt(QueueFlowLimit::flowStopSizeKey, 350000);
        args.setInt(QueueFlowLimit::flowResumeSizeKey, 300000);

        boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
        BOOST_CHECK(flow);

        BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowStopCount());
        BOOST_CHECK_EQUAL((uint32_t) 0, flow->getFlowResumeCount());
        BOOST_CHECK_EQUAL((uint64_t) 350000, flow->getFlowStopSize());
        BOOST_CHECK_EQUAL((uint64_t) 300000, flow->getFlowResumeSize());
        BOOST_CHECK(!flow->isFlowControlActive());
        BOOST_CHECK(flow->monitorFlowControl());
    }
    {
        FieldTable args;
        args.setInt(QueueFlowLimit::flowStopCountKey, 35000);
        args.setInt(QueueFlowLimit::flowResumeCountKey, 30000);
        args.setInt(QueueFlowLimit::flowStopSizeKey, 350000);
        args.setInt(QueueFlowLimit::flowResumeSizeKey, 300000);

        boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
        BOOST_CHECK(flow);

        BOOST_CHECK_EQUAL((uint32_t) 35000, flow->getFlowStopCount());
        BOOST_CHECK_EQUAL((uint32_t) 30000, flow->getFlowResumeCount());
        BOOST_CHECK_EQUAL((uint64_t) 350000, flow->getFlowStopSize());
        BOOST_CHECK_EQUAL((uint64_t) 300000, flow->getFlowResumeSize());
        BOOST_CHECK(!flow->isFlowControlActive());
        BOOST_CHECK(flow->monitorFlowControl());
    }
}


QPID_AUTO_TEST_CASE(testFlowOverrideDefaults)
{
    QueueFlowLimit::setDefaults(2950001, // max queue byte count
                                97,     // stop threshold
                                73);    // resume threshold
    FieldTable args;
    boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
    BOOST_CHECK(flow);

    BOOST_CHECK_EQUAL((uint32_t) 2861501, flow->getFlowStopSize());
    BOOST_CHECK_EQUAL((uint32_t) 2153500, flow->getFlowResumeSize());
    BOOST_CHECK(!flow->isFlowControlActive());
    BOOST_CHECK(flow->monitorFlowControl());
}


QPID_AUTO_TEST_CASE(testFlowDisable)
{
    {
        FieldTable args;
        args.setInt(QueueFlowLimit::flowStopCountKey, 0);
        args.setInt(QueueFlowLimit::flowStopSizeKey, 0);
        boost::shared_ptr<QueueFlowLimit> flow = TestFlow::getQueueFlowLimit(args);
        BOOST_CHECK(!flow);
    }
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
