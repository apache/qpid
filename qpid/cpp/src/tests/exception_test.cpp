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
#include "InProcessBroker.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/framing/reply_exceptions.h"

QPID_AUTO_TEST_SUITE(exception_test)


using namespace std;
using namespace qpid;
using namespace client;
using namespace framing;

struct Fixture {
    InProcessConnection connection;
    InProcessConnection connection2;
    Session_0_10 session;
    SubscriptionManager sub;
    LocalQueue q;

    Fixture() : connection(),
                connection2(connection.getBroker()),
                session(connection.newSession()),
                sub(session)
    {
        session.queueDeclare(arg::queue="q");
    }
};


// TODO aconway 2007-11-30: need InProcessBroker to be a more accurate
// simulation of shutdown behaviour. It should override only 
// Connector.run() to substitute NetworkQueues for the Dispatcher.
// 
// template <class Ex>
// struct Catcher : public sys::Runnable {
//     Session_0_10 s;
//     boost::function<void ()> f;
//     bool caught;
//     Catcher(Session_0_10 s_, boost::function<void ()> f_)
//         : s(s_), f(f_), caught(false) {}
//     void run() {
//         try { f(); } catch(const Ex& e) {
//             caught=true;
//             BOOST_MESSAGE(e.what());
//         }
//     }
// };

// BOOST_FIXTURE_TEST_CASE(DisconnectedGet, Fixture) {
//     Catcher<Exception> get(session, boost::bind(&Session_0_10::get, session));
//     sub.subscribe(q, "q");
//     sys::Thread t(get);
//     connection.disconnect();
//     t.join();
//     BOOST_CHECK(get.caught);
// }

// BOOST_FIXTURE_TEST_CASE(DisconnectedListen, Fixture) {
//     struct NullListener : public MessageListener {
//         void received(Message&) { BOOST_FAIL("Unexpected message"); }
//     } l;
//     sub.subscribe(l, "q");
//     connection.disconnect();
//     try {
//         sub.run();
//         BOOST_FAIL("Expected exception");
//     } catch (const Exception&e) { BOOST_FAIL(e.what()); }
//     try {
//         session.queueDeclare(arg::queue="foo");
//         BOOST_FAIL("Expected exception");
//     } catch (const Exception&e) { BOOST_FAIL(e.what()); }
// }

// TODO aconway 2007-11-30: setSynchronous appears not to work.
// BOOST_FIXTURE_TEST_CASE(NoSuchQueueTest, Fixture) {
//     session.setSynchronous(true);
//     BOOST_CHECK_THROW(sub.subscribe(q, "no such queue"), NotFoundException);
// }

QPID_AUTO_TEST_SUITE_END()
