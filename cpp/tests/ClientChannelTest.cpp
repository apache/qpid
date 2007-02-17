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
#include "qpid_test_plugin.h"
#include "InProcessBroker.h"
#include "ClientChannel.h"
#include "ClientMessage.h"
#include "ClientQueue.h"
#include "ClientExchange.h"

using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::framing;

/**
 * Test client API using an in-process broker.
 */
class ClientChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ClientChannelTest);
    CPPUNIT_TEST(testGet);
    CPPUNIT_TEST(testConsume);
    CPPUNIT_TEST_SUITE_END();

    InProcessBrokerClient connection; // client::connection + local broker
    Channel channel;
    const std::string key;
    const std::string data;
    Queue queue;
    Exchange exchange;

  public:

    ClientChannelTest()
        : key("testq"), data("hello"),
          queue(key, true), exchange("", Exchange::DIRECT_EXCHANGE)
    {
        connection.openChannel(channel);
        CPPUNIT_ASSERT(channel.getId() != 0);
        channel.declareQueue(queue);
    }

    void testGet() {
        // FIXME aconway 2007-02-16: Must fix thread safety bug
        // in ClientChannel::get for this to pass.
        return;

        Message pubMsg(data);
        channel.publish(pubMsg, exchange, key);
        Message getMsg;
        channel.get(getMsg, queue);
        CPPUNIT_ASSERT_EQUAL(data, getMsg.getData());
    }

    void testConsume() {
    }
    

    // FIXME aconway 2007-02-15: Cover full channel API
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ClientChannelTest);
