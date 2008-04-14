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
#include "qpid/broker/Message.h"
#include "qpid/broker/MessageBuilder.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/framing/frame_functors.h"
#include "qpid/framing/TypeFilter.h"
#include "qpid_test_plugin.h"
#include <list>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

class MessageBuilderTest : public CppUnit::TestCase  
{
    class MockMessageStore : public NullMessageStore
    {
        enum Op {STAGE=1, APPEND=2};

        uint64_t id;
        intrusive_ptr<PersistableMessage> expectedMsg;        
        string expectedData;
        std::list<Op> ops;
        
        void checkExpectation(Op actual)
        {
            CPPUNIT_ASSERT_EQUAL(ops.front(), actual);
            ops.pop_front();
        }

    public:
        MockMessageStore() : id(0), expectedMsg(0) {}

        void expectStage(PersistableMessage& msg) 
        { 
            expectedMsg = &msg;
            ops.push_back(STAGE); 
        }

        void expectAppendContent(PersistableMessage& msg, const string& data) 
        { 
            expectedMsg = &msg;
            expectedData = data;
            ops.push_back(APPEND); 
        }

        void stage(intrusive_ptr<PersistableMessage>& msg)
        {
            checkExpectation(STAGE);
            CPPUNIT_ASSERT_EQUAL(expectedMsg, msg);
            msg->setPersistenceId(++id);
        }

        void appendContent(intrusive_ptr<const PersistableMessage>& msg, const string& data)
        {
            checkExpectation(APPEND);
            CPPUNIT_ASSERT_EQUAL(static_pointer_cast<const PersistableMessage>(expectedMsg), msg);
            CPPUNIT_ASSERT_EQUAL(expectedData, data);            
        }

        bool expectationsMet()
        {
            return ops.empty();
        }
    };

    CPPUNIT_TEST_SUITE(MessageBuilderTest);
    CPPUNIT_TEST(testHeaderOnly);
    CPPUNIT_TEST(test1ContentFrame);
    CPPUNIT_TEST(test2ContentFrames);
    CPPUNIT_TEST(testStaging);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testHeaderOnly(){
        MessageBuilder builder(0, 0);
        builder.start(SequenceNumber());

        std::string exchange("builder-exchange");
        std::string key("builder-exchange");

        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());

        header.castBody<AMQHeaderBody>()->get<MessageProperties>(true)->setContentLength(0);        
        header.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true)->setRoutingKey(key);

        builder.handle(method);
        builder.handle(header);

        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT_EQUAL(exchange, builder.getMessage()->getExchangeName());
        CPPUNIT_ASSERT_EQUAL(key, builder.getMessage()->getRoutingKey());
        CPPUNIT_ASSERT(builder.getMessage()->getFrames().isComplete());
    }

    void test1ContentFrame(){
        MessageBuilder builder(0, 0);
        builder.start(SequenceNumber());

        std::string data("abcdefg");
        std::string exchange("builder-exchange");
        std::string key("builder-exchange");

        AMQFrame method(in_place<MessageTransferBody>(ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());
        AMQFrame content(in_place<AMQContentBody>(data));
        method.setEof(false);
        header.setBof(false);
        header.setEof(false);
        content.setBof(false);

        header.castBody<AMQHeaderBody>()->get<MessageProperties>(true)->setContentLength(data.size());        
        header.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true)->setRoutingKey(key);

        builder.handle(method);
        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT(!builder.getMessage()->getFrames().isComplete());

        builder.handle(header);
        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT(!builder.getMessage()->getFrames().isComplete());

        builder.handle(content);        
        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT(builder.getMessage()->getFrames().isComplete());
    }

    void test2ContentFrames(){
        MessageBuilder builder(0, 0);
        builder.start(SequenceNumber());

        std::string data1("abcdefg");
        std::string data2("hijklmn");
        std::string exchange("builder-exchange");
        std::string key("builder-exchange");

        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());
        AMQFrame content1(in_place<AMQContentBody>(data1));
        AMQFrame content2(in_place<AMQContentBody>(data2));
        method.setEof(false);
        header.setBof(false);
        header.setEof(false);
        content1.setBof(false);
        content1.setEof(false);
        content2.setBof(false);

        header.castBody<AMQHeaderBody>()->get<MessageProperties>(true)->setContentLength(data1.size() + data2.size());        
        header.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true)->setRoutingKey(key);

        builder.handle(method);
        builder.handle(header);
        builder.handle(content1);
        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT(!builder.getMessage()->getFrames().isComplete());

        builder.handle(content2);
        CPPUNIT_ASSERT(builder.getMessage());
        CPPUNIT_ASSERT(builder.getMessage()->getFrames().isComplete());
    }

    void testStaging(){
        MockMessageStore store;
        MessageBuilder builder(&store, 5);
        builder.start(SequenceNumber());
        
        std::string data1("abcdefg");
        std::string data2("hijklmn");
        std::string exchange("builder-exchange");
        std::string key("builder-exchange");

        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());
        AMQFrame content1(in_place<AMQContentBody>(data1));
        AMQFrame content2(in_place<AMQContentBody>(data2));

        header.castBody<AMQHeaderBody>()->get<MessageProperties>(true)->setContentLength(data1.size() + data2.size());        
        header.castBody<AMQHeaderBody>()->get<DeliveryProperties>(true)->setRoutingKey(key);

        builder.handle(method);
        builder.handle(header);

        store.expectStage(*builder.getMessage());
        builder.handle(content1);
        CPPUNIT_ASSERT(store.expectationsMet());
        CPPUNIT_ASSERT_EQUAL((uint64_t) 1, builder.getMessage()->getPersistenceId());

        store.expectAppendContent(*builder.getMessage(), data2);
        builder.handle(content2);
        CPPUNIT_ASSERT(store.expectationsMet());

        //were the content frames dropped?
        CPPUNIT_ASSERT_EQUAL((uint64_t) 0, builder.getMessage()->contentSize());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageBuilderTest);
