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

#include "qpid/sys/Runnable.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Serializer.h"

#include <boost/bind.hpp>
#include <boost/utility/value_init.hpp>
#include "unit_test.h"

#include <set>

#include <unistd.h>

QPID_AUTO_TEST_SUITE(SerializerTestSuite)


using namespace qpid;
using namespace qpid::sys;
using namespace qpid::framing;
using namespace std;

typedef Serializer<boost::function<void()> > BoostFunctionSerializer;

/** Test for concurrent calls */
struct Tester {
    Monitor lock;
    size_t count;
    size_t collisions;
    set<long> threads;

    Tester() : count(0), collisions(0) {}
    
    void test() {
        if (lock.trylock()) {   // Check for concurrent calls.
            ++count;
            threads.insert(Thread::logId()); // Record thread.
            usleep(1000);           // Encourage overlap.
            lock.notify();
            lock.unlock();
        }
        else
            ++collisions;
    }
};

void execute(BoostFunctionSerializer& s, boost::function<void()> t) 
{
    s.execute(t);
}

QPID_AUTO_TEST_CASE(testSingleThread) {
    // Verify that we call in the same thread by default.
    Tester tester;
    BoostFunctionSerializer s;
    for (int i = 0; i < 100; ++i) 
        execute(s, boost::bind(&Tester::test, &tester));
    // All should be executed in this thread.
    BOOST_CHECK_EQUAL(0u, tester.collisions);
    BOOST_CHECK_EQUAL(100u, tester.count);
    BOOST_REQUIRE_EQUAL(1u, tester.threads.size());
    BOOST_CHECK_EQUAL(Thread::logId(), *tester.threads.begin());
}
    
        
QPID_AUTO_TEST_CASE(testSingleThreadNoImmediate) {
    // Verify that we call in different thread if immediate=false.
    Tester tester;
    BoostFunctionSerializer s(false);
    for (int i = 0; i < 100; ++i)
        execute(s, boost::bind(&Tester::test, &tester));
    {
        // Wait for dispatch thread to complete.
        Mutex::ScopedLock l(tester.lock);
        while (tester.count != 100)
            tester.lock.wait();
    }
    BOOST_CHECK_EQUAL(0u, tester.collisions);
    BOOST_CHECK_EQUAL(100u, tester.count);
    BOOST_REQUIRE_EQUAL(1u, tester.threads.size());
    BOOST_CHECK(Thread::logId() != *tester.threads.begin());
}

struct Caller : public Runnable, public Tester {
    Caller(BoostFunctionSerializer& s) : serializer(s) {}
    void run() { execute(serializer, boost::bind(&Tester::test, this)); }
    BoostFunctionSerializer& serializer;
};

QPID_AUTO_TEST_CASE(testDispatchThread) {
    BoostFunctionSerializer s;
    Caller caller(s);
    Thread threads[100];
    // Concurrent calls.
    for (size_t i = 0; i < 100; ++i)
        threads[i] = Thread(caller);
    for (size_t i = 0; i < 100; ++i)
        threads[i].join();

    // At least one task should have been queued.
    BOOST_CHECK_EQUAL(0u, caller.collisions);
    BOOST_CHECK(caller.threads.size() > 2u);
    BOOST_CHECK(caller.threads.size() < 100u);
}


std::auto_ptr<BoostFunctionSerializer> serializer;
    
struct CallDispatch : public Runnable {
    void run() {
        serializer->dispatch();
    }
};

void notifyDispatch() {
    static CallDispatch cd;
    Thread t(cd);
}

// Use externally created threads.
QPID_AUTO_TEST_CASE(testExternalDispatch) {
    serializer.reset(new BoostFunctionSerializer(false, &notifyDispatch));
    Tester tester;
    for (int i = 0; i < 100; ++i) 
        execute(*serializer, boost::bind(&Tester::test, &tester));
    {
        // Wait for dispatch thread to complete.
        Mutex::ScopedLock l(tester.lock);
        while (tester.count != 100)
            tester.lock.wait();
    }
    BOOST_CHECK_EQUAL(0u, tester.collisions);
    BOOST_CHECK_EQUAL(100u, tester.count);
    BOOST_CHECK(Thread::logId() != *tester.threads.begin());
}

QPID_AUTO_TEST_SUITE_END()
