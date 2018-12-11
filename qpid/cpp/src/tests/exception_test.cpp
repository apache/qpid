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
#include "BrokerFixture.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/client/MessageListener.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(exception_test)

using namespace std;
using namespace qpid;
using namespace sys;
using namespace client;
using namespace framing;

using qpid::broker::Broker;
using boost::bind;
using boost::function;

template <class Ex>
struct Catcher : public Runnable {
    function<void ()> f;
    bool caught;
    Thread thread;

    Catcher(function<void ()> f_) : f(f_), caught(false), thread(this) {}
    ~Catcher() { join(); }

    void run() {
        try {
            ScopedSuppressLogging sl; // Suppress messages for expected errors.
            f();
        }
        catch(const Ex& e) {
            caught=true;
            BOOST_MESSAGE(string("Caught expected exception: ")+e.what()+"["+typeid(e).name()+"]");
        }
        catch(const std::exception& e) {
            BOOST_ERROR(string("Bad exception: ")+e.what()+"["+typeid(e).name()+"] expected: "+typeid(Ex).name());
        }
        catch(...) {
            BOOST_ERROR(string("Bad exception: unknown"));
        }
    }

    bool join() {
        if (thread) {
            thread.join();
            thread=Thread();
        }
        return caught;
    }
};

QPID_AUTO_TEST_CASE(TestSessionBusy) {
    SessionFixture f;
    try {
        ScopedSuppressLogging sl; // Suppress messages for expected errors.
        f.connection.newSession(f.session.getId().getName());
        BOOST_FAIL("Expected SessionBusyException for " << f.session.getId().getName());
    } catch (const SessionBusyException&) {}
}

QPID_AUTO_TEST_CASE(DisconnectedPop) {
    SessionFixture fix;
    fix.session.queueDeclare(arg::queue="q");
    fix.subs.subscribe(fix.lq, "q");
    Catcher<TransportFailure> pop(bind(&LocalQueue::pop, &fix.lq, sys::TIME_SEC));
    fix.shutdownBroker();
    BOOST_CHECK(pop.join());
}

QPID_AUTO_TEST_CASE(DisconnectedListen) {
    SessionFixture fix;
    struct NullListener : public MessageListener {
        void received(Message&) { BOOST_FAIL("Unexpected message"); }
    } l;
    fix.session.queueDeclare(arg::queue="q");
    fix.subs.subscribe(l, "q");

    Catcher<TransportFailure> runner(bind(&SubscriptionManager::run, boost::ref(fix.subs)));
    fix.shutdownBroker();
    runner.join();
    BOOST_CHECK_THROW(fix.session.queueDeclare(arg::queue="x"), TransportFailure);
}

QPID_AUTO_TEST_CASE(NoSuchQueueTest) {
    SessionFixture fix;
    ScopedSuppressLogging sl; // Suppress messages for expected errors.
    BOOST_CHECK_THROW(fix.subs.subscribe(fix.lq, "no such queue"), NotFoundException);
}

QPID_AUTO_TEST_SUITE_END()

}} // namespace qpid::tests
