/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/broker/Channel.h"
#include "qpid/broker/Message.h"
#include <qpid_test_plugin.h>
#include <iostream>
#include <memory>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::concurrent;

struct DummyHandler : OutputHandler{
    std::vector<AMQFrame*> frames; 

    virtual void send(AMQFrame* frame){
        frames.push_back(frame);
    }
};


class ChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(ChannelTest);
    CPPUNIT_TEST(testConsumerMgmt);
    CPPUNIT_TEST(testDeliveryNoAck);
    CPPUNIT_TEST_SUITE_END();

  public:

    void testConsumerMgmt(){
        Queue::shared_ptr queue(new Queue("my_queue"));
        Channel channel(0, 0, 0);
        CPPUNIT_ASSERT(!channel.exists("my_consumer"));

        ConnectionToken* owner = 0;
        string tag("my_consumer");
        channel.consume(tag, queue, false, false, owner);
        string tagA;
        string tagB;
        channel.consume(tagA, queue, false, false, owner);
        channel.consume(tagB, queue, false, false, owner);
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 3, queue->getConsumerCount());
        CPPUNIT_ASSERT(channel.exists("my_consumer"));
        CPPUNIT_ASSERT(channel.exists(tagA));
        CPPUNIT_ASSERT(channel.exists(tagB));
        channel.cancel(tagA);
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 2, queue->getConsumerCount());
        CPPUNIT_ASSERT(channel.exists("my_consumer"));
        CPPUNIT_ASSERT(!channel.exists(tagA));
        CPPUNIT_ASSERT(channel.exists(tagB));
        channel.close();
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 0, queue->getConsumerCount());        
    }

    void testDeliveryNoAck(){
        DummyHandler handler;
        Channel channel(&handler, 7, 10000);

        Message::shared_ptr msg(new Message(0, "test", "my_routing_key", false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(14);
        msg->setHeader(header);
        AMQContentBody::shared_ptr body(new AMQContentBody("abcdefghijklmn"));
        msg->addContent(body);

        Queue::shared_ptr queue(new Queue("my_queue"));
        ConnectionToken* owner(0);
        string tag("no_ack");
        channel.consume(tag, queue, false, false, owner);

        queue->deliver(msg);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, handler.frames.size());
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[0]->getChannel());        
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[1]->getChannel());        
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[2]->getChannel());
        BasicDeliverBody::shared_ptr deliver(dynamic_pointer_cast<BasicDeliverBody, AMQBody>(handler.frames[0]->getBody()));
        AMQHeaderBody::shared_ptr contentHeader(dynamic_pointer_cast<AMQHeaderBody, AMQBody>(handler.frames[1]->getBody()));
        AMQContentBody::shared_ptr contentBody(dynamic_pointer_cast<AMQContentBody, AMQBody>(handler.frames[2]->getBody()));
        CPPUNIT_ASSERT(deliver);
        CPPUNIT_ASSERT(contentHeader);
        CPPUNIT_ASSERT(contentBody);
        CPPUNIT_ASSERT_EQUAL(string("abcdefghijklmn"), contentBody->getData());
    }

    void testDeliveryAndRecovery(){
        DummyHandler handler;
        Channel channel(&handler, 7, 10000);

        Message::shared_ptr msg(new Message(0, "test", "my_routing_key", false, false));
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(14);
        msg->setHeader(header);
        AMQContentBody::shared_ptr body(new AMQContentBody("abcdefghijklmn"));
        msg->addContent(body);

        Queue::shared_ptr queue(new Queue("my_queue"));
        ConnectionToken* owner;
        string tag("ack");
        channel.consume(tag, queue, true, false, owner);

        queue->deliver(msg);
        CPPUNIT_ASSERT_EQUAL((size_t) 3, handler.frames.size());
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[0]->getChannel());        
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[1]->getChannel());        
        CPPUNIT_ASSERT_EQUAL((u_int16_t) 7, handler.frames[2]->getChannel());
        BasicDeliverBody::shared_ptr deliver(dynamic_pointer_cast<BasicDeliverBody, AMQBody>(handler.frames[0]->getBody()));
        AMQHeaderBody::shared_ptr contentHeader(dynamic_pointer_cast<AMQHeaderBody, AMQBody>(handler.frames[1]->getBody()));
        AMQContentBody::shared_ptr contentBody(dynamic_pointer_cast<AMQContentBody, AMQBody>(handler.frames[2]->getBody()));
        CPPUNIT_ASSERT(deliver);
        CPPUNIT_ASSERT(contentHeader);
        CPPUNIT_ASSERT(contentBody);
        CPPUNIT_ASSERT_EQUAL(string("abcdefghijklmn"), contentBody->getData());
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ChannelTest);
