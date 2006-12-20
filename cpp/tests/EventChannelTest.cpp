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
using namespace std;
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
    CPPUNIT_TEST(testDispatch);
    CPPUNIT_TEST(testRead);
    CPPUNIT_TEST(testPartialRead);
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

    // Verify that calling wait returns event.
    template <class T> bool isNextEvent(T& event)
    {
        return &event == dynamic_cast<T*>(ec->wait(5*TIME_SEC));
    }

    template <class T> bool isNextEventOk(T& event)
    {
        Event* next = ec->wait(TIME_SEC);
        if (next) next->throwIfException();
        return &event == next;
    }
        
    void testDispatch()
    {
        RunMe runMe;
        CPPUNIT_ASSERT(!runMe.ran);
        // Instances of Event just pass thru the channel immediately.
        DispatchEvent e(runMe.functor());
        ec->post(e);
        CPPUNIT_ASSERT(isNextEventOk(e));
        e.dispatch();
        CPPUNIT_ASSERT(runMe.ran);
    }

    void testRead() {
        ReadEvent re(pipe[0], readBuf, size);
        ec->post(re);
        CPPUNIT_ASSERT_EQUAL(ssize_t(size), ::write(pipe[1], hello, size));
        CPPUNIT_ASSERT(isNextEventOk(re));
        CPPUNIT_ASSERT_EQUAL(size, re.getBytesRead());
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testPartialRead() {
        ReadEvent re(pipe[0], readBuf, size, 0);
        ec->post(re);
        CPPUNIT_ASSERT_EQUAL(ssize_t(size/2), ::write(pipe[1], hello, size/2));
        CPPUNIT_ASSERT(isNextEventOk(re));
        CPPUNIT_ASSERT_EQUAL(size/2, re.getBytesRead());
        CPPUNIT_ASSERT_EQUAL(std::string(hello, size/2),
                             std::string(readBuf, size/2));
    }

    void testFailedRead() {
        ReadEvent re(pipe[0], readBuf, size);
        ec->post(re);
        // Close the write end while reading, causes a HUP.
        ::close(pipe[1]);
        CPPUNIT_ASSERT(isNextEvent(re));
        CPPUNIT_ASSERT(re.getException());

        // Try to read from closed fd. Fails in post() and throws.
        try {
            ec->post(re);
            CPPUNIT_FAIL("Expected exception");
        }
        catch (const qpid::QpidError&) {}
    }

    void testWrite() {
        WriteEvent wr(pipe[1], hello, size);
        ec->post(wr);
        CPPUNIT_ASSERT(isNextEventOk(wr));
        CPPUNIT_ASSERT_EQUAL(size, wr.getBytesWritten());
        CPPUNIT_ASSERT_EQUAL(ssize_t(size), ::read(pipe[0], readBuf, size));;
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testFailedWrite() {
        WriteEvent wr(pipe[1], hello, size);
        ec->post(wr);
        // Close the read end while writing.
        ::close(pipe[0]);
        CPPUNIT_ASSERT(isNextEvent(wr));
        CPPUNIT_ASSERT(wr.getException());
    }

    void testReadWrite()
    {
        ReadEvent re(pipe[0], readBuf, size);
        WriteEvent wr(pipe[1], hello, size);
        ec->post(re);
        ec->post(wr);
        ec->wait(TIME_SEC);
        ec->wait(TIME_SEC);
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void connectSendRead(AcceptEvent& ae, int port, Socket client)
    {
        ec->post(ae);
        // Connect a client, send some data, read the data.
        client.connect("localhost", port);
        CPPUNIT_ASSERT(isNextEvent(ae));
        ae.throwIfException();

        char readBuf[size];
        ReadEvent re(ae.getAcceptedDesscriptor(), readBuf, size);
        ec->post(re);
        CPPUNIT_ASSERT_EQUAL(ssize_t(size),
                             client.send(hello, sizeof(hello)));
        CPPUNIT_ASSERT(isNextEvent(re));
        re.throwIfException();
        CPPUNIT_ASSERT_EQUAL(std::string(hello), std::string(readBuf));
    }

    void testAccept() {
        Socket s = Socket::createTcp();
        int port = s.listen(0, 10);
        CPPUNIT_ASSERT(port != 0);

        AcceptEvent ae(s.fd());
        Socket client = Socket::createTcp();
        connectSendRead(ae, port,  client);
        Socket client2 = Socket::createTcp();
        connectSendRead(ae, port,  client2);
        client.close();
        client2.close();
        Socket client3 = Socket::createTcp();
        connectSendRead(ae, port,  client3);
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(EventChannelTest);

