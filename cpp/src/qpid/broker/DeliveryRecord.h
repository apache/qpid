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
#include "Queue.h"
#include "Consumer.h"
#include "DeliveryId.h"
#include "DeliveryToken.h"
#include "Message.h"
#include "Prefetch.h"

namespace qpid {
namespace broker {
class SemanticState;

/**
 * Record of a delivery for which an ack is outstanding.
 */
class DeliveryRecord{
    QueuedMessage msg;
    mutable Queue::shared_ptr queue;
    const std::string tag;
    DeliveryToken::shared_ptr token;
    DeliveryId id;
    bool acquired;
    const bool pull;
    bool cancelled;
    const uint32_t credit;
    const uint64_t size;

    bool completed;
    bool ended;

    void setEnded();

  public:
    DeliveryRecord(const QueuedMessage& msg, Queue::shared_ptr queue, const std::string tag, DeliveryToken::shared_ptr token, 
                   const DeliveryId id, bool acquired, bool confirmed = false);
    DeliveryRecord(const QueuedMessage& msg, Queue::shared_ptr queue, const DeliveryId id);
            
    bool matches(DeliveryId tag) const;
    bool matchOrAfter(DeliveryId tag) const;
    bool after(DeliveryId tag) const;
    bool coveredBy(const framing::AccumulatedAck* const range) const;

    void dequeue(TransactionContext* ctxt = 0) const;
    void requeue() const;
    void release();
    void reject();
    void cancel(const std::string& tag);
    void redeliver(SemanticState* const);
    void acquire(DeliveryIds& results);
    void complete();
    void accept(TransactionContext* ctxt);

    bool isAcquired() const { return acquired; }
    bool isComplete() const { return completed; }
    bool isRedundant() const { return ended && completed; }

    uint32_t getCredit() const;
    void addTo(Prefetch&) const;
    void subtractFrom(Prefetch&) const;
    const std::string& getTag() const { return tag; } 
    bool isPull() const { return pull; }
    friend bool operator<(const DeliveryRecord&, const DeliveryRecord&);         
    friend std::ostream& operator<<(std::ostream&, const DeliveryRecord&);
};

typedef std::list<DeliveryRecord> DeliveryRecords; 
typedef std::list<DeliveryRecord>::iterator ack_iterator; 

struct AckRange
{
    ack_iterator start;
    ack_iterator end;    
    AckRange(ack_iterator _start, ack_iterator _end) : start(_start), end(_end) {}
};

struct AcquireFunctor
{
    DeliveryIds& results;

    AcquireFunctor(DeliveryIds& _results) : results(_results) {}

    void operator()(DeliveryRecord& record)
    {
        record.acquire(results);
    }
};

}
}


#endif
