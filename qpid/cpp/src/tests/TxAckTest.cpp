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
#include "MessageUtils.h"
#include "qpid/broker/NullMessageStore.h"
#include "qpid/broker/RecoveryManager.h"
#include "qpid/broker/TxAck.h"
#include "TestMessageStore.h"
#include "unit_test.h"
#include <iostream>
#include <list>
#include <vector>

using std::list;
using std::vector;
using boost::intrusive_ptr;
using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;

QPID_AUTO_TEST_SUITE(TxAckTestSuite)

struct TxAckTest
{
    AccumulatedAck acked;
    TestMessageStore store;
    Queue::shared_ptr queue;
    vector<intrusive_ptr<Message> > messages;
    list<DeliveryRecord> deliveries;
    TxAck op;

    TxAckTest() : acked(0), queue(new Queue("my_queue", false, &store, 0)), op(acked, deliveries)
    {
        for(int i = 0; i < 10; i++){
            intrusive_ptr<Message> msg(MessageUtils::createMessage("exchange", "routing_key"));
            msg->getProperties<DeliveryProperties>()->setDeliveryMode(PERSISTENT);
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

};

QPID_AUTO_TEST_CASE(testPrepare)
{
    TxAckTest t;

    //ensure acked messages are discarded, i.e. dequeued from store
    t.op.prepare(0);
    BOOST_CHECK_EQUAL((size_t) 7, t.store.dequeued.size());
    BOOST_CHECK_EQUAL((size_t) 10, t.deliveries.size());
    int dequeued[] = {0, 1, 2, 3, 4, 6, 8};
    for (int i = 0; i < 7; i++) {
        BOOST_CHECK_EQUAL(static_pointer_cast<PersistableMessage>(t.messages[dequeued[i]]), t.store.dequeued[i]);
    }
}

QPID_AUTO_TEST_CASE(testCommit)
{
    TxAckTest t;

    //ensure acked messages are removed from list
    t.op.commit();
    BOOST_CHECK_EQUAL((size_t) 3, t.deliveries.size());
    list<DeliveryRecord>::iterator i = t.deliveries.begin();
    BOOST_CHECK(i->matches(6));//msg 6
    BOOST_CHECK((++i)->matches(8));//msg 8
    BOOST_CHECK((++i)->matches(10));//msg 10
}

QPID_AUTO_TEST_SUITE_END()
