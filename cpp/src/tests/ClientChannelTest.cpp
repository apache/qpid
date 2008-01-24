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
#include "BrokerFixture.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Message.h"
#include "qpid/client/Queue.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/BasicMessageChannel.h"
#include "qpid/client/MessageMessageChannel.h"

using namespace std;
using namespace boost;
using namespace qpid::client;
using namespace qpid::sys;
using namespace qpid::framing;

/// Small frame size so we can create fragmented messages.
const size_t FRAME_MAX = 256;


/**
 * Test base for client API using an in-process broker.
 * The test base defines the tests methods, derived classes
 * instantiate the channel in Basic or Message mode.
 */
class ChannelTestBase : public CppUnit::TestCase, public SessionFixture
{
    struct Listener: public qpid::client::MessageListener {
        vector<Message> messages;
        Monitor monitor;
        void received(Message& msg) {
            Mutex::ScopedLock l(monitor);
            messages.push_back(msg);
            monitor.notifyAll();
        }
    };
    
    const std::string qname;
    const std::string data;
    Queue queue;
    Exchange exchange;
    Listener listener;

  protected:
        boost::scoped_ptr<Channel> channel;

  public:

    ChannelTestBase()
        : qname("testq"), data("hello"),
          queue(qname, true), exchange("", Exchange::DIRECT_EXCHANGE)
    {}

    void setUp() {
        CPPUNIT_ASSERT(channel);
        connection.openChannel(*channel);
        CPPUNIT_ASSERT(channel->getId() != 0);
        channel->declareQueue(queue);
    }

    void testPublishGet() {
        Message pubMsg(data);
        pubMsg.getHeaders().setString("hello", "world");
        channel->publish(pubMsg, exchange, qname);
        Message getMsg;
        CPPUNIT_ASSERT(channel->get(getMsg, queue));
        CPPUNIT_ASSERT_EQUAL(data, getMsg.getData());
        CPPUNIT_ASSERT_EQUAL(string("world"),
                             getMsg.getHeaders().getString("hello"));
        CPPUNIT_ASSERT(!channel->get(getMsg, queue)); // Empty queue
    }

    void testGetNoContent() {
        Message pubMsg;
        pubMsg.getHeaders().setString("hello", "world");
        channel->publish(pubMsg, exchange, qname);
        Message getMsg;
        CPPUNIT_ASSERT(channel->get(getMsg, queue));
        CPPUNIT_ASSERT(getMsg.getData().empty());
        CPPUNIT_ASSERT_EQUAL(string("world"),
                             getMsg.getHeaders().getString("hello"));
    }

    void testConsumeCancel() {
        string tag;             // Broker assigned
        channel->consume(queue, tag, &listener);
        channel->start();
        CPPUNIT_ASSERT_EQUAL(size_t(0), listener.messages.size());
        channel->publish(Message("a"), exchange, qname);
        {
            Mutex::ScopedLock l(listener.monitor);
            Time deadline(now() + 1*TIME_SEC);
            while (listener.messages.size() != 1) {
                CPPUNIT_ASSERT(listener.monitor.wait(deadline));
            }
        }
        CPPUNIT_ASSERT_EQUAL(size_t(1), listener.messages.size());
        CPPUNIT_ASSERT_EQUAL(string("a"), listener.messages[0].getData());
            
        channel->publish(Message("b"), exchange, qname);
        channel->publish(Message("c"), exchange, qname);
        {
            Mutex::ScopedLock l(listener.monitor);
            while (listener.messages.size() != 3) {
                CPPUNIT_ASSERT(listener.monitor.wait(1*TIME_SEC));
            }
        }
        CPPUNIT_ASSERT_EQUAL(size_t(3), listener.messages.size());
        CPPUNIT_ASSERT_EQUAL(string("b"), listener.messages[1].getData());
        CPPUNIT_ASSERT_EQUAL(string("c"), listener.messages[2].getData());
    
        channel->cancel(tag);
        channel->publish(Message("d"), exchange, qname);
        CPPUNIT_ASSERT_EQUAL(size_t(3), listener.messages.size());
        {
            Mutex::ScopedLock l(listener.monitor);
            CPPUNIT_ASSERT(!listener.monitor.wait(TIME_SEC/2));
        }
        Message msg;
        CPPUNIT_ASSERT(channel->get(msg, queue));
        CPPUNIT_ASSERT_EQUAL(string("d"), msg.getData());
    }

    // Consume already-published messages
    void testConsumePublished() {
        Message pubMsg("x");
        pubMsg.getHeaders().setString("y", "z");
        channel->publish(pubMsg, exchange, qname);
        string tag;
        channel->consume(queue, tag, &listener);
        CPPUNIT_ASSERT_EQUAL(size_t(0), listener.messages.size());
        channel->start();
        {
            Mutex::ScopedLock l(listener.monitor);
            while (listener.messages.size() != 1) 
                CPPUNIT_ASSERT(listener.monitor.wait(1*TIME_SEC));
        }
        CPPUNIT_ASSERT_EQUAL(string("x"), listener.messages[0].getData());
        CPPUNIT_ASSERT_EQUAL(string("z"),
                             listener.messages[0].getHeaders().getString("y"));
    }

    void testGetFragmentedMessage() {
        string longStr(FRAME_MAX*2, 'x'); // Longer than max frame size.
        channel->publish(Message(longStr), exchange, qname);
        Message getMsg;
        CPPUNIT_ASSERT(channel->get(getMsg, queue));
    }
    
    void testConsumeFragmentedMessage() {
        string xx(FRAME_MAX*2, 'x');
        channel->publish(Message(xx), exchange, qname);
        channel->start();
        string tag;
        channel->consume(queue, tag, &listener);
        string yy(FRAME_MAX*2, 'y');
        channel->publish(Message(yy), exchange, qname);
        {
            Mutex::ScopedLock l(listener.monitor);
            while (listener.messages.size() != 2)
                CPPUNIT_ASSERT(listener.monitor.wait(1*TIME_SEC));
        }
        CPPUNIT_ASSERT_EQUAL(xx, listener.messages[0].getData());
        CPPUNIT_ASSERT_EQUAL(yy, listener.messages[1].getData());
    }
};

class BasicChannelTest : public ChannelTestBase {
    CPPUNIT_TEST_SUITE(BasicChannelTest);
    CPPUNIT_TEST(testPublishGet);
    CPPUNIT_TEST(testGetNoContent);
    CPPUNIT_TEST(testConsumeCancel);
    CPPUNIT_TEST(testConsumePublished);
    CPPUNIT_TEST(testGetFragmentedMessage);
    CPPUNIT_TEST(testConsumeFragmentedMessage);
    CPPUNIT_TEST_SUITE_END();

  public:
    BasicChannelTest(){
        channel.reset(new Channel(false, 500, Channel::AMQP_08));
    }
};

class MessageChannelTest : public ChannelTestBase {
    CPPUNIT_TEST_SUITE(MessageChannelTest);
    CPPUNIT_TEST(testPublishGet);
    CPPUNIT_TEST(testGetNoContent);
    CPPUNIT_TEST(testGetFragmentedMessage);
    CPPUNIT_TEST_SUITE_END();
  public:
    MessageChannelTest() {
        channel.reset(new Channel(false, 500, Channel::AMQP_09));
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(BasicChannelTest);
CPPUNIT_TEST_SUITE_REGISTRATION(MessageChannelTest);
