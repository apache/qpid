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
#include "BrokerFixture.h"
#include "qpid/client/SubscriptionManager.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/framing/reply_exceptions.h"

QPID_AUTO_TEST_SUITE(exception_test)


using namespace std;
using namespace qpid;
using namespace sys;
using namespace client;
using namespace framing;

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
        try { f(); }
        catch(const Ex& e) {
            caught=true;
            BOOST_MESSAGE(string("Caught expected exception: ")+e.what());
        }
        catch(const std::exception& e) {
            BOOST_ERROR(string("Bad exception: ")+e.what());
        }
        catch(...) {
            BOOST_ERROR(string("Bad exception: unknown"));
        }
    }

    bool join() {
        if (thread.id()) {
            thread.join();
            thread=Thread();
        }
        return caught;
    }
};

BOOST_FIXTURE_TEST_CASE(DisconnectedPop, ProxySessionFixture) {
    ProxyConnection c(broker->getPort());
    session.queueDeclare(arg::queue="q");
    subs.subscribe(lq, "q");
    Catcher<ClosedException> pop(bind(&LocalQueue::pop, boost::ref(lq)));
    connection.proxy.close();
    BOOST_CHECK(pop.join());
}

BOOST_FIXTURE_TEST_CASE(DisconnectedListen, ProxySessionFixture) {
    struct NullListener : public MessageListener {
        void received(Message&) { BOOST_FAIL("Unexpected message"); }
    } l;
    ProxyConnection c(broker->getPort());
    session.queueDeclare(arg::queue="q");
    subs.subscribe(l, "q");
    Thread t(subs);
    connection.proxy.close();
    t.join();
    BOOST_CHECK_THROW(session.close(), InternalErrorException);    
}

BOOST_FIXTURE_TEST_CASE(NoSuchQueueTest, SessionFixture) {
    BOOST_CHECK_THROW(subs.subscribe(lq, "no such queue").sync(), NotFoundException);
}

QPID_AUTO_TEST_SUITE_END()
