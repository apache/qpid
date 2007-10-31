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
#include <Exception.h>
#include <BrokerMessage.h>
#include <MessageBuilder.h>
#include <NullMessageStore.h>
#include <Buffer.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <memory>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;

class MessageBuilderTest : public CppUnit::TestCase  
{
    struct DummyHandler : MessageBuilder::CompletionHandler{
        Message::shared_ptr msg;
        
        virtual void complete(Message::shared_ptr& _msg){
            msg = _msg;
        }
    };

    class TestMessageStore : public NullMessageStore
    {
        Buffer* header;
        Buffer* content;
        const u_int32_t contentBufferSize;
        
    public:

        void stage(Message* const msg)
        {
            if (msg->getPersistenceId() == 0) {
                header = new Buffer(msg->encodedHeaderSize());
                msg->encodeHeader(*header);                
                content = new Buffer(contentBufferSize);
                msg->setPersistenceId(1);
            } else {
                throw qpid::Exception("Message already staged!");
            }
        }

        void appendContent(Message* msg, const string& data)
        {
            if (msg) {
                content->putRawData(data);
            } else {
                throw qpid::Exception("Invalid message id!");
            }
        }

        void destroy(Message* msg)
        {
            CPPUNIT_ASSERT(msg->getPersistenceId());
        }

        Message::shared_ptr getRestoredMessage()
        {
            Message::shared_ptr msg(new Message());
            if (header) {
                header->flip();
                msg->decodeHeader(*header);
                delete header;
                header = 0; 
                if (content) {
                    content->flip();
                    msg->decodeContent(*content);
                    delete content;
                    content = 0;
                }
            }
            return msg;
        }
        
        //dont care about any of the other methods:
        TestMessageStore(u_int32_t _contentBufferSize) : NullMessageStore(false), header(0), content(0), 
                                                         contentBufferSize(_contentBufferSize) {}
        ~TestMessageStore(){}
    };

    CPPUNIT_TEST_SUITE(MessageBuilderTest);
    CPPUNIT_TEST(testHeaderOnly);
    CPPUNIT_TEST(test1ContentFrame);
    CPPUNIT_TEST(test2ContentFrames);
    CPPUNIT_TEST(testStaging);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testHeaderOnly(){
        DummyHandler handler;
        MessageBuilder builder(&handler);

        Message::shared_ptr message(new Message(0, "test", "my_routing_key", false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(0);
        
        builder.initialise(message);
        CPPUNIT_ASSERT(!handler.msg);
        builder.setHeader(header);
        CPPUNIT_ASSERT(handler.msg);
        CPPUNIT_ASSERT_EQUAL(message, handler.msg);
    }

    void test1ContentFrame(){
        DummyHandler handler;
        MessageBuilder builder(&handler);

        string data1("abcdefg");

        Message::shared_ptr message(new Message(0, "test", "my_routing_key", false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(7);
        AMQContentBody::shared_ptr part1(new AMQContentBody(data1));
        
        builder.initialise(message);
        CPPUNIT_ASSERT(!handler.msg);
        builder.setHeader(header);
        CPPUNIT_ASSERT(!handler.msg);
        builder.addContent(part1);
        CPPUNIT_ASSERT(handler.msg);
        CPPUNIT_ASSERT_EQUAL(message, handler.msg);
    }

    void test2ContentFrames(){
        DummyHandler handler;
        MessageBuilder builder(&handler);

        string data1("abcdefg");
        string data2("hijklmn");

        Message::shared_ptr message(new Message(0, "test", "my_routing_key", false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(14);
        AMQContentBody::shared_ptr part1(new AMQContentBody(data1));
        AMQContentBody::shared_ptr part2(new AMQContentBody(data2));        
        
        builder.initialise(message);
        CPPUNIT_ASSERT(!handler.msg);
        builder.setHeader(header);
        CPPUNIT_ASSERT(!handler.msg);
        builder.addContent(part1);
        CPPUNIT_ASSERT(!handler.msg);
        builder.addContent(part2);
        CPPUNIT_ASSERT(handler.msg);
        CPPUNIT_ASSERT_EQUAL(message, handler.msg);
    }

    void testStaging(){
        //store must be the last thing to be destroyed or destructor
        //of Message fails (it uses the store to call destroy if lazy
        //loaded content is in use)
        TestMessageStore store(14);
        {
            DummyHandler handler;
            MessageBuilder builder(&handler, &store, 5);
            
            string data1("abcdefg");
            string data2("hijklmn");
            
            Message::shared_ptr message(new Message(0, "test", "my_routing_key", false, false));
            AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
            header->setContentSize(14);
            BasicHeaderProperties* properties = dynamic_cast<BasicHeaderProperties*>(header->getProperties());
            properties->setMessageId("MyMessage");
            properties->getHeaders().setString("abc", "xyz");
            
            AMQContentBody::shared_ptr part1(new AMQContentBody(data1));
            AMQContentBody::shared_ptr part2(new AMQContentBody(data2));        
            
            builder.initialise(message);
            builder.setHeader(header);
            builder.addContent(part1);
            builder.addContent(part2);
            CPPUNIT_ASSERT(handler.msg);
            CPPUNIT_ASSERT_EQUAL(message, handler.msg);
            
            Message::shared_ptr restored = store.getRestoredMessage();
            CPPUNIT_ASSERT_EQUAL(message->getExchange(), restored->getExchange());
            CPPUNIT_ASSERT_EQUAL(message->getRoutingKey(), restored->getRoutingKey());
            CPPUNIT_ASSERT_EQUAL(message->getHeaderProperties()->getMessageId(), restored->getHeaderProperties()->getMessageId());
            CPPUNIT_ASSERT_EQUAL(message->getHeaderProperties()->getHeaders().getString("abc"), 
                                 restored->getHeaderProperties()->getHeaders().getString("abc"));
            CPPUNIT_ASSERT_EQUAL((u_int64_t) 14, restored->contentSize());
        }
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageBuilderTest);
