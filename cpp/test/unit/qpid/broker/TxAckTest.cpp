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
#include <qpid/broker/MessageStore.h>
#include <qpid/broker/QueueRegistry.h>
#include <qpid/broker/TxAck.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <list>
#include <vector>

using std::list;
using std::vector;
using namespace qpid::broker;
using namespace qpid::framing;

class TxAckTest : public CppUnit::TestCase  
{

    class TestMessageStore : public MessageStore
    {
    public:
        vector<Message::shared_ptr> dequeued;

        void dequeue(TransactionContext*, Message::shared_ptr& msg, const Queue& /*queue*/, const string * const /*xid*/)
        {
            dequeued.push_back(msg);
        }

        //dont care about any of the other methods:
        void create(const Queue&){}
        void destroy(const Queue&){}        
        void recover(QueueRegistry&){}
        void enqueue(TransactionContext*, Message::shared_ptr&, const Queue&, const string * const){}
        void committed(const string * const){}
        void aborted(const string * const){}
        std::auto_ptr<TransactionContext> begin(){ return std::auto_ptr<TransactionContext>(); }
        void commit(TransactionContext*){}
        void abort(TransactionContext*){}        
        ~TestMessageStore(){}
    };

    CPPUNIT_TEST_SUITE(TxAckTest);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testCommit);
    CPPUNIT_TEST_SUITE_END();


    AccumulatedAck acked;
    TestMessageStore store;
    Queue::shared_ptr queue;
    vector<Message::shared_ptr> messages;
    list<DeliveryRecord> deliveries;
    TxAck op;


public:

    TxAckTest() : queue(new Queue("my_queue", false, &store, 0)), op(acked, deliveries)
    {
        for(int i = 0; i < 10; i++){
            Message::shared_ptr msg(new Message(0, "exchange", "routing_key", false, false));
            msg->setHeader(AMQHeaderBody::shared_ptr(new AMQHeaderBody(BASIC)));
            msg->getHeaderProperties()->setDeliveryMode(PERSISTENT);
            messages.push_back(msg);
            deliveries.push_back(DeliveryRecord(msg, queue, "xyz", (i+1)));
        }

        //assume msgs 1-5, 7 and 9 are all acked (i.e. 6, 8 & 10 are not)
        acked.range = 5;
        acked.individual.push_back(7);
        acked.individual.push_back(9);
    }      

    void testPrepare()
    {
        //ensure acked messages are discarded, i.e. dequeued from store
        op.prepare(0);
        CPPUNIT_ASSERT_EQUAL((size_t) 7, store.dequeued.size());
        CPPUNIT_ASSERT_EQUAL((size_t) 10, deliveries.size());
        CPPUNIT_ASSERT_EQUAL(messages[0], store.dequeued[0]);//msg 1
        CPPUNIT_ASSERT_EQUAL(messages[1], store.dequeued[1]);//msg 2
        CPPUNIT_ASSERT_EQUAL(messages[2], store.dequeued[2]);//msg 3
        CPPUNIT_ASSERT_EQUAL(messages[3], store.dequeued[3]);//msg 4
        CPPUNIT_ASSERT_EQUAL(messages[4], store.dequeued[4]);//msg 5
        CPPUNIT_ASSERT_EQUAL(messages[6], store.dequeued[5]);//msg 7
        CPPUNIT_ASSERT_EQUAL(messages[8], store.dequeued[6]);//msg 9
    }

    void testCommit()
    {
        //emsure acked messages are removed from list
        op.commit();
        CPPUNIT_ASSERT_EQUAL((size_t) 3, deliveries.size());
        list<DeliveryRecord>::iterator i = deliveries.begin();
        CPPUNIT_ASSERT(i->matches(6));//msg 6
        CPPUNIT_ASSERT((++i)->matches(8));//msg 8
        CPPUNIT_ASSERT((++i)->matches(10));//msg 10
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(TxAckTest);

