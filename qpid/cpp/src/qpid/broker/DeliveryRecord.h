#ifndef QPID_BROKER_DELIVERYRECORD_H
#define QPID_BROKER_DELIVERYRECORD_H

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

#include <algorithm>
#include <deque>
#include <vector>
#include <ostream>
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/SequenceSet.h"
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/QueueCursor.h"
#include "qpid/broker/DeliveryId.h"
#include "qpid/broker/Message.h"

namespace qpid {
namespace broker {

class Queue;
class TransactionContext;
class SemanticState;
struct AckRange;
class Consumer;

/**
 * Record of a delivery for which an ack is outstanding.
 */
class DeliveryRecord
{
    QueueCursor msg;
    mutable boost::shared_ptr<Queue> queue;
    std::string tag;    // name of consumer
    boost::shared_ptr<Consumer> consumer;
    DeliveryId id;
    bool acquired : 1;
    bool acceptExpected : 1;
    bool cancelled : 1;
    bool completed : 1;
    bool ended : 1;
    bool windowing : 1;

    /**
     * Record required credit on construction as the pointer to the
     * message may be reset once we no longer need to deliver it
     * (e.g. when it is accepted), but we will still need to be able
     * to reallocate credit when it is completed (which could happen
     * after that).
     */
    uint32_t credit;
    framing::SequenceNumber msgId;
    framing::SequenceNumber replicationId;

  public:
    QPID_BROKER_EXTERN DeliveryRecord(const QueueCursor& msgCursor,
                                      framing::SequenceNumber msgId,
                                      framing::SequenceNumber replicationId,
                                      const boost::shared_ptr<Queue>& queue, 
                                      const std::string& tag,
                                      const boost::shared_ptr<Consumer>& consumer,
                                      bool acquired,
                                      bool accepted,
                                      bool windowing,
                                      uint32_t credit=0 // Only used if msg is empty.
    );

    bool coveredBy(const framing::SequenceSet* const range) const { return range->contains(id); }

    void dequeue(TransactionContext* ctxt = 0) const;
    void requeue();
    void release(bool setRedelivered);
    void reject();
    void cancel(const std::string& tag);
    void acquire(DeliveryIds& results);
    void complete();
    bool accept(TransactionContext* ctxt); // Returns isRedundant()
    bool setEnded();            // Returns isRedundant()
    void committed() const;

    bool isAcquired() const { return acquired; }
    bool isComplete() const { return completed; }
    bool isRedundant() const { return ended && (!windowing || completed || cancelled); }
    bool isCancelled() const { return cancelled; }
    bool isAccepted() const { return !acceptExpected; }
    bool isEnded() const { return ended; }
    bool isWindowing() const { return windowing; }

    uint32_t getCredit() const;
    const std::string& getTag() const { return tag; }

    void setId(DeliveryId _id) { id = _id; }

    typedef std::deque<DeliveryRecord> DeliveryRecords;
    static AckRange findRange(DeliveryRecords& records, DeliveryId first, DeliveryId last);
    const QueueCursor& getMessage() const { return msg; }
    framing::SequenceNumber getId() const { return id; }
    framing::SequenceNumber getMessageId() const { return msgId; }
    framing::SequenceNumber getReplicationId() const { return replicationId; }
    boost::shared_ptr<Queue> getQueue() const { return queue; }

    friend std::ostream& operator<<(std::ostream&, const DeliveryRecord&);
};

inline bool operator<(const DeliveryRecord& a, const DeliveryRecord& b) { return a.getId() < b.getId(); }
inline bool operator<(const framing::SequenceNumber& a, const DeliveryRecord& b) { return a < b.getId(); }
inline bool operator<(const DeliveryRecord& a, const framing::SequenceNumber& b) { return a.getId() < b; }

struct AcquireFunctor
{
    DeliveryIds& results;

    AcquireFunctor(DeliveryIds& _results) : results(_results) {}

    void operator()(DeliveryRecord& record)
    {
        record.acquire(results);
    }
};

typedef DeliveryRecord::DeliveryRecords DeliveryRecords;

struct AckRange
{
    DeliveryRecords::iterator start;
    DeliveryRecords::iterator end;
    AckRange(DeliveryRecords::iterator _start, DeliveryRecords::iterator _end) : start(_start), end(_end) {}
};

}
}


#endif  /*!QPID_BROKER_DELIVERYRECORD_H*/
