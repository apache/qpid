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

#include "qpid/broker/SuspendedSessions.h"

#define BOOST_AUTO_TEST_MAIN
#include <boost/test/auto_unit_test.hpp>

using namespace std;
using namespace qpid::framing;
using namespace qpid::broker;
using namespace qpid::sys;

BOOST_AUTO_TEST_CASE(testSuspendedSessions) {
    SuspendedSessions suspended;

    SessionState s;
    BOOST_CHECK_EQUAL(s.getState(), SessionState::CLOSED);
    s.open(0);
    BOOST_CHECK_EQUAL(s.getState(), SessionState::ACTIVE);
    BOOST_CHECK(!s.getId().empty());
    suspended.suspend(s);
    BOOST_CHECK(s.getState() == SessionState::CLOSED);
    try {
        s = suspended.resume(s.getId());
        BOOST_FAIL("Expected session to be timed out.");
    } catch (...) {}

    s.close();
    s.open(1);        // New session, 1 sec timeout.
    try {
        suspended.resume(s.getId());
        BOOST_FAIL("Expeced exception: non-existent session.");
    } catch (...) {}
    suspended.suspend(s);
    BOOST_CHECK(s.getState() == SessionState::SUSPENDED);
    s = suspended.resume(s.getId());
    BOOST_CHECK(s.getState() == SessionState::ACTIVE);

    suspended.suspend(s);       // Real timeout
    sleep(2);
    try {
        suspended.resume(s.getId());
        BOOST_FAIL("Expeced timeout.");
    } catch (...) {}
}


