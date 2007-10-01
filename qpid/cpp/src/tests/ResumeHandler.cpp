/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/framing/ResumeHandler.h"

#define BOOST_AUTO_TEST_MAIN
#include <boost/test/auto_unit_test.hpp>

#include <vector>

using namespace std;
using namespace qpid::framing;

AMQFrame& frame(const char* s) {
    static AMQFrame frame;
    frame.setBody(AMQContentBody(s));
    return frame;
}

struct Collector : public FrameHandler, public vector<AMQFrame> {
    void handle(AMQFrame& f) { push_back(f); }
};


namespace qpid {
namespace framing {

bool operator==(const AMQFrame& a, const AMQFrame& b) {
    const AMQContentBody* ab=dynamic_cast<const AMQContentBody*>(a.getBody());
    const AMQContentBody* bb=dynamic_cast<const AMQContentBody*>(b.getBody());
    return ab && bb && ab->getData() == bb->getData();
}

}} // namespace qpid::framing


BOOST_AUTO_TEST_CASE(testSend) {
    AMQFrame f;
    ResumeHandler sender;
    Collector collect;
    sender.out.next = &collect;
    sender.out(frame("a"));
    BOOST_CHECK_EQUAL(1u, collect.size());
    BOOST_CHECK_EQUAL(frame("a"), collect[0]);
    sender.out(frame("b"));
    sender.out(frame("c"));
    sender.ackReceived(1);      // ack a,b.
    sender.out(frame("d"));
    BOOST_CHECK_EQUAL(4u, collect.size());
    BOOST_CHECK_EQUAL(frame("d"), collect.back());
    // Now try a resend.
    collect.clear();
    sender.resend();
    BOOST_REQUIRE_EQUAL(collect.size(), 2u);
    BOOST_CHECK_EQUAL(frame("c"), collect[0]);
    BOOST_CHECK_EQUAL(frame("d"), collect[1]);
}


BOOST_AUTO_TEST_CASE(testReceive) {
    ResumeHandler receiver;
    Collector collect;
    receiver.in.next = &collect;
    receiver.in(frame("a"));
    receiver.in(frame("b"));
    BOOST_CHECK_EQUAL(receiver.getLastReceived().getValue(), 1u);
    receiver.in(frame("c"));
    BOOST_CHECK_EQUAL(receiver.getLastReceived().getValue(), 2u);
    BOOST_CHECK_EQUAL(3u, collect.size());
    BOOST_CHECK_EQUAL(frame("a"), collect[0]);
    BOOST_CHECK_EQUAL(frame("c"), collect[2]);
}
