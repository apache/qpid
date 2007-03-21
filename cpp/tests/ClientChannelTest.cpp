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
#include "qpid_test_plugin.h"
#include "InProcessBroker.h"
#include "ClientChannel.h"
#include "ClientMessage.h"
#include "ClientQueue.h"
#include "ClientExchange.h"

using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::sys;
using namespace qpid::framing;

/**
 * Test client API using an in-process broker.
 */
class ClientChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ClientChannelTest);
    CPPUNIT_TEST(testPublishGet);
    CPPUNIT_TEST(testGetNoContent);
    CPPUNIT_TEST(testConsumeCancel);
    CPPUNIT_TEST(testConsumePublished);
    CPPUNIT_TEST_SUITE_END();

    struct Listener: public qpid::client::MessageListener {
        vector<Message> messages;
        Monitor monitor;
        void received(Message& msg) {
            Mutex::ScopedLock l(monitor);
            messages.push_back(msg);
            monitor.notifyAll();
        }
    };
    
    InProcessBrokerClient connection; // client::connection + local broker
    Channel channel;
    const std::string qname;
    const std::string data;
    Queue queue;
    Exchange exchange;
    Listener listener;

  public:

    ClientChannelTest()
        : qname("testq"), data("hello"),
          queue(qname, true), exchange("", Exchange::DIRECT_EXCHANGE)
    {
        connection.openChannel(channel);
        CPPUNIT_ASSERT(channel.getId() != 0);
        channel.declareQueue(queue);
    }

    void testPublishGet() {
        Message pubMsg(data);
        pubMsg.getHeaders().setString("hello", "world");
        channel.getBasic().publish(pubMsg, exchange, qname);
        Message getMsg;
        CPPUNIT_ASSERT(channel.getBasic().get(getMsg, queue));
        CPPUNIT_ASSERT_EQUAL(data, getMsg.getData());
        CPPUNIT_ASSERT_EQUAL(string("world"),
                             getMsg.getHeaders().getString("hello"));
        CPPUNIT_ASSERT(!channel.getBasic().get(getMsg, queue)); // Empty queue
    }

    void testGetNoContent() {
        Message pubMsg;
        pubMsg.getHeaders().setString("hello", "world");
        channel.getBasic().publish(pubMsg, exchange, qname);
        Message getMsg;
        CPPUNIT_ASSERT(channel.getBasic().get(getMsg, queue));
        CPPUNIT_ASSERT(getMsg.getData().empty());
        CPPUNIT_ASSERT_EQUAL(string("world"),
                             getMsg.getHeaders().getString("hello"));
    }

    void testConsumeCancel() {
        string tag;             // Broker assigned
        channel.getBasic().consume(queue, tag, &listener);
        channel.start();
        CPPUNIT_ASSERT_EQUAL(size_t(0), listener.messages.size());
        channel.getBasic().publish(Message("a"), exchange, qname);
        {
            Mutex::ScopedLock l(listener.monitor);
            Time deadline(now() + 1*TIME_SEC);
            while (listener.messages.size() != 1) {
                CPPUNIT_ASSERT(listener.monitor.wait(deadline));
            }
        }
        CPPUNIT_ASSERT_EQUAL(size_t(1), listener.messages.size());
        CPPUNIT_ASSERT_EQUAL(string("a"), listener.messages[0].getData());
            
        channel.getBasic().publish(Message("b"), exchange, qname);
        channel.getBasic().publish(Message("c"), exchange, qname);
        {
            Mutex::ScopedLock l(listener.monitor);
            while (listener.messages.size() != 3) {
                CPPUNIT_ASSERT(listener.monitor.wait(1*TIME_SEC));
            }
        }
        CPPUNIT_ASSERT_EQUAL(size_t(3), listener.messages.size());
        CPPUNIT_ASSERT_EQUAL(string("b"), listener.messages[1].getData());
        CPPUNIT_ASSERT_EQUAL(string("c"), listener.messages[2].getData());
    
        channel.getBasic().cancel(tag);
        channel.getBasic().publish(Message("d"), exchange, qname);
        CPPUNIT_ASSERT_EQUAL(size_t(3), listener.messages.size());
        {
            Mutex::ScopedLock l(listener.monitor);
            CPPUNIT_ASSERT(!listener.monitor.wait(TIME_SEC/2));
        }
        Message msg;
        CPPUNIT_ASSERT(channel.getBasic().get(msg, queue));
        CPPUNIT_ASSERT_EQUAL(string("d"), msg.getData());
    }

    // Consume already-published messages
    void testConsumePublished() {
        Message pubMsg("x");
        pubMsg.getHeaders().setString("y", "z");
        channel.getBasic().publish(pubMsg, exchange, qname);
        string tag;
        channel.getBasic().consume(queue, tag, &listener);
        CPPUNIT_ASSERT_EQUAL(size_t(0), listener.messages.size());
        channel.start();
        {
            Mutex::ScopedLock l(listener.monitor);
            while (listener.messages.size() != 1) 
                CPPUNIT_ASSERT(listener.monitor.wait(1*TIME_SEC));
        }
        CPPUNIT_ASSERT_EQUAL(string("x"), listener.messages[0].getData());
        CPPUNIT_ASSERT_EQUAL(string("z"),
                             listener.messages[0].getHeaders().getString("y"));
    }

    
        
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ClientChannelTest);
