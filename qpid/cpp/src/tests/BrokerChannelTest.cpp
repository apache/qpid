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

// FIXME aconway 2007-08-30: Rewrite as a Session test.
// There is an issue with the tests use of DeliveryAdapter
// which is no longer exposed on Session (part of SemanticHandler.)
// 
#include "qpid/broker/BrokerChannel.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/FanOutExchange.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/MessageDelivery.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <sstream>
#include <memory>
#include "qpid/framing/AMQP_HighestVersion.h"
#include "qpid/framing/AMQFrame.h"
#include "MockChannel.h"
#include "qpid/broker/Connection.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ConnectionStartBody.h"
#include <vector>

using namespace boost;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
using std::string;
using std::queue;

struct MockHandler : ConnectionOutputHandler{
    std::vector<AMQFrame> frames; 

    void send(AMQFrame& frame){ frames.push_back(frame); }

    void close() {};
};

struct DeliveryRecorder : DeliveryAdapter
{
    DeliveryId id;
    typedef std::pair<intrusive_ptr<Message>, DeliveryToken::shared_ptr> Delivery;
    std::vector<Delivery> delivered;

    DeliveryId deliver(intrusive_ptr<Message>& msg, DeliveryToken::shared_ptr token)
    {
        delivered.push_back(Delivery(msg, token));
        return ++id;
    }

    void redeliver(intrusive_ptr<Message>& msg, DeliveryToken::shared_ptr token, DeliveryId /*tag*/) 
    {
        delivered.push_back(Delivery(msg, token));
    }
};

class BrokerChannelTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(BrokerChannelTest);
    CPPUNIT_TEST(testConsumerMgmt);;
    CPPUNIT_TEST(testDeliveryNoAck);
    CPPUNIT_TEST(testQueuePolicy);
    CPPUNIT_TEST(testFlow);
    CPPUNIT_TEST(testAsyncMesgToMoreThanOneQueue);
    CPPUNIT_TEST_SUITE_END();

    shared_ptr<Broker> broker;
    Connection connection;
    MockHandler handler;
    
    class MockMessageStore : public NullMessageStore
    {
        struct MethodCall
        {
            const string name;
            PersistableMessage* msg;
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

        void handle(const string& name, PersistableMessage* msg, const string& data)
        {
            MethodCall call = {name, msg, data};
            handle(call);
        }

    public:

        MockMessageStore() : expectMode(false) {}

        void stage(PersistableMessage& msg)
        {
            if(!expectMode) msg.setPersistenceId(1);
            MethodCall call = {"stage", &msg, ""};
            handle(call);
        }

        void appendContent(PersistableMessage& msg, const string& data)
        {
            MethodCall call = {"appendContent", &msg, data};
            handle(call);
        }

        // Don't hide overloads.
        using NullMessageStore::destroy;
        
        void destroy(PersistableMessage& msg)
        {
            MethodCall call = {"destroy", &msg, ""};
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
            if (!expected.empty()) {
                std::stringstream error;
                error << "Expected: ";
                while (!expected.empty()) {
                    MethodCall& m = expected.front();
                    error << m.name << "(" << m.msg << ", '" << m.data << "'); ";
                    expected.pop();
                }
                CPPUNIT_FAIL(error.str());
            }
        }
    };

    DeliveryRecorder recorder;

  public:

    BrokerChannelTest() :
        broker(Broker::create()),
        connection(&handler, *broker)
    {
        connection.initiated(ProtocolInitiation());
    }


    void testConsumerMgmt(){
        Queue::shared_ptr queue(new Queue("my_queue"));
        Channel channel(connection, recorder, 0);
        channel.open();
        CPPUNIT_ASSERT(!channel.exists("my_consumer"));

        ConnectionToken* owner = 0;
        string tag("my_consumer");
        DeliveryToken::shared_ptr unused;
        channel.consume(unused, tag, queue, false, false, owner);
        string tagA;
        string tagB;
        channel.consume(unused, tagA, queue, false, false, owner);
        channel.consume(unused, tagB, queue, false, false, owner);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 3, queue->getConsumerCount());
        CPPUNIT_ASSERT(channel.exists("my_consumer"));
        CPPUNIT_ASSERT(channel.exists(tagA));
        CPPUNIT_ASSERT(channel.exists(tagB));
        channel.cancel(tagA);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 2, queue->getConsumerCount());
        CPPUNIT_ASSERT(channel.exists("my_consumer"));
        CPPUNIT_ASSERT(!channel.exists(tagA));
        CPPUNIT_ASSERT(channel.exists(tagB));
        channel.close();
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, queue->getConsumerCount());        
    }

    void testDeliveryNoAck(){        
        Channel channel(connection, recorder, 7);
        intrusive_ptr<Message> msg(createMessage("test", "my_routing_key", "my_message_id", 14));
        Queue::shared_ptr queue(new Queue("my_queue"));
        string tag("test");
        DeliveryToken::shared_ptr token(MessageDelivery::getBasicConsumeToken("my-token"));
        channel.consume(token, tag, queue, false, false, 0);
        queue->deliver(msg);
	sleep(2);

        CPPUNIT_ASSERT_EQUAL((size_t) 1, recorder.delivered.size());
        CPPUNIT_ASSERT_EQUAL(msg, recorder.delivered.front().first);
        CPPUNIT_ASSERT_EQUAL(token, recorder.delivered.front().second);
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
        intrusive_ptr<Message> msg1(createMessage("e", "A", "MsgA", data1.size()));
        intrusive_ptr<Message> msg2(createMessage("e", "B", "MsgB", data2.size()));
        intrusive_ptr<Message> msg3(createMessage("e", "C", "MsgC", data3.size()));
        addContent(msg1, data1);
        addContent(msg2, data2);
        addContent(msg3, data3);

        QueuePolicy policy(2, 0);//third message should be stored on disk and lazy loaded
        FieldTable settings;
        policy.update(settings);
        
        store.expect();
        store.stage(0, *msg3);
        store.test();

        Queue::shared_ptr queue(new Queue("my_queue", false, &store, 0));
        queue->configure(settings);//set policy
        queue->deliver(msg1);
        queue->deliver(msg2);
        queue->deliver(msg3);
	sleep(2);
        
        intrusive_ptr<Message> next = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1, next);
        CPPUNIT_ASSERT_EQUAL((uint32_t) data1.size(), next->encodedContentSize());
        next = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg2, next);
        CPPUNIT_ASSERT_EQUAL((uint32_t) data2.size(), next->encodedContentSize());
        next = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg3, next);
        CPPUNIT_ASSERT_EQUAL((uint32_t) 0, next->encodedContentSize());

        next.reset();
        msg1.reset();
        msg2.reset();
        msg3.reset();//must clear all references to messages to allow them to be destroyed

        }
        store.check();
    }


    //NOTE: message or queue test,
    //but as it can usefully use the same utility classes as this
    //class it is defined here for simpllicity
    void testAsyncMesgToMoreThanOneQueue()
    {
        MockMessageStore store;
        {//must ensure that store is last thing deleted
        const string data1("abcd");
        intrusive_ptr<Message> msg1(createMessage("e", "A", "MsgA", data1.size()));
        addContent(msg1, data1);
 
        Queue::shared_ptr queue1(new Queue("my_queue1", false, &store, 0));
        Queue::shared_ptr queue2(new Queue("my_queue2", false, &store, 0));
        Queue::shared_ptr queue3(new Queue("my_queue3", false, &store, 0));
        queue1->deliver(msg1);
        queue2->deliver(msg1);
        queue3->deliver(msg1);
	sleep(2);
        
        intrusive_ptr<Message> next = queue1->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1, next);
        next = queue2->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1, next);
        next = queue3->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1, next);

        }
    }



    void testFlow(){
        Channel channel(connection, recorder, 7);
        channel.open();
        //there will always be a connection-start frame
        CPPUNIT_ASSERT_EQUAL((size_t) 1, handler.frames.size());
        CPPUNIT_ASSERT_EQUAL(ChannelId(0), handler.frames[0].getChannel());
        CPPUNIT_ASSERT(dynamic_cast<ConnectionStartBody*>(handler.frames[0].getBody()));
        
        Queue::shared_ptr queue(new Queue("my_queue"));
        string tag("test");
        DeliveryToken::shared_ptr token(MessageDelivery::getBasicConsumeToken("my-token"));
        channel.consume(token, tag, queue, false, false, 0);
        channel.flow(false);

        //'publish' a message
        intrusive_ptr<Message> msg(createMessage("test", "my_routing_key", "my_message_id", 14));
        addContent(msg, "abcdefghijklmn");
        queue->deliver(msg);

        //ensure no messages have been delivered
        CPPUNIT_ASSERT_EQUAL((size_t) 0, recorder.delivered.size());

        channel.flow(true);
	sleep(2);
        //ensure no messages have been delivered
        CPPUNIT_ASSERT_EQUAL((size_t) 1, recorder.delivered.size());
        CPPUNIT_ASSERT_EQUAL(msg, recorder.delivered.front().first);
        CPPUNIT_ASSERT_EQUAL(token, recorder.delivered.front().second);
    }

    intrusive_ptr<Message> createMessage(const string& exchange, const string& routingKey, const string& messageId, uint64_t contentSize)
    {
        intrusive_ptr<Message> msg(new Message());

        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), 0, exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());

        msg->getFrames().append(method);
        msg->getFrames().append(header);
        MessageProperties* props = msg->getFrames().getHeaders()->get<MessageProperties>(true);
        props->setContentLength(contentSize);        
        props->setMessageId(messageId);
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
        return msg;
    }

    void addContent(intrusive_ptr<Message> msg, const string& data)
    {
        AMQFrame content(in_place<AMQContentBody>(data));
        msg->getFrames().append(content);
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(BrokerChannelTest);
