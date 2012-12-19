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
#include "qpid/broker/QueueFlowLimit.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/Exception.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include "qpid/broker/SessionState.h"

#include "qmf/org/apache/qpid/broker/Queue.h"

#include <sstream>

using namespace qpid::broker;
using namespace qpid::framing;

namespace {
    /** ensure that the configured flow control stop and resume values are
     * valid with respect to the maximum queue capacity, and each other
     */
    template <typename T>
    void validateFlowConfig(T max, T& stop, T& resume, const std::string& type, const std::string& queue)
    {
        if (resume > stop) {
            throw InvalidArgumentException(QPID_MSG("Queue \"" << queue << "\": qpid.flow_resume_" << type
                                                    << "=" << resume
                                                    << " must be less than qpid.flow_stop_" << type
                                                    << "=" << stop));
        }
        if (resume == 0) resume = stop;
        if (max != 0 && (max < stop)) {
            throw InvalidArgumentException(QPID_MSG("Queue \"" << queue << "\": qpid.flow_stop_" << type
                                                    << "=" << stop
                                                    << " must be less than qpid.max_" << type
                                                    << "=" << max));
        }
    }
}



QueueFlowLimit::QueueFlowLimit(Queue *_queue,
                               uint32_t _flowStopCount, uint32_t _flowResumeCount,
                               uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : StatefulQueueObserver(std::string("QueueFlowLimit")), queue(_queue), queueName("<unknown>"),
      flowStopCount(_flowStopCount), flowResumeCount(_flowResumeCount),
      flowStopSize(_flowStopSize), flowResumeSize(_flowResumeSize),
      flowStopped(false), count(0), size(0), broker(0)
{
    uint32_t maxCount(0);
    uint64_t maxSize(0);

    if (queue) {
        queueName = _queue->getName();
        if (queue->getSettings().maxDepth.hasCount()) maxCount = queue->getSettings().maxDepth.getCount();
        if (queue->getSettings().maxDepth.hasCount()) maxSize = queue->getSettings().maxDepth.getSize();
        broker = queue->getBroker();
        queueMgmtObj = boost::dynamic_pointer_cast<_qmfBroker::Queue> (queue->GetManagementObject());
        if (queueMgmtObj) {
            queueMgmtObj->set_flowStopped(isFlowControlActive());
        }
    }
    validateFlowConfig( maxCount, flowStopCount, flowResumeCount, "count", queueName );
    validateFlowConfig( maxSize, flowStopSize, flowResumeSize, "size", queueName );
    QPID_LOG(info, "Queue \"" << queueName << "\": Flow limit created: flowStopCount=" << flowStopCount
             << ", flowResumeCount=" << flowResumeCount
             << ", flowStopSize=" << flowStopSize << ", flowResumeSize=" << flowResumeSize );
}


QueueFlowLimit::~QueueFlowLimit()
{
    sys::Mutex::ScopedLock l(indexLock);
    if (!index.empty()) {
        // we're gone - release all pending msgs
        for (std::map<framing::SequenceNumber, Message >::iterator itr = index.begin();
             itr != index.end(); ++itr)
            if (itr->second)
                try {
                    itr->second.getPersistentContext()->getIngressCompletion().finishCompleter();
                } catch (...) {}    // ignore - not safe for a destructor to throw.
        index.clear();
    }
}


void QueueFlowLimit::enqueued(const Message& msg)
{
    sys::Mutex::ScopedLock l(indexLock);

    ++count;
    size += msg.getContentSize();

    if (!flowStopped) {
        if (flowStopCount && count > flowStopCount) {
            flowStopped = true;
            QPID_LOG(info, "Queue \"" << queueName << "\": has reached " << flowStopCount << " enqueued messages. Producer flow control activated." );
        } else if (flowStopSize && size > flowStopSize) {
            flowStopped = true;
            QPID_LOG(info, "Queue \"" << queueName << "\": has reached " << flowStopSize << " enqueued bytes. Producer flow control activated." );
        }
        if (flowStopped && queueMgmtObj) {
            queueMgmtObj->set_flowStopped(true);
            queueMgmtObj->inc_flowStoppedCount();
        }
    }

    if (flowStopped || !index.empty()) {
        QPID_LOG(trace, "Queue \"" << queueName << "\": setting flow control for msg pos=" << msg.getSequence());
        msg.getPersistentContext()->getIngressCompletion().startCompleter();    // don't complete until flow resumes
        bool unique;
        unique = index.insert(std::pair<framing::SequenceNumber, Message >(msg.getSequence(), msg)).second;
        // Like this to avoid tripping up unused variable warning when NDEBUG set
        if (!unique) assert(unique);
    }
}



void QueueFlowLimit::dequeued(const Message& msg)
{
    sys::Mutex::ScopedLock l(indexLock);

    if (count > 0) {
        --count;
    } else {
        throw Exception(QPID_MSG("Flow limit count underflow on dequeue. Queue=" << queueName));
    }

    uint64_t _size = msg.getContentSize();
    if (_size <= size) {
        size -= _size;
    } else {
        throw Exception(QPID_MSG("Flow limit size underflow on dequeue. Queue=" << queueName));
    }

    if (flowStopped &&
        (flowResumeSize == 0 || size < flowResumeSize) &&
        (flowResumeCount == 0 || count < flowResumeCount)) {
        flowStopped = false;
        if (queueMgmtObj)
            queueMgmtObj->set_flowStopped(false);
        QPID_LOG(info, "Queue \"" << queueName << "\": has drained below the flow control resume level. Producer flow control deactivated." );
    }

    if (!index.empty()) {
        if (!flowStopped) {
            // flow enabled - release all pending msgs
            for (std::map<framing::SequenceNumber, Message >::iterator itr = index.begin();
                 itr != index.end(); ++itr)
                if (itr->second)
                    itr->second.getPersistentContext()->getIngressCompletion().finishCompleter();
            index.clear();
        } else {
            // even if flow controlled, we must release this msg as it is being dequeued
            std::map<framing::SequenceNumber, Message >::iterator itr = index.find(msg.getSequence());
            if (itr != index.end()) {       // this msg is flow controlled, release it:
                msg.getPersistentContext()->getIngressCompletion().finishCompleter();
                index.erase(itr);
            }
        }
    }
}


void QueueFlowLimit::encode(Buffer& buffer) const
{
  buffer.putLong(flowStopCount);
  buffer.putLong(flowResumeCount);
  buffer.putLongLong(flowStopSize);
  buffer.putLongLong(flowResumeSize);
  buffer.putLong(count);
  buffer.putLongLong(size);
}


void QueueFlowLimit::decode ( Buffer& buffer )
{
  flowStopCount   = buffer.getLong();
  flowResumeCount = buffer.getLong();
  flowStopSize    = buffer.getLongLong();
  flowResumeSize  = buffer.getLongLong();
  count    = buffer.getLong();
  size     = buffer.getLongLong();
}


uint32_t QueueFlowLimit::encodedSize() const {
  return sizeof(uint32_t) +  // flowStopCount
         sizeof(uint32_t) +  // flowResumecount
         sizeof(uint64_t) +  // flowStopSize
         sizeof(uint64_t) +  // flowResumeSize
         sizeof(uint32_t) +  // count
         sizeof(uint64_t);   // size
}


const std::string QueueFlowLimit::flowStopCountKey("qpid.flow_stop_count");
const std::string QueueFlowLimit::flowResumeCountKey("qpid.flow_resume_count");
const std::string QueueFlowLimit::flowStopSizeKey("qpid.flow_stop_size");
const std::string QueueFlowLimit::flowResumeSizeKey("qpid.flow_resume_size");
uint64_t QueueFlowLimit::defaultMaxSize;
uint QueueFlowLimit::defaultFlowStopRatio;
uint QueueFlowLimit::defaultFlowResumeRatio;


void QueueFlowLimit::setDefaults(uint64_t maxQueueSize, uint flowStopRatio, uint flowResumeRatio)
{
    defaultMaxSize = maxQueueSize;
    defaultFlowStopRatio = flowStopRatio;
    defaultFlowResumeRatio = flowResumeRatio;

    /** @todo KAG: Verify valid range on Broker::Options instead of here */
    if (flowStopRatio > 100 || flowResumeRatio > 100)
        throw InvalidArgumentException(QPID_MSG("Default queue flow ratios must be between 0 and 100, inclusive:"
                                                << " flowStopRatio=" << flowStopRatio
                                                << " flowResumeRatio=" << flowResumeRatio));
    if (flowResumeRatio > flowStopRatio)
        throw InvalidArgumentException(QPID_MSG("Default queue flow stop ratio must be >= flow resume ratio:"
                                                << " flowStopRatio=" << flowStopRatio
                                                << " flowResumeRatio=" << flowResumeRatio));
}


void QueueFlowLimit::observe(Queue& queue, const QueueSettings& settings)
{
    QueueFlowLimit *ptr = createLimit( &queue, settings );
    if (ptr) {
        boost::shared_ptr<QueueFlowLimit> observer(ptr);
        queue.addObserver(observer);
    }
}

/** returns ptr to a QueueFlowLimit, else 0 if no limit */
QueueFlowLimit *QueueFlowLimit::createLimit(Queue *queue, const QueueSettings& settings)
{
    if (settings.dropMessagesAtLimit) {
        // The size of a RING queue is limited by design - no need for flow control.
        return 0;
    }

    if (settings.flowStop.hasCount() || settings.flowStop.hasSize()) {
        // user provided (some) flow settings manually...
        if (settings.flowStop.getCount() || settings.flowStop.getSize()) {
            return new QueueFlowLimit(queue,
                                      settings.flowStop.getCount(),
                                      settings.flowResume.getCount(),
                                      settings.flowStop.getSize(),
                                      settings.flowResume.getSize());
        } else {
            //don't have a non-zero value for either the count or the
            //size to stop at, yet at least one of these settings was
            //provided, i.e it was set to 0 explicitly which we treat
            //as turning it off
            return 0;
        }
    }

    if (defaultFlowStopRatio) {   // broker has a default ratio setup...
        uint64_t maxByteCount = settings.maxDepth.hasSize() ? settings.maxDepth.getSize() : defaultMaxSize;
        uint64_t flowStopSize = (uint64_t)(maxByteCount * (defaultFlowStopRatio/100.0) + 0.5);
        uint64_t flowResumeSize = (uint64_t)(maxByteCount * (defaultFlowResumeRatio/100.0));
        uint32_t maxMsgCount =  settings.maxDepth.hasCount() ? settings.maxDepth.getCount() : 0;
        uint32_t flowStopCount = (uint32_t)(maxMsgCount * (defaultFlowStopRatio/100.0) + 0.5);
        uint32_t flowResumeCount = (uint32_t)(maxMsgCount * (defaultFlowResumeRatio/100.0));
        return new QueueFlowLimit(queue, flowStopCount, flowResumeCount, flowStopSize, flowResumeSize);
    }
    return 0;
}

/* Cluster replication */

namespace {
    /** pack a set of sequence number ranges into a framing::Array */
    void buildSeqRangeArray(qpid::framing::Array *seqs,
                            const qpid::framing::SequenceNumber& first,
                            const qpid::framing::SequenceNumber& last)
    {
        seqs->push_back(qpid::framing::Array::ValuePtr(new Unsigned32Value(first)));
        seqs->push_back(qpid::framing::Array::ValuePtr(new Unsigned32Value(last)));
    }
}

/** Runs on UPDATER to snapshot current state */
void QueueFlowLimit::getState(qpid::framing::FieldTable& state ) const
{
    sys::Mutex::ScopedLock l(indexLock);
    state.clear();

    framing::SequenceSet ss;
    if (!index.empty()) {
        /* replicate the set of messages pending flow control */
        for (std::map<framing::SequenceNumber, Message >::const_iterator itr = index.begin();
             itr != index.end(); ++itr) {
            ss.add(itr->first);
        }
        framing::Array seqs(TYPE_CODE_UINT32);
        typedef boost::function<void(framing::SequenceNumber, framing::SequenceNumber)> arrayBuilder;
        ss.for_each((arrayBuilder)boost::bind(&buildSeqRangeArray, &seqs, _1, _2));
        state.setArray("pendingMsgSeqs", seqs);
    }
    QPID_LOG(debug, "Queue \"" << queueName << "\": flow limit replicating pending msgs, range=" << ss);
}


/** called on UPDATEE to set state from snapshot */
void QueueFlowLimit::setState(const qpid::framing::FieldTable& state)
{
    sys::Mutex::ScopedLock l(indexLock);
    index.clear();

    framing::SequenceSet fcmsg;
    framing::Array seqArray(TYPE_CODE_UINT32);
    if (state.getArray("pendingMsgSeqs", seqArray)) {
        assert((seqArray.count() & 0x01) == 0); // must be even since they are sequence ranges
        framing::Array::const_iterator i = seqArray.begin();
        while (i != seqArray.end()) {
            framing::SequenceNumber first((*i)->getIntegerValue<uint32_t, 4>());
            ++i;
            framing::SequenceNumber last((*i)->getIntegerValue<uint32_t, 4>());
            ++i;
            fcmsg.add(first, last);
            for (SequenceNumber seq = first; seq <= last; ++seq) {
                Message msg;
                queue->find(seq, msg);   // fyi: may not be found if msg is acquired & unacked
                bool unique;
                unique = index.insert(std::pair<framing::SequenceNumber, Message >(seq, msg)).second;
                // Like this to avoid tripping up unused variable warning when NDEBUG set
                if (!unique) assert(unique);
            }
        }
    }

    flowStopped = index.size() != 0;
    if (queueMgmtObj) {
        queueMgmtObj->set_flowStopped(isFlowControlActive());
    }
    QPID_LOG(debug, "Queue \"" << queueName << "\": flow limit replicated the pending msgs, range=" << fcmsg)
}


namespace qpid {
    namespace broker {

std::ostream& operator<<(std::ostream& out, const QueueFlowLimit& f)
{
    out << "; flowStopCount=" << f.flowStopCount << ", flowResumeCount=" << f.flowResumeCount;
    out << "; flowStopSize=" << f.flowStopSize << ", flowResumeSize=" << f.flowResumeSize;
    return out;
}

    }
}

