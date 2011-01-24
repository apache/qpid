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
#include "qpid/broker/QueuePolicy.h"
#include "qpid/broker/Queue.h"
#include "qpid/Exception.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
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

    /** extract a capacity value as passing in an argument map
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

QueuePolicy::QueuePolicy(const std::string& _name, uint32_t _maxCount, uint64_t _maxSize, const std::string& _type,
                         uint32_t _flowStopCount, uint32_t _flowResumeCount, uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : maxCount(_maxCount), maxSize(_maxSize), type(_type), count(0), size(0), policyExceeded(false),
      flowStopCount(_flowStopCount), flowResumeCount(_flowResumeCount),
      flowStopSize(_flowStopSize), flowResumeSize(_flowResumeSize),
      flowStopped(false), name(_name)
{
    validateFlowConfig( maxCount, flowStopCount, flowResumeCount, "count", name );
    validateFlowConfig( maxSize, flowStopSize, flowResumeSize, "size", name );
    QPID_LOG(info, "Queue \"" << name << "\": Policy created: type=" << type << "; maxCount=" << maxCount << "; maxSize=" << maxSize
             << "; flowStopCount=" << flowStopCount << "; flowResumeCount=" << flowResumeCount
             << "; flowStopSize=" << flowStopSize << "; flowResumeSize=" << flowResumeSize );
}

void QueuePolicy::enqueued(uint64_t _size)
{
    if (maxCount || flowStopCount) {
        ++count;
        if (flowStopCount && !flowStopped && count > flowStopCount) {
            flowStopped = true;
            QPID_LOG(info, "Queue \"" << name << "\": has reached " << flowStopCount << " enqueued messages. Producer flow control activated." );
        }
    }

    if (maxSize  || flowStopSize) {
        size += _size;
        if (flowStopSize && !flowStopped && size > flowStopSize) {
            flowStopped = true;
            QPID_LOG(info, "Queue \"" << name << "\": has reached " << flowStopSize << " enqueued bytes. Producer flow control activated." );
        }
    }
}

void QueuePolicy::dequeued(uint64_t _size)
{
    if (maxCount || flowStopCount) {
        if (count > 0) {
            --count;
        } else {
            throw Exception(QPID_MSG("Attempted count underflow on dequeue(" << _size << "): " << *this));
        }
    }
    if (maxSize || flowStopSize) {
        if (_size > size) {
            throw Exception(QPID_MSG("Attempted size underflow on dequeue(" << _size << "): " << *this));
        } else {
            size -= _size;
        }
    }
    if (flowStopped &&
        (flowResumeSize == 0 || size < flowResumeSize) &&
        (flowResumeCount == 0 || count < flowResumeCount)) {
        flowStopped = false;
        QPID_LOG(info, "Queue \"" << name << "\": has drained below the flow control resume level. Producer flow control deactivated." );
    }
}

bool QueuePolicy::checkLimit(boost::intrusive_ptr<Message> m)
{
    bool sizeExceeded = maxSize && (size + m->contentSize()) > maxSize;
    bool countExceeded = maxCount && (count + 1) > maxCount;
    bool exceeded = sizeExceeded || countExceeded;
    if (exceeded) {
        if (!policyExceeded) {
            policyExceeded = true;            
            if (sizeExceeded) QPID_LOG(info, "Queue cumulative message size exceeded policy for " << name);
            if (countExceeded) QPID_LOG(info, "Queue message count exceeded policy for " << name);
        }
    } else {
        if (policyExceeded) {
            policyExceeded = false;
            QPID_LOG(info, "Queue cumulative message size and message count within policy for " << name);
        }
    }
    return !exceeded;
}

void QueuePolicy::tryEnqueue(boost::intrusive_ptr<Message> m)
{
    if (checkLimit(m)) {
        enqueued(m->contentSize());
    } else {
        throw ResourceLimitExceededException(QPID_MSG("Policy exceeded on " << name << ", policy: " << *this));
    }
}

void QueuePolicy::recoverEnqueued(boost::intrusive_ptr<Message> m)
{
    tryEnqueue(m);
}

void QueuePolicy::enqueueAborted(boost::intrusive_ptr<Message> m)
{
    dequeued(m->contentSize());
}

void QueuePolicy::enqueued(const QueuedMessage&) {}

void QueuePolicy::dequeued(const QueuedMessage& m)
{
    dequeued(m.payload->contentSize());
}

bool QueuePolicy::isEnqueued(const QueuedMessage&)
{
    return true;
}

void QueuePolicy::update(FieldTable& settings)
{
    if (maxCount) settings.setInt(maxCountKey, maxCount);
    if (maxSize) settings.setInt(maxSizeKey, maxSize);
    settings.setString(typeKey, type);
}

std::string QueuePolicy::getType(const FieldTable& settings)
{
    FieldTable::ValuePtr v = settings.get(typeKey);
    if (v && v->convertsTo<std::string>()) {
        std::string t = v->get<std::string>();
        std::transform(t.begin(), t.end(), t.begin(), tolower);        
        if (t == REJECT || t == FLOW_TO_DISK || t == RING || t == RING_STRICT) return t;
    }
    return REJECT;
}

void QueuePolicy::setDefaultMaxSize(uint64_t s)
{
    defaultMaxSize = s;
}

void QueuePolicy::getPendingDequeues(Messages&) {}




void QueuePolicy::encode(Buffer& buffer) const
{
  buffer.putLong(maxCount);
  buffer.putLongLong(maxSize);
  buffer.putLong(count);
  buffer.putLongLong(size);
  buffer.putLong(flowStopCount);
  buffer.putLong(flowResumeCount);
  buffer.putLongLong(flowStopSize);
  buffer.putLongLong(flowResumeSize);
}

void QueuePolicy::decode ( Buffer& buffer ) 
{
  maxCount = buffer.getLong();
  maxSize  = buffer.getLongLong();
  count    = buffer.getLong();
  size     = buffer.getLongLong();
  flowStopCount   = buffer.getLong();
  flowResumeCount = buffer.getLong();
  flowStopSize    = buffer.getLongLong();
  flowResumeSize  = buffer.getLongLong();
}


uint32_t QueuePolicy::encodedSize() const {
  return sizeof(uint32_t) +  // maxCount
         sizeof(uint64_t) +  // maxSize
         sizeof(uint32_t) +  // count
         sizeof(uint64_t) +  // size
         sizeof(uint32_t) +  // flowStopCount
         sizeof(uint32_t) +  // flowResumecount
         sizeof(uint64_t) +  // flowStopSize
         sizeof(uint64_t);   // flowResumeSize
}



const std::string QueuePolicy::maxCountKey("qpid.max_count");
const std::string QueuePolicy::maxSizeKey("qpid.max_size");
const std::string QueuePolicy::typeKey("qpid.policy_type");
const std::string QueuePolicy::flowStopCountKey("qpid.flow_stop_count");
const std::string QueuePolicy::flowResumeCountKey("qpid.flow_resume_count");
const std::string QueuePolicy::flowStopSizeKey("qpid.flow_stop_size");
const std::string QueuePolicy::flowResumeSizeKey("qpid.flow_resume_size");
const std::string QueuePolicy::REJECT("reject");
const std::string QueuePolicy::FLOW_TO_DISK("flow_to_disk");
const std::string QueuePolicy::RING("ring");
const std::string QueuePolicy::RING_STRICT("ring_strict");
uint64_t QueuePolicy::defaultMaxSize(0);

FlowToDiskPolicy::FlowToDiskPolicy(const std::string& _name, uint32_t _maxCount, uint64_t _maxSize,
                                   uint32_t _flowStopCount, uint32_t _flowResumeCount,
                                   uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : QueuePolicy(_name, _maxCount, _maxSize, FLOW_TO_DISK,
                  _flowStopCount, _flowResumeCount, _flowStopSize, _flowResumeSize) {}

bool FlowToDiskPolicy::checkLimit(boost::intrusive_ptr<Message> m)
{
    if (!QueuePolicy::checkLimit(m)) m->requestContentRelease(); 
    return true;
}

RingQueuePolicy::RingQueuePolicy(const std::string& _name, 
                                 uint32_t _maxCount, uint64_t _maxSize, const std::string& _type,
                                 uint32_t _flowStopCount, uint32_t _flowResumeCount,
                                 uint64_t _flowStopSize,  uint64_t _flowResumeSize)
    : QueuePolicy(_name, _maxCount, _maxSize, _type, _flowStopCount, _flowResumeCount,
                  _flowStopSize, _flowResumeSize), strict(_type == RING_STRICT) {}

bool before(const QueuedMessage& a, const QueuedMessage& b)
{
    return a.position < b.position;
}

void RingQueuePolicy::enqueued(const QueuedMessage& m)
{
    //need to insert in correct location based on position
    queue.insert(lower_bound(queue.begin(), queue.end(), m, before), m);
}

void RingQueuePolicy::dequeued(const QueuedMessage& m)
{
    //find and remove m from queue
    if (find(m, pendingDequeues, true) || find(m, queue, true)) {
        //now update count and size
        QueuePolicy::dequeued(m);
    }
}

bool RingQueuePolicy::isEnqueued(const QueuedMessage& m)
{
    //for non-strict ring policy, a message can be replaced (and
    //therefore dequeued) before it is accepted or released by
    //subscriber; need to detect this
    return find(m, pendingDequeues, false) || find(m, queue, false); 
}

bool RingQueuePolicy::checkLimit(boost::intrusive_ptr<Message> m)
{

    // If the message is bigger than the queue size, give up
    if (m->contentSize() > getMaxSize()) {
        QPID_LOG(debug, "Message too large for ring queue " << name 
                 << " [" << *this  << "] "
                 << ": message size = " << m->contentSize() << " bytes"
                 << ": max queue size = " << getMaxSize() << " bytes");
        return false;
    }

    // if within limits, ok to accept
    if (QueuePolicy::checkLimit(m)) return true;

    // At this point, we've exceeded maxSize, maxCount, or both.
    //
    // If we've exceeded maxCount, we've exceeded it by 1, so
    // replacing the first message is sufficient. If we've exceeded
    // maxSize, we need to pop enough messages to get the space we
    // need.

    unsigned int haveSpace = getMaxSize() - getCurrentQueueSize();

    do {
        QueuedMessage oldest  = queue.front();

        if (oldest.queue->acquire(oldest) || !strict) {
            queue.pop_front();
            pendingDequeues.push_back(oldest);
            QPID_LOG(debug, "Ring policy triggered in " << name 
                     << ": removed message " << oldest.position << " to make way for new message");

            haveSpace += oldest.payload->contentSize();
            
        } else {
            //in strict mode, if oldest message has been delivered (hence
            //cannot be acquired) but not yet acked, it should not be
            //removed and the attempted enqueue should fail
            QPID_LOG(debug, "Ring policy could not be triggered in " << name 
                     << ": oldest message (seq-no=" << oldest.position << ") has been delivered but not yet acknowledged or requeued");
            return false;
        }
    } while (haveSpace < m->contentSize());
    
    
    return true;
}

void RingQueuePolicy::getPendingDequeues(Messages& result)
{
    result = pendingDequeues;
}

bool RingQueuePolicy::find(const QueuedMessage& m, Messages& q, bool remove)
{
    for (Messages::iterator i = q.begin(); i != q.end(); i++) {
        if (i->payload == m.payload) {
            if (remove) q.erase(i);
            return true;
        }
    }
    return false;
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(uint32_t maxCount, uint64_t maxSize, const std::string& type)
{
    return createQueuePolicy("<unspecified>", maxCount, maxSize, type);
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(const qpid::framing::FieldTable& settings)
{
    return createQueuePolicy("<unspecified>", settings);
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(const std::string& name, const qpid::framing::FieldTable& settings)
{
    uint32_t maxCount = getCapacity(settings, maxCountKey, 0);
    uint64_t maxSize = getCapacity(settings, maxSizeKey, defaultMaxSize);
    uint32_t flowStopCount = getCapacity(settings, flowStopCountKey, 0);
    uint32_t flowResumeCount = getCapacity(settings, flowResumeCountKey, 0);
    uint64_t flowStopSize = getCapacity(settings, flowStopSizeKey, 0);
    uint64_t flowResumeSize = getCapacity(settings, flowResumeSizeKey, 0);

    if (maxCount || maxSize || flowStopCount || flowResumeCount || flowStopSize || flowResumeSize) {
        return createQueuePolicy(name, maxCount, maxSize, getType(settings),
                                 flowStopCount, flowResumeCount, flowStopSize, flowResumeSize);
    } else {
        return std::auto_ptr<QueuePolicy>();
    }
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(const std::string& name, 
                                                          uint32_t maxCount, uint64_t maxSize, const std::string& type,
                                                          uint32_t flowStopCount, uint32_t flowResumeCount,
                                                          uint64_t flowStopSize, uint64_t flowResumeSize)
{
    if (type == RING || type == RING_STRICT) {
        return std::auto_ptr<QueuePolicy>(new RingQueuePolicy(name, maxCount, maxSize, type,
                                                              flowStopCount, flowResumeCount, flowStopSize, flowResumeSize));
    } else if (type == FLOW_TO_DISK) {
        return std::auto_ptr<QueuePolicy>(new FlowToDiskPolicy(name, maxCount, maxSize,
                                                               flowStopCount, flowResumeCount, flowStopSize, flowResumeSize));
    } else {
        return std::auto_ptr<QueuePolicy>(new QueuePolicy(name, maxCount, maxSize, type,
                                                          flowStopCount, flowResumeCount, flowStopSize, flowResumeSize));
    }

}
 
namespace qpid {
    namespace broker {

std::ostream& operator<<(std::ostream& out, const QueuePolicy& p)
{
    if (p.maxSize) out << "size: max=" << p.maxSize << ", current=" << p.size;
    else out << "size: unlimited";
    out << "; ";
    if (p.maxCount) out << "count: max=" << p.maxCount << ", current=" << p.count;
    else out << "count: unlimited";    
    out << "; type=" << p.type;
    if (p.flowStopCount) out << "; flowStopCount=" << p.flowStopCount << ", flowResumeCount=" << p.flowResumeCount;
    if (p.flowStopSize) out << ";  flowStopSize=" << p.flowStopSize << ", flowResumeSize=" << p.flowResumeSize;
    return out;
}

    }
}

