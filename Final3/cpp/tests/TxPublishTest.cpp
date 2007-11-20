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
#include <NullMessageStore.h>
#include <RecoveryManager.h>
#include <TxPublish.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <list>
#include <vector>

using std::list;
using std::pair;
using std::vector;
using namespace qpid::broker;
using namespace qpid::framing;

class TxPublishTest : public CppUnit::TestCase  
{
    struct Triple
    {
        string first;
        Message* second;
        const string* third;
    };

    class TestMessageStore : public NullMessageStore
    {
    public:
        vector<Triple> enqueued;
        
        void enqueue(TransactionContext*, Message* const msg, const Queue& queue, const string * const xid)
        {
            Triple args = {queue.getName(), msg, xid};
            enqueued.push_back(args);
        }
        
        //dont care about any of the other methods:
        TestMessageStore() : NullMessageStore(false) {}
        ~TestMessageStore(){}
    };
    
    CPPUNIT_TEST_SUITE(TxPublishTest);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testPrepare2pc);
    CPPUNIT_TEST(testCommit);
    CPPUNIT_TEST_SUITE_END();
    
    
    TestMessageStore store;
    Queue::shared_ptr queue1;
    Queue::shared_ptr queue2;
    Message::shared_ptr const msg;
    TxPublish op;
    string xid;
    
public:
    
    TxPublishTest() : queue1(new Queue("queue1", false, &store, 0)), 
                      queue2(new Queue("queue2", false, &store, 0)), 
                      msg(new Message(0, "exchange", "routing_key", false, false)),
                      op(msg, &xid)
    {
        msg->setHeader(AMQHeaderBody::shared_ptr(new AMQHeaderBody(BASIC)));
        msg->getHeaderProperties()->setDeliveryMode(PERSISTENT);
        op.deliverTo(queue1);
        op.deliverTo(queue2);
    }      

    void testPrepare()
    {
        //ensure messages are enqueued in store
        op.prepare(0);
        CPPUNIT_ASSERT_EQUAL((size_t) 2, store.enqueued.size());
        CPPUNIT_ASSERT_EQUAL(string("queue1"), store.enqueued[0].first);
        CPPUNIT_ASSERT_EQUAL(msg.get(), store.enqueued[0].second);
        CPPUNIT_ASSERT_EQUAL(string("queue2"), store.enqueued[1].first);
        CPPUNIT_ASSERT_EQUAL(msg.get(), store.enqueued[1].second);
    }

    void testPrepare2pc()
    {
        xid = "abcde";
        const string expected(xid);
        testPrepare();
        CPPUNIT_ASSERT_EQUAL(expected, *store.enqueued[0].third);
        CPPUNIT_ASSERT_EQUAL(expected, *store.enqueued[1].third);        
    }

    void testCommit()
    {
        //ensure messages are delivered to queue
        op.commit();
        CPPUNIT_ASSERT_EQUAL((u_int32_t) 1, queue1->getMessageCount());
        CPPUNIT_ASSERT_EQUAL(msg, queue1->dequeue());

        CPPUNIT_ASSERT_EQUAL((u_int32_t) 1, queue2->getMessageCount());
        CPPUNIT_ASSERT_EQUAL(msg, queue2->dequeue());            
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TxPublishTest);

