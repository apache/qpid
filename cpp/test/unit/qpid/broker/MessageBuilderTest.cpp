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
#include <qpid/broker/Message.h>
#include <qpid/broker/MessageBuilder.h>
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


    CPPUNIT_TEST_SUITE(MessageBuilderTest);
    CPPUNIT_TEST(testHeaderOnly);
    CPPUNIT_TEST(test1ContentFrame);
    CPPUNIT_TEST(test2ContentFrames);
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
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(MessageBuilderTest);
