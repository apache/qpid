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

#include "qpid/framing/SessionState.h"
#include "qpid/Exception.h"

#include <boost/bind.hpp>
#include "unit_test.h"

QPID_AUTO_TEST_SUITE(SessionStateTestSuite)

using namespace std;
using namespace qpid::framing;
using namespace boost;

// Create a frame with a one-char string.
AMQFrame& frame(char s) {
    static AMQFrame frame;
    frame.setBody(AMQContentBody(string(&s, 1)));
    return frame;
}

// Extract the one-char string from a frame.
char charFromFrame(const AMQFrame& f) {
    const AMQContentBody* b=dynamic_cast<const AMQContentBody*>(f.getBody());
    BOOST_REQUIRE(b && b->getData().size() > 0);
    return b->getData()[0];
}

// Sent chars as frames
void sent(SessionState& session, const std::string& frames) {
    for_each(frames.begin(), frames.end(),
             bind(&SessionState::sent, ref(session), bind(frame, _1)));
}

// Received chars as frames
void received(SessionState& session, const std::string& frames) {
    for_each(frames.begin(), frames.end(),
             bind(&SessionState::received, ref(session), bind(frame, _1)));
}

// Make a string from a ReplayRange.
std::string replayChars(const SessionState::Replay& frames) {
    string result(frames.size(), ' ');
    transform(frames.begin(), frames.end(), result.begin(),
              bind(&charFromFrame, _1));
    return result;
}

namespace qpid {
namespace framing {

bool operator==(const AMQFrame& a, const AMQFrame& b) {
    const AMQContentBody* ab=dynamic_cast<const AMQContentBody*>(a.getBody());
    const AMQContentBody* bb=dynamic_cast<const AMQContentBody*>(b.getBody());
    return ab && bb && ab->getData() == bb->getData();
}

}} // namespace qpid::framing


QPID_AUTO_TEST_CASE(testSent) {
    // Test that we send solicit-ack at the right interval.
    AMQContentBody f; 
    SessionState s1(1);
    BOOST_CHECK(s1.sent(f));
    BOOST_CHECK(s1.sent(f));
    BOOST_CHECK(s1.sent(f));
    
    SessionState s3(3);
    BOOST_CHECK(!s3.sent(f));
    BOOST_CHECK(!s3.sent(f));
    BOOST_CHECK(s3.sent(f));

    BOOST_CHECK(!s3.sent(f));
    BOOST_CHECK(!s3.sent(f));
    s3.receivedAck(4);
    BOOST_CHECK(!s3.sent(f));
    BOOST_CHECK(!s3.sent(f));
    BOOST_CHECK(s3.sent(f));
}

QPID_AUTO_TEST_CASE(testReplay) {
    // Replay of all frames.
    SessionState session(100);
    sent(session, "abc"); 
    session.suspend(); session.resuming();
    session.receivedAck(-1);
    BOOST_CHECK_EQUAL(replayChars(session.replay()), "abc");

    // Replay with acks
    session.receivedAck(0); // ack a.
    session.suspend();
    session.resuming();
    session.receivedAck(1); // ack b.
    BOOST_CHECK_EQUAL(replayChars(session.replay()), "c");

    // Replay after further frames.
    sent(session, "def");
    session.suspend();
    session.resuming();
    session.receivedAck(3);
    BOOST_CHECK_EQUAL(replayChars(session.replay()), "ef");

    // Bad ack, too high
    try {
        session.receivedAck(6);
        BOOST_FAIL("expected exception");
    } catch(const std::exception&) {}

}

QPID_AUTO_TEST_CASE(testReceived) {
    // Check that we request acks at the right interval.
    AMQContentBody f;
    SessionState s1(1);
    BOOST_CHECK_EQUAL(0u, *s1.received(f));
    BOOST_CHECK_EQUAL(1u, *s1.received(f));
    BOOST_CHECK_EQUAL(2u, *s1.received(f));

    SessionState s3(3);
    BOOST_CHECK(!s3.received(f));
    BOOST_CHECK(!s3.received(f));
    BOOST_CHECK_EQUAL(2u, *s3.received(f));

    BOOST_CHECK(!s3.received(f));
    BOOST_CHECK(!s3.received(f));
    BOOST_CHECK_EQUAL(5u, *s3.received(f));
}

QPID_AUTO_TEST_SUITE_END()
