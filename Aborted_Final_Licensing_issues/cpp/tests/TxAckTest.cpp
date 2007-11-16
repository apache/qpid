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
#include <TxAck.h>
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

    class TestMessageStore : public NullMessageStore
    {
    public:
        vector< std::pair<Message*, const string*> > dequeued;

        void dequeue(TransactionContext*, Message* const msg, const Queue& /*queue*/, const string * const xid)
        {
            dequeued.push_back(std::pair<Message*, const string*>(msg, xid));
        }

        TestMessageStore() : NullMessageStore(false) {}
        ~TestMessageStore(){}
    };

    CPPUNIT_TEST_SUITE(TxAckTest);
    CPPUNIT_TEST(testPrepare2pc);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testCommit);
    CPPUNIT_TEST_SUITE_END();


    AccumulatedAck acked;
    TestMessageStore store;
    Queue::shared_ptr queue;
    vector<Message::shared_ptr> messages;
    list<DeliveryRecord> deliveries;
    TxAck op;
    std::string xid;


public:

    TxAckTest() : acked(0), queue(new Queue("my_queue", false, &store, 0)), op(acked, deliveries, &xid)
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
        int dequeued[] = {0, 1, 2, 3, 4, 6, 8};
        for (int i = 0; i < 7; i++) {
            CPPUNIT_ASSERT_EQUAL(messages[dequeued[i]].get(), store.dequeued[i].first);
        }
    }

    void testPrepare2pc()
    {
        xid = "abcdefg";
        testPrepare();
        const string expected(xid);
        for (int i = 0; i < 7; i++) {
            CPPUNIT_ASSERT_EQUAL(expected, *store.dequeued[i].second);
        }
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

