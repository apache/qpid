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
#include "Queue.h"
#include "QueueRegistry.h"
#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>
#include <iostream>

using namespace qpid::broker;
using namespace qpid::concurrent;


class TestBinding : public virtual Binding{
    bool cancelled;

public:
    TestBinding();
    virtual void cancel();
    bool isCancelled();
};

class TestConsumer : public virtual Consumer{
public:
    Message::shared_ptr last;

    virtual bool deliver(Message::shared_ptr& msg);
};


class QueueTest : public CppUnit::TestCase  
{
    CPPUNIT_TEST_SUITE(QueueTest);
    CPPUNIT_TEST(testMe);
    CPPUNIT_TEST_SUITE_END();

  public:
    void testMe() 
    {
        Queue::shared_ptr queue(new Queue("my_queue", true, true));
    
        //Test adding consumers:
        TestConsumer c1; 
        TestConsumer c2; 
        queue->consume(&c1);
        queue->consume(&c2);

        CPPUNIT_ASSERT_EQUAL(u_int32_t(2), queue->getConsumerCount());
        
        //Test basic delivery:
        Message::shared_ptr msg1 = Message::shared_ptr(new Message(0, "e", "A", true, true));
        Message::shared_ptr msg2 = Message::shared_ptr(new Message(0, "e", "B", true, true));
        Message::shared_ptr msg3 = Message::shared_ptr(new Message(0, "e", "C", true, true));

        queue->deliver(msg1);
        CPPUNIT_ASSERT_EQUAL(msg1.get(), c1.last.get());

        queue->deliver(msg2);
        CPPUNIT_ASSERT_EQUAL(msg2.get(), c2.last.get());
        
        queue->deliver(msg3);
        CPPUNIT_ASSERT_EQUAL(msg3.get(), c1.last.get());        
    
        //Test cancellation:
        queue->cancel(&c1);
        CPPUNIT_ASSERT_EQUAL(u_int32_t(1), queue->getConsumerCount());
        queue->cancel(&c2);
        CPPUNIT_ASSERT_EQUAL(u_int32_t(0), queue->getConsumerCount());

        //Test bindings:
        TestBinding a;
        TestBinding b;
        queue->bound(&a);
        queue->bound(&b);    
    
        queue.reset();

        CPPUNIT_ASSERT(a.isCancelled());
        CPPUNIT_ASSERT(b.isCancelled());

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
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(QueueTest);

//TestBinding
TestBinding::TestBinding() : cancelled(false) {}

void TestBinding::cancel(){
    CPPUNIT_ASSERT(!cancelled);
    cancelled = true;
}

bool TestBinding::isCancelled(){
    return cancelled;
}

//TestConsumer
bool TestConsumer::deliver(Message::shared_ptr& msg){
    last = msg;
    return true;
}

