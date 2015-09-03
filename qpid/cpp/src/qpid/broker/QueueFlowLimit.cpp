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

#include <boost/enable_shared_from_this.hpp>

using namespace qpid::broker;
using namespace qpid::framing;

namespace {
    /** ensure that the configured flow control stop and resume values are
     * valid with respect to the maximum queue capacity, and each other
     */
    template <typename T>
    void validateFlowConfig(T max, T& stop, T& resume, const std::string& type, const std::string& queue)
    {
        if (stop) {
            if (resume > stop) {
                throw InvalidArgumentException(QPID_MSG("Queue \"" << queue << "\": qpid.flow_resume_" << type
                                                    << "=" << resume
                                                    << " must be less or equal to qpid.flow_stop_" << type
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
}



QueueFlowLimit::QueueFlowLimit(const std::string& _queueName,
                               uint32_t _flowStopCount, uint32_t _flowResumeCount,
                               uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : queue(0), queueName(_queueName),
      flowStopCount(_flowStopCount), flowResumeCount(_flowResumeCount),
      flowStopSize(_flowStopSize), flowResumeSize(_flowResumeSize),
      flowStopped(false), count(0), size(0), broker(0)
{
    QPID_LOG(debug, "Queue \"" << queueName << "\": Flow limit created: flowStopCount=" << flowStopCount
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
    size += msg.getMessageSize();

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

    uint64_t _size = msg.getMessageSize();
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


void QueueFlowLimit::observe(Queue& queue)
{
    /* set up management stuff */
    broker = queue.getBroker();
    queueMgmtObj = boost::dynamic_pointer_cast<_qmfBroker::Queue> (queue.GetManagementObject());
    if (queueMgmtObj) {
        queueMgmtObj->set_flowStopped(isFlowControlActive());
    }

    /* set up the observer */
    queue.getObservers().add(shared_from_this());
}

/** returns ptr to a QueueFlowLimit, else 0 if no limit */
boost::shared_ptr<QueueFlowLimit> QueueFlowLimit::createLimit(const std::string& queueName, const QueueSettings& settings)
{
    if (settings.dropMessagesAtLimit) {
        // The size of a RING queue is limited by design - no need for flow control.
        return boost::shared_ptr<QueueFlowLimit>();
    }
    if ((!settings.flowStop.hasCount()) && (!settings.flowStop.hasSize()) && (settings.flowResume.hasCount() || settings.flowResume.hasSize()))
        QPID_LOG(warning, "queue " << queueName << ": user-configured flow limits are ignored as no stop limits provided");

    uint32_t flowStopCount(0), flowResumeCount(0), maxMsgCount(settings.maxDepth.hasCount() ? settings.maxDepth.getCount() : 0);
    uint64_t flowStopSize(0), flowResumeSize(0), maxByteCount(settings.maxDepth.hasSize() ? settings.maxDepth.getSize() : defaultMaxSize);

    // pre-fill by defaults, if exist
    if (defaultFlowStopRatio) {   // broker has a default ratio setup...
        flowStopSize = (uint64_t)(maxByteCount * (defaultFlowStopRatio/100.0) + 0.5);
        flowStopCount = (uint32_t)(maxMsgCount * (defaultFlowStopRatio/100.0) + 0.5);
    }

    if (defaultFlowResumeRatio) {   // broker has a default ratio setup...
        flowResumeSize = (uint64_t)(maxByteCount * (defaultFlowResumeRatio/100.0));
	flowResumeCount = (uint32_t)(maxMsgCount * (defaultFlowResumeRatio/100.0));
    }

    // update by user-specified thresholds
    if (settings.flowStop.hasCount())
        flowStopCount = settings.flowStop.getCount();
    if (settings.flowStop.hasSize())
        flowStopSize = settings.flowStop.getSize();
    if (settings.flowResume.hasCount())
        flowResumeCount = settings.flowResume.getCount();
    if (settings.flowResume.hasSize())
        flowResumeSize = settings.flowResume.getSize();

    if (flowStopCount || flowStopSize) {
	validateFlowConfig(maxMsgCount, flowStopCount, flowResumeCount, "count", queueName );
        validateFlowConfig(maxByteCount, flowStopSize, flowResumeSize, "size", queueName );
        return boost::shared_ptr<QueueFlowLimit>(new QueueFlowLimit(queueName, flowStopCount, flowResumeCount, flowStopSize, flowResumeSize));
    }
    else
        //don't have a non-zero value for either the count or the
        //size to stop at, so no flow limit applicable
        return boost::shared_ptr<QueueFlowLimit>();
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

