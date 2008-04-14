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
#include <iostream>
#include <boost/bind.hpp>

#include "qpid/sys/Socket.h"
#include "qpid/sys/posix/EventChannelThreads.h"
#include "qpid_test_plugin.h"


using namespace std;

using namespace qpid::sys;

const int nConnections = 5;
const int nMessages = 10; // Messages read/written per connection.


// Accepts + reads + writes.
const int totalEvents = nConnections+2*nConnections*nMessages;

/**
 * Messages are numbered 0..nMessages.
 * We count the total number of events, and the
 * number of reads and writes for each message number.
 */
class TestResults : public Monitor {
  public:
    TestResults() : isShutdown(false), nEventsRemaining(totalEvents) {}

    void countEvent() {
        if (--nEventsRemaining == 0)
            shutdown();
    }

    void countRead(int messageNo) {
        ++reads[messageNo];
        countEvent();
    }

    void countWrite(int messageNo) {
        ++writes[messageNo];
        countEvent();
    }

    void shutdown(const std::string& exceptionMsg = std::string()) {
        ScopedLock lock(*this);
        exception = exceptionMsg;
        isShutdown = true;
        notifyAll();
    }
    
    void wait() {
        ScopedLock lock(*this);
        Time deadline = now() + 10*TIME_SEC; 
        while (!isShutdown) {
            CPPUNIT_ASSERT(Monitor::wait(deadline));
        }
    }

    bool isShutdown;
    std::string exception;
    AtomicCount reads[nMessages];
    AtomicCount writes[nMessages];
    AtomicCount nEventsRemaining;
};

TestResults results;

EventChannelThreads::shared_ptr threads;

// Functor to wrap callbacks in try/catch.
class SafeCallback {
  public:
    SafeCallback(Runnable& r) : callback(r.functor()) {}
    SafeCallback(Event::Callback cb) : callback(cb) {}
    
    void operator()() {
        std::string exception;
        try {
            callback();
            return;
        }
        catch (const std::exception& e) {
            exception = e.what();
        }
        catch (...) {
            exception = "Unknown exception.";
        }
        results.shutdown(exception);
    }

  private:
    Event::Callback callback;
};

/** Repost an event N times. */
class Repost {
  public:
    Repost(int n) : count (n) {}
    virtual ~Repost() {}
    
    void repost(Event* event) {
        if (--count==0) {
            delete event;
        } else {
            threads->postEvent(event);
        }
    }
  private:
    int count;
};
    
            

/** Repeating read event. */
class TestReadEvent : public ReadEvent, public Runnable, private Repost {
  public:
    explicit TestReadEvent(int fd=-1) :
        ReadEvent(fd, &value, sizeof(value), SafeCallback(*this)),
        Repost(nMessages)
    {}
    
    void run() {
        CPPUNIT_ASSERT_EQUAL(sizeof(value), getSize());
        CPPUNIT_ASSERT(0 <= value);
        CPPUNIT_ASSERT(value < nMessages);
        results.countRead(value);
        repost(this);
    }
    
  private:
    int value;
    ReadEvent original;
};


/** Fire and forget write event */
class TestWriteEvent : public WriteEvent, public Runnable, private Repost {
  public:
    TestWriteEvent(int fd=-1) :
        WriteEvent(fd, &value, sizeof(value), SafeCallback(*this)),
        Repost(nMessages),
        value(0)
    {}
    
    void run() {
        CPPUNIT_ASSERT_EQUAL(sizeof(int), getSize());
        results.countWrite(value++);
        repost(this);
    }

  private:
    int value;
};

/** Fire-and-forget Accept event, posts reads on the accepted connection. */
class TestAcceptEvent : public AcceptEvent, public Runnable, private Repost {
  public:
    TestAcceptEvent(int fd=-1) :
        AcceptEvent(fd, SafeCallback(*this)),
        Repost(nConnections)
    {}
    
    void run() {
        threads->postEvent(new TestReadEvent(getAcceptedDesscriptor()));
        results.countEvent();
        repost(this);
    }
};

class EventChannelThreadsTest : public CppUnit::TestCase
{
    CPPUNIT_TEST_SUITE(EventChannelThreadsTest);
    CPPUNIT_TEST(testThreads);
    CPPUNIT_TEST_SUITE_END();

  public:

    void setUp() {
        threads = EventChannelThreads::create(EventChannel::create());
    }

    void tearDown() {
        threads.reset();
    }

    void testThreads()
    {
        Socket listener = Socket::createTcp();
        int port = listener.listen();

        // Post looping accept events, will repost nConnections times.
        // The accept event will automatically post read events.
        threads->postEvent(new TestAcceptEvent(listener.fd()));

        // Make connections.
        Socket connections[nConnections];
        for (int i = 0; i < nConnections; ++i) {
            connections[i] = Socket::createTcp();
            connections[i].connect("localhost", port);
        }

        // Post looping write events.
        for (int i = 0; i < nConnections; ++i) {
            threads->postEvent(new TestWriteEvent(connections[i].fd()));
        }

        // Wait for all events to be dispatched.
        results.wait();

        if (!results.exception.empty()) CPPUNIT_FAIL(results.exception);
        CPPUNIT_ASSERT_EQUAL(0, int(results.nEventsRemaining));

        // Expect a read and write for each messageNo from each connection.
        for (int messageNo = 0; messageNo < nMessages; ++messageNo) {
            CPPUNIT_ASSERT_EQUAL(nConnections, int(results.reads[messageNo]));
            CPPUNIT_ASSERT_EQUAL(nConnections, int(results.writes[messageNo]));
        }

        threads->shutdown();
        threads->join();
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(EventChannelThreadsTest);

