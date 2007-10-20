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
#include <BrokerChannel.h>
#include <BrokerMessage.h>
#include <BrokerQueue.h>
#include <FanOutExchange.h>
#include <NullMessageStore.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <memory>
#include <AMQP_HighestVersion.h>


using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using std::string;
using std::queue;

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
    CPPUNIT_TEST(testDeliveryAndRecovery);
    CPPUNIT_TEST(testStaging);
    CPPUNIT_TEST(testQueuePolicy);
    CPPUNIT_TEST(testFlow);
    CPPUNIT_TEST_SUITE_END();

    class MockMessageStore : public NullMessageStore
    {
        struct MethodCall
        {
            const string name;
            Message* const msg;
            const string data;//only needed for appendContent

            void check(const MethodCall& other) const
            {
                CPPUNIT_ASSERT_EQUAL(name, other.name);
                CPPUNIT_ASSERT_EQUAL(msg, other.msg);
                CPPUNIT_ASSERT_EQUAL(data, other.data);
            }
        };

        queue<MethodCall> expected;
        bool expectMode;//true when setting up expected calls

        void handle(const MethodCall& call)
        {
            if (expectMode) {
                expected.push(call);
            } else {
                call.check(expected.front());
                expected.pop();
            }
        }

        void handle(const string& name, Message* msg, const string& data)
        {
            MethodCall call = {name, msg, data};
            handle(call);
        }

    public:

        MockMessageStore() : expectMode(false) {}

        void stage(Message* const msg)
        {
            if(!expectMode) msg->setPersistenceId(1);
            MethodCall call = {"stage", msg, ""};
            handle(call);
        }

        void appendContent(Message* msg, const string& data)
        {
            MethodCall call = {"appendContent", msg, data};
            handle(call);
        }

        void destroy(Message* msg)
        {
            MethodCall call = {"destroy", msg, ""};
            handle(call);
        }
        
        void expect()
        {
            expectMode = true;
        }

        void test()
        {
            expectMode = false;
        }

        void check()
        {
            CPPUNIT_ASSERT(expected.empty());
        }
    };


  public:

    void testConsumerMgmt(){
        Queue::shared_ptr queue(new Queue("my_queue"));
        Channel channel(qpid::framing::highestProtocolVersion, 0, 0, 0);
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
        Channel channel(qpid::framing::highestProtocolVersion, &handler, 7, 10000);

        const string data("abcdefghijklmn");

        Message::shared_ptr msg(createMessage("test", "my_routing_key", "my_message_id", 14));
        addContent(msg, data);
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
        CPPUNIT_ASSERT_EQUAL(data, contentBody->getData());
    }

    void testDeliveryAndRecovery(){
        DummyHandler handler;
        Channel channel(qpid::framing::highestProtocolVersion, &handler, 7, 10000);
        const string data("abcdefghijklmn");

        Message::shared_ptr msg(createMessage("test", "my_routing_key", "my_message_id", 14));
        addContent(msg, data);

        Queue::shared_ptr queue(new Queue("my_queue"));
        ConnectionToken* owner(0);
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
        CPPUNIT_ASSERT_EQUAL(data, contentBody->getData());
    }

    void testStaging(){
        MockMessageStore store;
        DummyHandler handler;
        Channel channel(qpid::framing::highestProtocolVersion, &handler, 1, 1000/*framesize*/, &store, 10/*staging threshold*/);
        const string data[] = {"abcde", "fghij", "klmno"};

        Message* msg = new Message(0, "my_exchange", "my_routing_key", false, false);

        store.expect();
        store.stage(msg);
        for (int i = 0; i < 3; i++) {
            store.appendContent(msg, data[i]);
        }
        store.destroy(msg);
        store.test();

        Exchange::shared_ptr exchange(new FanOutExchange("my_exchange"));
        Queue::shared_ptr queue(new Queue("my_queue"));
        exchange->bind(queue, "", 0);

        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        u_int64_t contentSize(0);
        for (int i = 0; i < 3; i++) {
            contentSize += data[i].size();
        }
        header->setContentSize(contentSize);
        channel.handlePublish(msg, exchange);
        channel.handleHeader(header);

        for (int i = 0; i < 3; i++) {
            AMQContentBody::shared_ptr body(new AMQContentBody(data[i]));
            channel.handleContent(body);
        }
        Message::shared_ptr msg2 = queue->dequeue();
        CPPUNIT_ASSERT_EQUAL(msg, msg2.get());
        msg2.reset();//should trigger destroy call

        store.check();
    }


    //NOTE: strictly speaking this should/could be part of QueueTest,
    //but as it can usefully use the same utility classes as this
    //class it is defined here for simpllicity
    void testQueuePolicy()
    {
        MockMessageStore store;
        {//must ensure that store is last thing deleted as it is needed by destructor of lazy loaded content
        const string data1("abcd");
        const string data2("efghijk");
        const string data3("lmnopqrstuvwxyz");
        Message::shared_ptr msg1(createMessage("e", "A", "MsgA", data1.size()));
        Message::shared_ptr msg2(createMessage("e", "B", "MsgB", data2.size()));
        Message::shared_ptr msg3(createMessage("e", "C", "MsgC", data3.size()));
        addContent(msg1, data1);
        addContent(msg2, data2);
        addContent(msg3, data3);

        QueuePolicy policy(2, 0);//third message should be stored on disk and lazy loaded
        FieldTable settings;
        policy.update(settings);
        
        store.expect();
        store.stage(msg3.get());
        store.destroy(msg3.get());
        store.test();

        Queue::shared_ptr queue(new Queue("my_queue", false, &store, 0));
        queue->configure(settings);//set policy
        queue->deliver(msg1);
        queue->deliver(msg2);
        queue->deliver(msg3);
        
        Message::shared_ptr next = queue->dequeue();
        CPPUNIT_ASSERT_EQUAL(msg1, next);
        CPPUNIT_ASSERT_EQUAL((u_int32_t) data1.size(), next->encodedContentSize());
        next = queue->dequeue();
        CPPUNIT_ASSERT_EQUAL(msg2, next);
        CPPUNIT_ASSERT_EQUAL((u_int32_t) data2.size(), next->encodedContentSize());
        next = queue->dequeue();
        CPPUNIT_ASSERT_EQUAL(msg3, next);
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 0, next->encodedContentSize());

        next.reset();
        msg1.reset();
        msg2.reset();
        msg3.reset();//must clear all references to messages to allow them to be destroyed

        }
        store.check();
    }


    void testFlow(){
        DummyHandler handler;
        Channel channel(qpid::framing::highestProtocolVersion, &handler, 7, 10000);

        const string data("abcdefghijklmn");

        Message::shared_ptr msg(createMessage("test", "my_routing_key", "my_message_id", 14));
        addContent(msg, data);
        Queue::shared_ptr queue(new Queue("my_queue"));
        ConnectionToken* owner(0);
        string tag("no_ack");
        channel.consume(tag, queue, false, false, owner);
        channel.flow(false);
        queue->deliver(msg);
        CPPUNIT_ASSERT_EQUAL((size_t) 0, handler.frames.size());
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 1, queue->getMessageCount());        
        channel.flow(true);
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
        CPPUNIT_ASSERT_EQUAL(data, contentBody->getData());
    }

    Message* createMessage(const string& exchange, const string& routingKey, const string& messageId, u_int64_t contentSize)
    {
        Message* msg = new Message(0, exchange, routingKey, false, false);
        AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
        header->setContentSize(contentSize);        
        msg->setHeader(header);
        msg->getHeaderProperties()->setMessageId(messageId);
        return msg;
    }

    void addContent(Message::shared_ptr msg, const string& data)
    {
        AMQContentBody::shared_ptr body(new AMQContentBody(data));
        msg->addContent(body);
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(ChannelTest);
