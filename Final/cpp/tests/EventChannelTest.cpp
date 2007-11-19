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
#include <posix/EventChannel.h>
#include <posix/check.h>
#include <sys/Runnable.h>
#include <sys/Socket.h>
#include <sys/Thread.h>
#include <qpid_test_plugin.h>

#include <sys/socket.h>
#include <signal.h>
#include <netinet/in.h>
#include <netdb.h>
#include <iostream>

using namespace qpid::sys;


const char hello[] = "hello";
const size_t size = sizeof(hello);

struct RunMe : public Runnable 
{
    bool ran;
    RunMe() : ran(false) {}
    void run() { ran = true; }
};

class EventChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(EventChannelTest);
    CPPUNIT_TEST(testEvent);
    CPPUNIT_TEST(testRead);
    CPPUNIT_TEST(testFailedRead);
    CPPUNIT_TEST(testWrite);
    CPPUNIT_TEST(testFailedWrite);
    CPPUNIT_TEST(testReadWrite);
    CPPUNIT_TEST(testAccept);
    CPPUNIT_TEST_SUITE_END();

  private:
    EventChannel::shared_ptr ec;
    int pipe[2];
    char readBuf[size];

  public:

    void setUp()
    {
        memset(readBuf, size, 0);
        ec = EventChannel::create();
        if (::pipe(pipe) != 0) throw QPID_POSIX_ERROR(errno);
        // Ignore SIGPIPE, otherwise we will crash writing to broken pipe.
        signal(SIGPIPE, SIG_IGN);
    }

    // Verify that calling getEvent returns event.
    template <class T> bool isNextEvent(T& event)
    {
        return &event == dynamic_cast<T*>(ec->getEvent());
    }

    template <class T> bool isNextEventOk(T& event)
    {
        Event* next = ec->getEvent();
        if (next) next->throwIfError();
        return &event == next;
    }
        
    void testEvent()
    {
        RunMe runMe;
        CPPUNIT_ASSERT(!runMe.ran);
        // Instances of Event just pass thru the channel immediately.
        Event e(runMe.functor());
        ec->postEvent(e);
        CPPUNIT_ASSERT(isNextEventOk(e));
        e.dispatch();
        CPPUNIT_ASSERT(runMe.ran);
    }

    void testRead() {
        ReadEvent re(pipe[0], readBuf, size);
        ec->postEvent(re);
        CPPUNIT_ASSERT_EQUAL(ssize_t(size), ::write(pipe[1], hello, size));
        CPPUNIT_ASSERT(isNextEventOk(re));
        CPPUNIT_ASSERT_EQUAL(size, re.getSize());
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testFailedRead() 
    {
        ReadEvent re(pipe[0], readBuf, size);
        ec->postEvent(re);

        // EOF before all data read.
        ::close(pipe[1]);
        CPPUNIT_ASSERT(isNextEvent(re));
        CPPUNIT_ASSERT(re.hasError());
        try {
            re.throwIfError();
            CPPUNIT_FAIL("Expected QpidError.");
        }
        catch (const qpid::QpidError&) { }

        //  Bad file descriptor. Note in this case we fail
        //  in postEvent and throw immediately.
        try {
            ReadEvent bad;
            ec->postEvent(bad);
            CPPUNIT_FAIL("Expected QpidError.");
        }
        catch (const qpid::QpidError&) { }
    }

    void testWrite() {
        WriteEvent wr(pipe[1], hello, size);
        ec->postEvent(wr);
        CPPUNIT_ASSERT(isNextEventOk(wr));
        CPPUNIT_ASSERT_EQUAL(ssize_t(size), ::read(pipe[0], readBuf, size));;
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testFailedWrite() {
        WriteEvent wr(pipe[1], hello, size);
        ::close(pipe[0]);
        ec->postEvent(wr);
        CPPUNIT_ASSERT(isNextEvent(wr));
        CPPUNIT_ASSERT(wr.hasError());
    }

    void testReadWrite()
    {
        ReadEvent re(pipe[0], readBuf, size);
        WriteEvent wr(pipe[1], hello, size);
        ec->postEvent(re);
        ec->postEvent(wr);
        ec->getEvent();
        ec->getEvent();
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testAccept() {
        Socket s = Socket::createTcp();
        int port = s.listen(0, 10);
        CPPUNIT_ASSERT(port != 0);

        AcceptEvent ae(s.fd());
        ec->postEvent(ae);
        Socket client = Socket::createTcp();
        client.connect("localhost", port);
        CPPUNIT_ASSERT(isNextEvent(ae));
        ae.dispatch();

        // Verify client writes are read by the accepted descriptor.
        char readBuf[size];
        ReadEvent re(ae.getAcceptedDesscriptor(), readBuf, size);
        ec->postEvent(re);
        CPPUNIT_ASSERT_EQUAL(ssize_t(size), client.send(hello, sizeof(hello)));
        CPPUNIT_ASSERT(isNextEvent(re));
        re.dispatch();
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(EventChannelTest);

