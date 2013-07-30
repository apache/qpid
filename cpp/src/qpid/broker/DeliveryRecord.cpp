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
#include "qpid/broker/DeliveryRecord.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/SemanticState.h"
#include "qpid/broker/Consumer.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/MessageTransferBody.h"

using namespace qpid;
using namespace qpid::broker;
using std::string;

DeliveryRecord::DeliveryRecord(const QueueCursor& _msg,
                               framing::SequenceNumber _msgId,
                               framing::SequenceNumber _replicationId,
                               const Queue::shared_ptr& _queue,
                               const std::string& _tag,
                               const boost::shared_ptr<Consumer>& _consumer,
                               bool _acquired,
                               bool accepted,
                               bool _windowing,
                               uint32_t _credit) : msg(_msg),
                                                   queue(_queue),
                                                   tag(_tag),
                                                   consumer(_consumer),
                                                   acquired(_acquired),
                                                   acceptExpected(!accepted),
                                                   cancelled(false),
                                                   completed(false),
                                                   ended(accepted && acquired),
                                                   windowing(_windowing),
                                                   credit(_credit),
                                                   msgId(_msgId),
                                                   replicationId(_replicationId)
{}

bool DeliveryRecord::setEnded()
{
    ended = true;
    QPID_LOG(debug, "DeliveryRecord::setEnded() id=" << id);
    return isRedundant();
}

void DeliveryRecord::requeue()
{
    if (acquired && !ended) {
        queue->release(msg);
    }
}

void DeliveryRecord::release(bool setRedelivered)
{
    if (acquired && !ended) {
        queue->release(msg, setRedelivered);
        acquired = false;
        setEnded();
    } else {
        QPID_LOG(debug, "Ignoring release for " << id << " acquired=" << acquired << ", ended =" << ended);
    }
}

void DeliveryRecord::complete()
{
    completed = true;
}

bool DeliveryRecord::accept(TransactionContext* ctxt) {
    if (!ended) {
        if (consumer) consumer->acknowledged(*this);
        if (acquired) queue->dequeue(ctxt, msg);
        setEnded();
        QPID_LOG(debug, "Accepted " << id);
    }
    return isRedundant();
}

void DeliveryRecord::dequeue(TransactionContext* ctxt) const
{
    if (acquired && !ended) {
        queue->dequeue(ctxt, msg);
    }
}

void DeliveryRecord::committed() const
{
    if (acquired && !ended) {
        queue->dequeueCommitted(msg);
    }
}

void DeliveryRecord::reject()
{
    if (acquired && !ended) {
        queue->reject(msg);
        setEnded();
    }
}

uint32_t DeliveryRecord::getCredit() const
{
    return credit;
}

void DeliveryRecord::acquire(DeliveryIds& results) {
    if (queue->acquire(msg, tag)) {
        acquired = true;
        results.push_back(id);
        if (!acceptExpected) {
            if (ended) { QPID_LOG(error, "Can't dequeue ended message"); }
            else { queue->dequeue(0, msg); setEnded(); }
        }
    } else {
        QPID_LOG(info, "Message already acquired " << id.getValue());
    }
}

void DeliveryRecord::cancel(const std::string& cancelledTag)
{
    if (tag == cancelledTag)
        cancelled = true;
}

AckRange DeliveryRecord::findRange(DeliveryRecords& records, DeliveryId first, DeliveryId last)
{
    DeliveryRecords::iterator start = lower_bound(records.begin(), records.end(), first);
    // Find end - position it just after the last record in range
    DeliveryRecords::iterator end = lower_bound(records.begin(), records.end(), last);
    if (end != records.end() && end->getId() == last) ++end;
    return AckRange(start, end);
}


namespace qpid {
namespace broker {

std::ostream& operator<<(std::ostream& out, const DeliveryRecord& r)
{
    out << "{" << "id=" << r.id.getValue();
    out << ", tag=" << r.tag << "}";
    out << ", queue=" << r.queue->getName() << "}";
    return out;
}


}}
