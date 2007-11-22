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
#include "qpid/broker/TxAck.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <list>
#include <vector>
#include "MockChannel.h"

using std::list;
using std::vector;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;

class TxAckTest : public CppUnit::TestCase  
{

    class TestMessageStore : public NullMessageStore
    {
    public:
        vector<PersistableMessage*> dequeued;

        void dequeue(TransactionContext*, PersistableMessage& msg, const PersistableQueue& /*queue*/)
        {
            dequeued.push_back(&msg);
        }

        TestMessageStore() : NullMessageStore() {}
        ~TestMessageStore(){}
    };

    CPPUNIT_TEST_SUITE(TxAckTest);
    CPPUNIT_TEST(testPrepare);
    CPPUNIT_TEST(testCommit);
    CPPUNIT_TEST_SUITE_END();


    AccumulatedAck acked;
    TestMessageStore store;
    Queue::shared_ptr queue;
    vector<intrusive_ptr<Message> > messages;
    list<DeliveryRecord> deliveries;
    TxAck op;


public:

    TxAckTest() : acked(0), queue(new Queue("my_queue", false, &store, 0)), op(acked, deliveries)
    {
        for(int i = 0; i < 10; i++){
            intrusive_ptr<Message> msg(new Message());
            AMQFrame method(in_place<MessageTransferBody>(
                                ProtocolVersion(), 0, "exchange", 0, 0));
            AMQFrame header(in_place<AMQHeaderBody>());
            msg->getFrames().append(method);
            msg->getFrames().append(header);
            msg->getProperties<DeliveryProperties>()->setDeliveryMode(PERSISTENT);
            msg->getProperties<DeliveryProperties>()->setRoutingKey("routing_key");
            messages.push_back(msg);
            QueuedMessage qm(queue.get());
            qm.payload = msg;
            deliveries.push_back(DeliveryRecord(qm, queue, "xyz", DeliveryToken::shared_ptr(), (i+1), true));
        }

        //assume msgs 1-5, 7 and 9 are all acked (i.e. 6, 8 & 10 are not)
        acked.mark = 5;
        acked.update(7, 7);
        acked.update(9, 9);
    }      

    void testPrepare()
    {
        //ensure acked messages are discarded, i.e. dequeued from store
        op.prepare(0);
        CPPUNIT_ASSERT_EQUAL((size_t) 7, store.dequeued.size());
        CPPUNIT_ASSERT_EQUAL((size_t) 10, deliveries.size());
        int dequeued[] = {0, 1, 2, 3, 4, 6, 8};
        for (int i = 0; i < 7; i++) {
            CPPUNIT_ASSERT_EQUAL((PersistableMessage*) messages[dequeued[i]].get(), store.dequeued[i]);
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

