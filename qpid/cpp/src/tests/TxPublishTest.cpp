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
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/RecoveryManager.h"
#include "qpid/broker/TxPublish.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <list>
#include <vector>
#include "MessageUtils.h"

using std::list;
using std::pair;
using std::vector;
using namespace qpid::broker;
using namespace qpid::framing;

class TxPublishTest : public CppUnit::TestCase  
{
    typedef std::pair<string, intrusive_ptr<PersistableMessage> > msg_queue_pair;

    class TestMessageStore : public NullMessageStore
    {
    public:
        vector<msg_queue_pair> enqueued;
        
        void enqueue(TransactionContext*, intrusive_ptr<PersistableMessage>& msg, const PersistableQueue& queue)
        {
            msg->enqueueComplete(); 
 	        enqueued.push_back(msg_queue_pair(queue.getName(), msg));
        }
        
        //dont care about any of the other methods:
        TestMessageStore() : NullMessageStore(false) {}
        ~TestMessageStore(){}
    };
    
    CPPUNIT_TEST_SUITE(TxPublishTest);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testCommit);
    CPPUNIT_TEST_SUITE_END();
    
    
    TestMessageStore store;
    Queue::shared_ptr queue1;
    Queue::shared_ptr queue2;
    intrusive_ptr<Message> msg;
    TxPublish op;
    
public:
    
    TxPublishTest() :
        queue1(new Queue("queue1", false, &store, 0)), 
        queue2(new Queue("queue2", false, &store, 0)), 
        msg(MessageUtils::createMessage("exchange", "routing_key", "id")),
        op(msg)
    {
        msg->getProperties<DeliveryProperties>()->setDeliveryMode(PERSISTENT);
        op.deliverTo(queue1);
        op.deliverTo(queue2);
    }      

    void testPrepare()
    {
        intrusive_ptr<PersistableMessage> pmsg = static_pointer_cast<PersistableMessage>(msg);
        //ensure messages are enqueued in store
        op.prepare(0);
        CPPUNIT_ASSERT_EQUAL((size_t) 2, store.enqueued.size());
        CPPUNIT_ASSERT_EQUAL(string("queue1"), store.enqueued[0].first);
        CPPUNIT_ASSERT_EQUAL(pmsg, store.enqueued[0].second);
        CPPUNIT_ASSERT_EQUAL(string("queue2"), store.enqueued[1].first);
        CPPUNIT_ASSERT_EQUAL(pmsg, store.enqueued[1].second);
	    CPPUNIT_ASSERT_EQUAL( true, ( static_pointer_cast<PersistableMessage>(msg))->isEnqueueComplete());
    }

    void testCommit()
    {
        //ensure messages are delivered to queue
        op.prepare(0);
        op.commit();
        CPPUNIT_ASSERT_EQUAL((uint32_t) 1, queue1->getMessageCount());
	intrusive_ptr<Message> msg_dequeue = queue1->dequeue().payload;

 	CPPUNIT_ASSERT_EQUAL( true, (static_pointer_cast<PersistableMessage>(msg_dequeue))->isEnqueueComplete());
        CPPUNIT_ASSERT_EQUAL(msg, msg_dequeue);

        CPPUNIT_ASSERT_EQUAL((uint32_t) 1, queue2->getMessageCount());
        CPPUNIT_ASSERT_EQUAL(msg, queue2->dequeue().payload);            
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TxPublishTest);

