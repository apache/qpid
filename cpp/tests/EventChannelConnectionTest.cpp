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
#include "framing/AMQHeartbeatBody.h"
#include "framing/AMQFrame.h"
#include "sys/posix/EventChannelConnection.h"
#include "sys/SessionHandler.h"
#include "sys/SessionHandlerFactory.h"
#include "sys/Socket.h"
#include "qpid_test_plugin.h"
#include "MockSessionHandler.h"

using namespace qpid::sys;
using namespace qpid::framing;
using namespace std;

class EventChannelConnectionTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(EventChannelConnectionTest);
    CPPUNIT_TEST(testSendReceive);
    CPPUNIT_TEST(testCloseExternal);
    CPPUNIT_TEST(testCloseException);
    CPPUNIT_TEST_SUITE_END();

  public:

    void setUp() {
        threads = EventChannelThreads::create();
        CPPUNIT_ASSERT_EQUAL(0, ::pipe(pipe));
        connection.reset(
            new EventChannelConnection(threads, factory, pipe[0], pipe[1]));
        CPPUNIT_ASSERT(factory.handler != 0);
    }

    void tearDown() {
        threads->shutdown();
        threads->join();
    }

    void testSendReceive()
    {
        // Send a protocol initiation.
        Buffer buf(1024);
        ProtocolInitiation(4,2).encode(buf);
        buf.flip();
        ssize_t n = write(pipe[1], buf.start(), buf.available());
        CPPUNIT_ASSERT_EQUAL(ssize_t(buf.available()), n);

        // Verify session handler got the protocol init.
        ProtocolInitiation init = factory.handler->waitForProtocolInit();
        CPPUNIT_ASSERT_EQUAL(int(4), int(init.getMajor()));
        CPPUNIT_ASSERT_EQUAL(int(2), int(init.getMinor()));

        // Send a heartbeat frame, verify connection got it.
        connection->send(new AMQFrame(42, new AMQHeartbeatBody()));
        AMQFrame frame = factory.handler->waitForFrame();
        CPPUNIT_ASSERT_EQUAL(u_int16_t(42), frame.getChannel());
        CPPUNIT_ASSERT_EQUAL(u_int8_t(HEARTBEAT_BODY),
                             frame.getBody()->type());
        threads->shutdown();
    }

    // Make sure the handler is closed if the connection is closed.
    void testCloseExternal() {
        connection->close();
        factory.handler->waitForClosed();
    }

    // Make sure the handler is closed if the connection closes or fails.
    // TODO aconway 2006-12-18: logs exception message in test output.
    void testCloseException() {
        ::close(pipe[0]);
        ::close(pipe[1]);
        // TODO aconway 2006-12-18: Shouldn't this be failing?
        connection->send(new AMQFrame(42, new AMQHeartbeatBody()));
        factory.handler->waitForClosed();
    }

  private:
    EventChannelThreads::shared_ptr threads;
    int pipe[2];
    std::auto_ptr<EventChannelConnection> connection;
    MockSessionHandlerFactory factory;
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(EventChannelConnectionTest);

