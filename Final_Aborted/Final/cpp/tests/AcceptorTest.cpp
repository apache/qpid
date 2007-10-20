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

#include "sys/Thread.h"
#include "sys/Acceptor.h"
#include "sys/Socket.h"
#include "framing/Buffer.h"
#include "qpid_test_plugin.h"

#include "MockSessionHandler.h"

using namespace qpid::sys;
using namespace qpid::framing;
using namespace std;

const char hello[] = "hello";
const size_t size = sizeof(hello);

        
class AcceptorTest : public CppUnit::TestCase, private Runnable
{
    CPPUNIT_TEST_SUITE(AcceptorTest);
    CPPUNIT_TEST(testAccept);
    CPPUNIT_TEST_SUITE_END();

  private:
    MockSessionHandlerFactory factory;
    Acceptor::shared_ptr acceptor;

  public:

    void run() {
        acceptor->run(factory);
    }

    void setUp() {
        acceptor = Acceptor::create(0, 10, 3);
    }

    void tearDown() {
        acceptor.reset();
    }

    void testAccept()
    {
        int port = acceptor->getPort();
        CPPUNIT_ASSERT(port > 0);
        Thread runThread(*this);
        // Connect to the acceptor
        Socket client = Socket::createTcp();
        client.connect("localhost", port);
        factory.waitForHandler();
        CPPUNIT_ASSERT(factory.handler != 0);
        // Send a protocol initiation.
        Buffer buf(1024);
        ProtocolInitiation(4,2).encode(buf);
        buf.flip();
        client.send(buf.start(), buf.available());

        // Verify session handler got the protocol init.
        ProtocolInitiation init = factory.handler->waitForProtocolInit();
        CPPUNIT_ASSERT_EQUAL(int(4), int(init.getMajor()));
        CPPUNIT_ASSERT_EQUAL(int(2), int(init.getMinor()));

        acceptor->shutdown();
        runThread.join(); 
        factory.handler->waitForClosed();
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(AcceptorTest);

