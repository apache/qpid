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
#ifndef _DeliveryRecord_
#define _DeliveryRecord_

#include <algorithm>
#include <list>
#include <vector>
#include <ostream>
#include "qpid/framing/AccumulatedAck.h"
#include "BrokerQueue.h"
#include "Consumer.h"
#include "DeliveryId.h"
#include "Message.h"
#include "Prefetch.h"

namespace qpid {
namespace broker {
class Session;

/**
 * Record of a delivery for which an ack is outstanding.
 */
class DeliveryRecord{
    mutable QueuedMessage msg;
    mutable Queue::shared_ptr queue;
    const std::string consumerTag;
    const DeliveryId id;
    bool acquired;
    const bool pull;

  public:
    DeliveryRecord(QueuedMessage& msg, Queue::shared_ptr queue, const std::string consumerTag, 
                   const DeliveryId id, bool acquired);
    DeliveryRecord(QueuedMessage& msg, Queue::shared_ptr queue, const DeliveryId id);
            
    void dequeue(TransactionContext* ctxt = 0) const;
    bool matches(DeliveryId tag) const;
    bool matchOrAfter(DeliveryId tag) const;
    bool after(DeliveryId tag) const;
    bool coveredBy(const framing::AccumulatedAck* const range) const;
    void requeue() const;
    void release();
    void reject();
    void redeliver(Session* const) const;
    void updateByteCredit(uint32_t& credit) const;
    void addTo(Prefetch&) const;
    void subtractFrom(Prefetch&) const;
    const std::string& getConsumerTag() const { return consumerTag; } 
    bool isPull() const { return pull; }
    bool isAcquired() const { return acquired; }
    //void setAcquired(bool isAcquired) { acquired = isAcquired; }
    void acquire(std::vector<DeliveryId>& results);
            
  friend std::ostream& operator<<(std::ostream&, const DeliveryRecord&);
};

typedef std::list<DeliveryRecord>::iterator ack_iterator; 

struct AckRange
{
    ack_iterator start;
    ack_iterator end;    
    AckRange(ack_iterator _start, ack_iterator _end) : start(_start), end(_end) {}
};

struct AcquireFunctor
{
    std::vector<DeliveryId>& results;

    AcquireFunctor(std::vector<DeliveryId>& _results) : results(_results) {}

    void operator()(DeliveryRecord& record)
    {
        record.acquire(results);
    }
};

}
}


#endif
