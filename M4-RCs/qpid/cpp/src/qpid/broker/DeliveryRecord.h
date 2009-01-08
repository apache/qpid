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
#include "qpid/framing/SequenceSet.h"
#include "Queue.h"
#include "QueuedMessage.h"
#include "DeliveryId.h"
#include "Message.h"

namespace qpid {
namespace broker {
class SemanticState;
class DeliveryRecord;

typedef std::list<DeliveryRecord> DeliveryRecords; 
typedef std::list<DeliveryRecord>::iterator ack_iterator; 

struct AckRange
{
    ack_iterator start;
    ack_iterator end;    
    AckRange(ack_iterator _start, ack_iterator _end) : start(_start), end(_end) {}
};


/**
 * Record of a delivery for which an ack is outstanding.
 */
class DeliveryRecord
{
    QueuedMessage msg;
    mutable Queue::shared_ptr queue;
    const std::string tag;
    DeliveryId id;
    bool acquired;
    bool acceptExpected;
    bool cancelled;

    bool completed;
    bool ended;
    const bool windowing;

    /**
     * Record required credit on construction as the pointer to the
     * message may be reset once we no longer need to deliver it
     * (e.g. when it is accepted), but we will still need to be able
     * to reallocate credit when it is completed (which could happen
     * after that).
     */
    const uint32_t credit;

  public:
    DeliveryRecord(
        const QueuedMessage& msg,
        const Queue::shared_ptr& queue, 
        const std::string& tag,
        bool acquired,
        bool accepted,
        bool windowing,
        uint32_t credit=0       // Only used if msg is empty.
    );
    
    bool matches(DeliveryId tag) const;
    bool matchOrAfter(DeliveryId tag) const;
    bool after(DeliveryId tag) const;
    bool coveredBy(const framing::SequenceSet* const range) const;
    
    void dequeue(TransactionContext* ctxt = 0) const;
    void requeue() const;
    void release(bool setRedelivered);
    void reject();
    void cancel(const std::string& tag);
    void redeliver(SemanticState* const);
    void acquire(DeliveryIds& results);
    void complete();
    void accept(TransactionContext* ctxt);
    void setEnded();
    void committed() const;

    bool isAcquired() const { return acquired; }
    bool isComplete() const { return completed; }
    bool isRedundant() const { return ended && (!windowing || completed); }
    bool isCancelled() const { return cancelled; }
    bool isAccepted() const { return !acceptExpected; }
    bool isEnded() const { return ended; }
    bool isWindowing() const { return windowing; }
    
    uint32_t getCredit() const;
    const std::string& getTag() const { return tag; }

    void deliver(framing::FrameHandler& h, DeliveryId deliveryId, uint16_t framesize);
    void setId(DeliveryId _id) { id = _id; }

    static AckRange findRange(DeliveryRecords& records, DeliveryId first, DeliveryId last);
    const QueuedMessage& getMessage() const { return msg; }
    framing::SequenceNumber getId() const { return id; }
    Queue::shared_ptr getQueue() const { return queue; }
    friend bool operator<(const DeliveryRecord&, const DeliveryRecord&);         
    friend std::ostream& operator<<(std::ostream&, const DeliveryRecord&);
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
