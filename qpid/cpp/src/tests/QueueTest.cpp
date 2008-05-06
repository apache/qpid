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
#include "qpid/Exception.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include "boost/format.hpp"

using boost::intrusive_ptr;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;


class TestConsumer : public virtual Consumer{
public:
    typedef boost::shared_ptr<TestConsumer> shared_ptr;            

    intrusive_ptr<Message> last;
    bool received;
    TestConsumer(): received(false) {};

    virtual bool deliver(QueuedMessage& msg){
        last = msg.payload;
        received = true;
        return true;
    };
    void notify() {}
    OwnershipToken* getSession() { return 0; }
};

class FailOnDeliver : public Deliverable
{
    Message msg;
public:
    void deliverTo(Queue::shared_ptr& queue)
    {
        throw Exception(QPID_MSG("Invalid delivery to " << queue->getName()));
    }
    Message& getMessage() { return msg; }
};

class QueueTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(QueueTest);
    CPPUNIT_TEST(testConsumers);
    CPPUNIT_TEST(testRegistry);
    CPPUNIT_TEST(testDequeue);
    CPPUNIT_TEST(testBound);
    CPPUNIT_TEST(testAsyncMessage);
    CPPUNIT_TEST(testAsyncMessageCount);
    CPPUNIT_TEST_SUITE_END();


  public:
    intrusive_ptr<Message> message(std::string exchange, std::string routingKey) {
        intrusive_ptr<Message> msg(new Message());
        AMQFrame method(in_place<MessageTransferBody>(
                            ProtocolVersion(), exchange, 0, 0));
        AMQFrame header(in_place<AMQHeaderBody>());
        msg->getFrames().append(method);
        msg->getFrames().append(header);
        msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
        return msg;
    }
    
    
    void testAsyncMessage(){
    
        Queue::shared_ptr queue(new Queue("my_test_queue", true));
        intrusive_ptr<Message> received;
	
        TestConsumer c1;
        queue->consume(c1);

       
        //Test basic delivery:
        intrusive_ptr<Message> msg1 = message("e", "A");
        msg1->enqueueAsync();//this is done on enqueue which is not called from process
        queue->process(msg1);
	sleep(2);

        CPPUNIT_ASSERT(!c1.received);
 	msg1->enqueueComplete();

        received = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1.get(), received.get());
 	
   
    }
    
    
    void testAsyncMessageCount(){
        Queue::shared_ptr queue(new Queue("my_test_queue", true));
        intrusive_ptr<Message> msg1 = message("e", "A");
        msg1->enqueueAsync();//this is done on enqueue which is not called from process
	
        queue->process(msg1);
	sleep(2);
	uint32_t compval=0;
        CPPUNIT_ASSERT_EQUAL(compval, queue->getMessageCount());
 	msg1->enqueueComplete();
	compval=1;
        CPPUNIT_ASSERT_EQUAL(compval, queue->getMessageCount());
    
    }
    
    void testConsumers(){
        Queue::shared_ptr queue(new Queue("my_queue", true));
    
        //Test adding consumers:
        TestConsumer c1;
        TestConsumer c2;
        queue->consume(c1);
        queue->consume(c2);

        CPPUNIT_ASSERT_EQUAL(uint32_t(2), queue->getConsumerCount());
        
        //Test basic delivery:
        intrusive_ptr<Message> msg1 = message("e", "A");
        intrusive_ptr<Message> msg2 = message("e", "B");
        intrusive_ptr<Message> msg3 = message("e", "C");

        queue->deliver(msg1);
        CPPUNIT_ASSERT(queue->dispatch(c1));
        CPPUNIT_ASSERT_EQUAL(msg1.get(), c1.last.get());

        queue->deliver(msg2);
        CPPUNIT_ASSERT(queue->dispatch(c2));
        CPPUNIT_ASSERT_EQUAL(msg2.get(), c2.last.get());
        
	c1.received = false;
        queue->deliver(msg3);
        CPPUNIT_ASSERT(queue->dispatch(c1));
        CPPUNIT_ASSERT_EQUAL(msg3.get(), c1.last.get());        
    
        //Test cancellation:
        queue->cancel(c1);
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), queue->getConsumerCount());
        queue->cancel(c2);
        CPPUNIT_ASSERT_EQUAL(uint32_t(0), queue->getConsumerCount());
    }

    void testRegistry(){
        //Test use of queues in registry:
        QueueRegistry registry;
        registry.declare("queue1", true, true);
        registry.declare("queue2", true, true);
        registry.declare("queue3", true, true);

        CPPUNIT_ASSERT(registry.find("queue1"));
        CPPUNIT_ASSERT(registry.find("queue2"));
        CPPUNIT_ASSERT(registry.find("queue3"));
        
        registry.destroy("queue1");
        registry.destroy("queue2");
        registry.destroy("queue3");

        CPPUNIT_ASSERT(!registry.find("queue1"));
        CPPUNIT_ASSERT(!registry.find("queue2"));
        CPPUNIT_ASSERT(!registry.find("queue3"));
    }

    void testDequeue(){
        Queue::shared_ptr queue(new Queue("my_queue", true));
        intrusive_ptr<Message> msg1 = message("e", "A");
        intrusive_ptr<Message> msg2 = message("e", "B");
        intrusive_ptr<Message> msg3 = message("e", "C");
        intrusive_ptr<Message> received;

        queue->deliver(msg1);
        queue->deliver(msg2);
        queue->deliver(msg3);

        CPPUNIT_ASSERT_EQUAL(uint32_t(3), queue->getMessageCount());
        
        received = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg1.get(), received.get());
        CPPUNIT_ASSERT_EQUAL(uint32_t(2), queue->getMessageCount());

        received = queue->dequeue().payload;
        CPPUNIT_ASSERT_EQUAL(msg2.get(), received.get());
        CPPUNIT_ASSERT_EQUAL(uint32_t(1), queue->getMessageCount());

        TestConsumer consumer;
        queue->consume(consumer);
        queue->dispatch(consumer);
	if (!consumer.received)
	    sleep(2);

        CPPUNIT_ASSERT_EQUAL(msg3.get(), consumer.last.get());
        CPPUNIT_ASSERT_EQUAL(uint32_t(0), queue->getMessageCount());

        received = queue->dequeue().payload;
        CPPUNIT_ASSERT(!received);
        CPPUNIT_ASSERT_EQUAL(uint32_t(0), queue->getMessageCount());
        
    }

    void testBound()
    {
        //test the recording of bindings, and use of those to allow a queue to be unbound
        string key("my-key");
        FieldTable args;

        Queue::shared_ptr queue(new Queue("my-queue", true));
        ExchangeRegistry exchanges;
        //establish bindings from exchange->queue and notify the queue as it is bound:
        Exchange::shared_ptr exchange1 = exchanges.declare("my-exchange-1", "direct").first;
        exchange1->bind(queue, key, &args);
        queue->bound(exchange1->getName(), key, args);

        Exchange::shared_ptr exchange2 = exchanges.declare("my-exchange-2", "fanout").first;
        exchange2->bind(queue, key, &args);
        queue->bound(exchange2->getName(), key, args);

        Exchange::shared_ptr exchange3 = exchanges.declare("my-exchange-3", "topic").first;
        exchange3->bind(queue, key, &args);
        queue->bound(exchange3->getName(), key, args);

        //delete one of the exchanges:
        exchanges.destroy(exchange2->getName());
        exchange2.reset();

        //unbind the queue from all exchanges it knows it has been bound to:
        queue->unbind(exchanges, queue);

        //ensure the remaining exchanges don't still have the queue bound to them:
        FailOnDeliver deliverable;        
        exchange1->route(deliverable, key, &args);
        exchange3->route(deliverable, key, &args);
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(QueueTest);


