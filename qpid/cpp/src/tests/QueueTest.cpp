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
#include "unit_test.h"
#include "qpid/Exception.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/framing/MessageTransferBody.h"
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

intrusive_ptr<Message> message(std::string exchange, std::string routingKey) {
    intrusive_ptr<Message> msg(new Message());
    AMQFrame method(in_place<MessageTransferBody>(ProtocolVersion(), exchange, 0, 0));
    AMQFrame header(in_place<AMQHeaderBody>());
    msg->getFrames().append(method);
    msg->getFrames().append(header);
    msg->getFrames().getHeaders()->get<DeliveryProperties>(true)->setRoutingKey(routingKey);
    return msg;
}

QPID_AUTO_TEST_SUITE(QueueTestSuite)

QPID_AUTO_TEST_CASE(testAsyncMessage) {
    Queue::shared_ptr queue(new Queue("my_test_queue", true));
    intrusive_ptr<Message> received;
    
    TestConsumer::shared_ptr c1(new TestConsumer());
    queue->consume(c1);
    
    
    //Test basic delivery:
    intrusive_ptr<Message> msg1 = message("e", "A");
    msg1->enqueueAsync();//this is done on enqueue which is not called from process
    queue->process(msg1);
    sleep(2);
    
    BOOST_CHECK(!c1->received);
    msg1->enqueueComplete();
    
    received = queue->get().payload;
    BOOST_CHECK_EQUAL(msg1.get(), received.get());    
}
    
    
QPID_AUTO_TEST_CASE(testAsyncMessageCount){
    Queue::shared_ptr queue(new Queue("my_test_queue", true));
    intrusive_ptr<Message> msg1 = message("e", "A");
    msg1->enqueueAsync();//this is done on enqueue which is not called from process
    
    queue->process(msg1);
    sleep(2);
    uint32_t compval=0;
    BOOST_CHECK_EQUAL(compval, queue->getMessageCount());
    msg1->enqueueComplete();
    compval=1;
    BOOST_CHECK_EQUAL(compval, queue->getMessageCount());    
}

QPID_AUTO_TEST_CASE(testConsumers){
    Queue::shared_ptr queue(new Queue("my_queue", true));
    
    //Test adding consumers:
    TestConsumer::shared_ptr c1(new TestConsumer());
    TestConsumer::shared_ptr c2(new TestConsumer());
    queue->consume(c1);
    queue->consume(c2);
    
    BOOST_CHECK_EQUAL(uint32_t(2), queue->getConsumerCount());
    
    //Test basic delivery:
    intrusive_ptr<Message> msg1 = message("e", "A");
    intrusive_ptr<Message> msg2 = message("e", "B");
    intrusive_ptr<Message> msg3 = message("e", "C");
    
    queue->deliver(msg1);
    BOOST_CHECK(queue->dispatch(c1));
    BOOST_CHECK_EQUAL(msg1.get(), c1->last.get());
    
    queue->deliver(msg2);
    BOOST_CHECK(queue->dispatch(c2));
    BOOST_CHECK_EQUAL(msg2.get(), c2->last.get());
    
    c1->received = false;
    queue->deliver(msg3);
    BOOST_CHECK(queue->dispatch(c1));
    BOOST_CHECK_EQUAL(msg3.get(), c1->last.get());        
    
    //Test cancellation:
    queue->cancel(c1);
    BOOST_CHECK_EQUAL(uint32_t(1), queue->getConsumerCount());
    queue->cancel(c2);
    BOOST_CHECK_EQUAL(uint32_t(0), queue->getConsumerCount());
}

QPID_AUTO_TEST_CASE(testRegistry){
    //Test use of queues in registry:
    QueueRegistry registry;
    registry.declare("queue1", true, true);
    registry.declare("queue2", true, true);
    registry.declare("queue3", true, true);
    
    BOOST_CHECK(registry.find("queue1"));
    BOOST_CHECK(registry.find("queue2"));
    BOOST_CHECK(registry.find("queue3"));
    
    registry.destroy("queue1");
    registry.destroy("queue2");
    registry.destroy("queue3");
    
    BOOST_CHECK(!registry.find("queue1"));
    BOOST_CHECK(!registry.find("queue2"));
    BOOST_CHECK(!registry.find("queue3"));
}

QPID_AUTO_TEST_CASE(testDequeue){
    Queue::shared_ptr queue(new Queue("my_queue", true));
    intrusive_ptr<Message> msg1 = message("e", "A");
    intrusive_ptr<Message> msg2 = message("e", "B");
    intrusive_ptr<Message> msg3 = message("e", "C");
    intrusive_ptr<Message> received;
    
    queue->deliver(msg1);
    queue->deliver(msg2);
    queue->deliver(msg3);
    
    BOOST_CHECK_EQUAL(uint32_t(3), queue->getMessageCount());
    
    received = queue->get().payload;
    BOOST_CHECK_EQUAL(msg1.get(), received.get());
    BOOST_CHECK_EQUAL(uint32_t(2), queue->getMessageCount());

    received = queue->get().payload;
    BOOST_CHECK_EQUAL(msg2.get(), received.get());
    BOOST_CHECK_EQUAL(uint32_t(1), queue->getMessageCount());

    TestConsumer::shared_ptr consumer(new TestConsumer());
    queue->consume(consumer);
    queue->dispatch(consumer);
    if (!consumer->received)
        sleep(2);

    BOOST_CHECK_EQUAL(msg3.get(), consumer->last.get());
    BOOST_CHECK_EQUAL(uint32_t(0), queue->getMessageCount());

    received = queue->get().payload;
    BOOST_CHECK(!received);
    BOOST_CHECK_EQUAL(uint32_t(0), queue->getMessageCount());
        
}

QPID_AUTO_TEST_CASE(testBound)
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

QPID_AUTO_TEST_CASE(testPersistLastNodeStanding){

    FieldTable args;

    // set queue mode
	args.setInt("qpid.persist_last_node", 1);
	
	Queue::shared_ptr queue(new Queue("my-queue", true));
    queue->configure(args);
	
    intrusive_ptr<Message> msg1 = message("e", "A");
    intrusive_ptr<Message> msg2 = message("e", "B");
    intrusive_ptr<Message> msg3 = message("e", "C");

	//enqueue 2 messages
    queue->deliver(msg1);
    queue->deliver(msg2);
	
	//change mode
	queue->setLastNodeFailure();
	
	//enqueue 1 message
    queue->deliver(msg3);
	
	//check all have persistent ids.
    BOOST_CHECK(msg1->isPersistent());
    BOOST_CHECK(msg2->isPersistent());
    BOOST_CHECK(msg3->isPersistent());

}

class TestMessageStoreOC : public NullMessageStore
{
  public:
    
    virtual void dequeue(TransactionContext*,
                 const boost::intrusive_ptr<PersistableMessage>& /*msg*/,
                 const PersistableQueue& /*queue*/)
    {
    }

    virtual void enqueue(TransactionContext*,
                 const boost::intrusive_ptr<PersistableMessage>& /*msg*/,
                 const PersistableQueue& /* queue */)
    {
    }

    TestMessageStoreOC() : NullMessageStore(false) {}
    ~TestMessageStoreOC(){}
};


QPID_AUTO_TEST_CASE(testOptimisticConsume){

    FieldTable args;
	args.setInt("qpid.persist_last_node", 1);

    // set queue mode
	
	TestMessageStoreOC store;
	Queue::shared_ptr queue(new Queue("my-queue", true, &store));
	queue->setLastNodeFailure();
	
    intrusive_ptr<Message> msg1 = message("e", "A");
	msg1->forcePersistent();

	//change mode
	args.setInt("qpid.optimistic_consume", 1);
    queue->configure(args);
	
	//enqueue 1 message
    queue->deliver(msg1);
	
    TestConsumer::shared_ptr consumer(new TestConsumer());
    queue->consume(consumer);
    queue->dispatch(consumer);
    if (!consumer->received)
        sleep(2);

    BOOST_CHECK_EQUAL(msg1.get(), consumer->last.get());
    BOOST_CHECK_EQUAL(uint32_t(0), queue->getMessageCount());

}


QPID_AUTO_TEST_SUITE_END()


