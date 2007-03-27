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
#include <vector>
#include <iostream>

#include <boost/bind.hpp>

#include <qpid_test_plugin.h>
#include "InProcessBroker.h"
#include "sys/ProducerConsumer.h"
#include "sys/Thread.h"
#include "AMQP_HighestVersion.h"
#include "sys/AtomicCount.h"

using namespace qpid;
using namespace sys;
using namespace framing;
using namespace boost;
using namespace std;

/** A counter that notifies a monitor when changed */
class WatchedCounter : public Monitor {
  public:
    WatchedCounter(int i=0) : count(i) {}
    WatchedCounter(const WatchedCounter& c) : Monitor(), count(int(c)) {}

    WatchedCounter& operator=(const WatchedCounter& x) {
        return *this = int(x);
    }
    
    WatchedCounter& operator=(int i) {
        Lock l(*this);
        count = i;
        return *this;
    }

    int operator++() {
        Lock l(*this);
        notifyAll();
        return ++count;
    }

    int operator++(int) {
        Lock l(*this);
        notifyAll();
        return count++;
    }

    bool operator==(int i) const {
        Lock l(const_cast<WatchedCounter&>(*this));
        return i == count;
    }

    operator int() const {
        Lock l(const_cast<WatchedCounter&>(*this));
        return count;
    }

    bool waitFor(int i, Time timeout=TIME_SEC) {
        Lock l(*this);
        Time deadline = timeout+now();
        while (count != i) {
            if (!wait(deadline))
                return false;
        }
        assert(count == i);
        return true;
    }
    
  private:
    typedef Mutex::ScopedLock Lock;
    int count;
};
    
class ProducerConsumerTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ProducerConsumerTest);
    CPPUNIT_TEST(testProduceConsume);
    CPPUNIT_TEST(testTimeout);
    CPPUNIT_TEST(testShutdown);
    CPPUNIT_TEST(testCancel);
    CPPUNIT_TEST_SUITE_END();

  public:
    client::InProcessBrokerClient client;
    ProducerConsumer pc;

    WatchedCounter shutdown;
    WatchedCounter timeout;
    WatchedCounter consumed;
    WatchedCounter produced;

    struct ConsumeRunnable : public Runnable {
        ProducerConsumerTest& test;
        ConsumeRunnable(ProducerConsumerTest& test_) : test(test_) {}
        void run() { test.consume(); }
    };

    struct ConsumeTimeoutRunnable : public Runnable {
        ProducerConsumerTest& test;
        Time timeout;
        ConsumeTimeoutRunnable(ProducerConsumerTest& test_, const Time& t)
            : test(test_), timeout(t) {}
        void run() { test.consumeTimeout(timeout); }
    };


    void consumeInternal(ProducerConsumer::ConsumerLock& consumer) {
        if (pc.isShutdown()) {
            ++shutdown;
            return;
        }
        if (consumer.isTimedOut()) {
            ++timeout;
            return;
        }
        CPPUNIT_ASSERT(consumer.isOk());
        CPPUNIT_ASSERT(pc.available() > 0);
        consumer.confirm();
        consumed++;
    }
    
    void consume() {
        ProducerConsumer::ConsumerLock consumer(pc);
        consumeInternal(consumer);
    };

    void consumeTimeout(const Time& timeout) {
        ProducerConsumer::ConsumerLock consumer(pc, timeout);
        consumeInternal(consumer);
    };

    void produce() {
        ProducerConsumer::ProducerLock producer(pc);
        CPPUNIT_ASSERT(producer.isOk());
        producer.confirm();
        produced++;
    }

    void join(vector<Thread>& threads) {
        for_each(threads.begin(), threads.end(), bind(&Thread::join,_1));
    }

    vector<Thread> startThreads(size_t n, Runnable& runnable) {
        vector<Thread> threads(n);
        while (n > 0)
            threads[--n] = Thread(runnable);
        return  threads;
    }

public:
    ProducerConsumerTest() : client() {}

    void testProduceConsume() {
        ConsumeRunnable runMe(*this);
        produce();
        produce();
        CPPUNIT_ASSERT(produced.waitFor(2));
        vector<Thread> threads = startThreads(1, runMe);
        CPPUNIT_ASSERT(consumed.waitFor(1));
        join(threads);

        threads = startThreads(1, runMe);
        CPPUNIT_ASSERT(consumed.waitFor(2));
        join(threads);
        
        threads = startThreads(3, runMe);
        produce();
        produce();
        CPPUNIT_ASSERT(consumed.waitFor(4));
        produce();
        CPPUNIT_ASSERT(consumed.waitFor(5));
        join(threads);
        CPPUNIT_ASSERT_EQUAL(0, int(shutdown));
    }

    void testTimeout() {
        try {
            // 0 timeout no items available throws exception
            ProducerConsumer::ConsumerLock consumer(pc, 0);
            CPPUNIT_FAIL("Expected exception");
        } catch(...){}

        produce();
        CPPUNIT_ASSERT(produced.waitFor(1));
        CPPUNIT_ASSERT_EQUAL(1, int(pc.available()));
        {
            // 0 timeout succeeds if there's an item available.
            ProducerConsumer::ConsumerLock consume(pc, 0);
            CPPUNIT_ASSERT(consume.isOk());
            consume.confirm();
        }
        CPPUNIT_ASSERT_EQUAL(0, int(pc.available()));

        // Produce an item within the timeout.
        ConsumeTimeoutRunnable runMe(*this, 2*TIME_SEC);
        vector<Thread> threads = startThreads(1, runMe);
        produce();
        CPPUNIT_ASSERT(consumed.waitFor(1));
        join(threads);
    }

    
    void testShutdown() {
        ConsumeRunnable runMe(*this);
        vector<Thread> threads = startThreads(2, runMe);
        while (pc.consumers() != 2)
            Thread::yield(); 
        pc.shutdown();
        CPPUNIT_ASSERT(shutdown.waitFor(2));
        join(threads);

        threads = startThreads(1, runMe); // Should shutdown immediately.
        CPPUNIT_ASSERT(shutdown.waitFor(3));
        join(threads);

        // Produce/consume while shutdown should return isShutdown and
        // throw on confirm.
        try {
            ProducerConsumer::ProducerLock p(pc);
            CPPUNIT_ASSERT(pc.isShutdown());
            CPPUNIT_FAIL("Expected exception");
        }
        catch (...) {}          // Expected
        try {
            ProducerConsumer::ConsumerLock c(pc);
            CPPUNIT_ASSERT(pc.isShutdown());
            CPPUNIT_FAIL("Expected exception");
        }
        catch (...) {}          // Expected
    }

    void testCancel() {
        CPPUNIT_ASSERT_EQUAL(size_t(0), pc.available());
        {
            ProducerConsumer::ProducerLock p(pc);
            CPPUNIT_ASSERT(p.isOk());
            p.cancel();
        }
        // Nothing was produced.
        CPPUNIT_ASSERT_EQUAL(size_t(0), pc.available());
        {
            ProducerConsumer::ConsumerLock c(pc, 0);
            CPPUNIT_ASSERT(c.isTimedOut());
        }
        // Now produce but cancel the consume
        {
            ProducerConsumer::ProducerLock p(pc);
            CPPUNIT_ASSERT(p.isOk());
            p.confirm();
        }
        CPPUNIT_ASSERT_EQUAL(size_t(1), pc.available());
        {
            ProducerConsumer::ConsumerLock c(pc);
            CPPUNIT_ASSERT(c.isOk());
            c.cancel();
        }
        CPPUNIT_ASSERT_EQUAL(size_t(1), pc.available());
    }
};


// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ProducerConsumerTest);

