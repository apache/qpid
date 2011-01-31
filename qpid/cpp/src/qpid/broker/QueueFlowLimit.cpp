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
#include "qpid/broker/Queue.h"
#include "qpid/Exception.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
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

    /** extract a capacity value as passed in an argument map
     */
    uint64_t getCapacity(const FieldTable& settings, const std::string& key, uint64_t defaultValue)
    {
        FieldTable::ValuePtr v = settings.get(key);

        int64_t result = 0;

        if (!v) return defaultValue;
        if (v->getType() == 0x23) {
            QPID_LOG(debug, "Value for " << key << " specified as float: " << v->get<float>());
        } else if (v->getType() == 0x33) {
            QPID_LOG(debug, "Value for " << key << " specified as double: " << v->get<double>());
        } else if (v->convertsTo<int64_t>()) {
            result = v->get<int64_t>();
            QPID_LOG(debug, "Got integer value for " << key << ": " << result);
            if (result >= 0) return result;
        } else if (v->convertsTo<string>()) {
            string s(v->get<string>());
            QPID_LOG(debug, "Got string value for " << key << ": " << s);
            std::istringstream convert(s);
            if (convert >> result && result >= 0) return result;
        }

        QPID_LOG(warning, "Cannot convert " << key << " to unsigned integer, using default (" << defaultValue << ")");
        return defaultValue;
    }
}



QueueFlowLimit::QueueFlowLimit(Queue *_queue,
                               uint32_t _flowStopCount, uint32_t _flowResumeCount,
                               uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : queue(_queue), queueName("<unknown>"),
      flowStopCount(_flowStopCount), flowResumeCount(_flowResumeCount),
      flowStopSize(_flowStopSize), flowResumeSize(_flowResumeSize),
      flowStopped(false), count(0), size(0)
{
    uint32_t maxCount(0);
    uint64_t maxSize(0);

    if (queue) {
        queueName = _queue->getName();
        if (queue->getPolicy()) {
            maxSize = _queue->getPolicy()->getMaxSize();
            maxCount = _queue->getPolicy()->getMaxCount();
        }
    }
    validateFlowConfig( maxCount, flowStopCount, flowResumeCount, "count", queueName );
    validateFlowConfig( maxSize, flowStopSize, flowResumeSize, "size", queueName );
    QPID_LOG(info, "Queue \"" << queueName << "\": Flow limit created: flowStopCount=" << flowStopCount
             << ", flowResumeCount=" << flowResumeCount
             << ", flowStopSize=" << flowStopSize << ", flowResumeSize=" << flowResumeSize );
}



bool QueueFlowLimit::consume(const QueuedMessage& msg)
{
    bool flowChanged(false);

    if (!msg.payload) return false;

    sys::Mutex::ScopedLock l(pendingFlowLock);

    ++count;
    size += msg.payload->contentSize();

    if (flowStopCount && !flowStopped && count > flowStopCount) {
        flowChanged = flowStopped = true;
        QPID_LOG(info, "Queue \"" << queueName << "\": has reached " << flowStopCount << " enqueued messages. Producer flow control activated." );
    }

    if (flowStopSize && !flowStopped && size > flowStopSize) {
        flowChanged = flowStopped = true;
        QPID_LOG(info, "Queue \"" << queueName << "\": has reached " << flowStopSize << " enqueued bytes. Producer flow control activated." );
    }

    // KAG: test - REMOVE ONCE STABLE
    if (index.find(msg.payload) != index.end()) {
        QPID_LOG(error, "Queue \"" << queueName << "\": has enqueued a msg twice: " << msg.position);
    }

    if (flowStopped || !pendingFlow.empty()) {
        msg.payload->getReceiveCompletion().startCompleter();    // don't complete until flow resumes
        pendingFlow.push_back(msg.payload);
        index.insert(msg.payload);
    }

    return flowChanged;
}



bool QueueFlowLimit::replenish(const QueuedMessage& msg)
{
    bool flowChanged(false);

    if (!msg.payload) return false;

    sys::Mutex::ScopedLock l(pendingFlowLock);

    if (count > 0) {
        --count;
    } else {
        throw Exception(QPID_MSG("Flow limit count underflow on dequeue. Queue=" << queueName));
    }

    uint64_t _size = msg.payload->contentSize();
    if (_size <= size) {
        size -= _size;
    } else {
        throw Exception(QPID_MSG("Flow limit size underflow on dequeue. Queue=" << queueName));
    }

    if (flowStopped &&
        (flowResumeSize == 0 || size < flowResumeSize) &&
        (flowResumeCount == 0 || count < flowResumeCount)) {
        flowStopped = false;
        flowChanged = true;
        QPID_LOG(info, "Queue \"" << queueName << "\": has drained below the flow control resume level. Producer flow control deactivated." );
    }

    if (!flowStopped && !pendingFlow.empty()) {
        // if msg is flow controlled, release it.
        std::set< boost::intrusive_ptr<Message> >::iterator itr = index.find(msg.payload);
        if (itr != index.end()) {
            (*itr)->getReceiveCompletion().finishCompleter();
            index.erase(itr);
            // stupid:
            std::list< boost::intrusive_ptr<Message> >::iterator itr2 = find(pendingFlow.begin(),
                                                                             pendingFlow.end(),
                                                                             msg.payload);
            if (itr2 == pendingFlow.end()) {
                QPID_LOG(error, "Queue \"" << queueName << "\": indexed msg missing in list: " << msg.position);
            } else {
                pendingFlow.erase(itr2);
            }
        }

        // for now, just release the oldest also
        if (!pendingFlow.empty()) {
            pendingFlow.front()->getReceiveCompletion().finishCompleter();
            itr = index.find(pendingFlow.front());
            if (itr == index.end()) {
                QPID_LOG(error, "Queue \"" << queueName << "\": msg missing in index: " << pendingFlow.front());
            } else {
                index.erase(itr);
            }
            pendingFlow.pop_front();
        }
    }

    return flowChanged;
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


std::auto_ptr<QueueFlowLimit> QueueFlowLimit::createQueueFlowLimit(Queue *queue, const qpid::framing::FieldTable& settings)
{
    uint32_t flowStopCount = getCapacity(settings, flowStopCountKey, 0);
    uint32_t flowResumeCount = getCapacity(settings, flowResumeCountKey, 0);
    uint64_t flowStopSize = getCapacity(settings, flowStopSizeKey, 0);
    uint64_t flowResumeSize = getCapacity(settings, flowResumeSizeKey, 0);

    if (flowStopCount || flowResumeCount || flowStopSize || flowResumeSize) {
        return std::auto_ptr<QueueFlowLimit>(new QueueFlowLimit(queue, flowStopCount, flowResumeCount,
                                                                flowStopSize, flowResumeSize));
    } else {
        return std::auto_ptr<QueueFlowLimit>();
    }
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

/**
 * TBD:
 * - Is there a direct way to determine if QM is on pendingFlow list?
 * - Rate limit the granting of flow.
 * - What about LVQ?  A newer msg may replace the older one.
 * - What about queueing during a recovery?
 * - What about queue purge?
 * - What about message move?
 * - How do we treat orphaned messages?
 * -- Xfer a message to an alternate exchange - do we ack?
 */
