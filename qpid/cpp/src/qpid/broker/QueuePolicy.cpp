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
#include "QueuePolicy.h"
#include "Queue.h"
#include "qpid/Exception.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"

using namespace qpid::broker;
using namespace qpid::framing;

QueuePolicy::QueuePolicy(uint32_t _maxCount, uint64_t _maxSize, const std::string& _type) : 
    maxCount(_maxCount), maxSize(_maxSize), type(_type), count(0), size(0), policyExceeded(false) {}

void QueuePolicy::enqueued(uint64_t _size)
{
    if (maxCount) ++count;
    if (maxSize) size += _size;
}

void QueuePolicy::dequeued(uint64_t _size)
{
    //Note: underflow detection is not reliable in the face of
    //concurrent updates (at present locking in Queue.cpp prevents
    //these anyway); updates are atomic and are safe regardless.
    if (maxCount) {
        if (count.get() > 0) {
            --count;
        } else {
            throw Exception(QPID_MSG("Attempted count underflow on dequeue(" << _size << "): " << *this));
        }
    }
    if (maxSize) {
        if (_size > size.get()) {
            throw Exception(QPID_MSG("Attempted size underflow on dequeue(" << _size << "): " << *this));
        } else {
            size -= _size;
        }
    }
}

bool QueuePolicy::checkLimit(const QueuedMessage& m)
{
    bool exceeded = (maxSize && (size.get() + m.payload->contentSize()) > maxSize) || (maxCount && (count.get() + 1) > maxCount);
    if (exceeded) {
        if (!policyExceeded) {
            policyExceeded = true;
            if (m.queue) {
                QPID_LOG(info, "Queue size exceeded policy for " << m.queue->getName());
            }
        }
    } else {
        if (policyExceeded) {
            policyExceeded = false;
            if (m.queue) {
                QPID_LOG(info, "Queue size within policy for " << m.queue->getName());
            }
        }
    }
    return !exceeded;
}

void QueuePolicy::tryEnqueue(const QueuedMessage& m)
{
    if (checkLimit(m)) {
        enqueued(m);
    } else {
        std::string queue = m.queue ? m.queue->getName() : std::string("unknown queue");
        throw ResourceLimitExceededException(
            QPID_MSG("Policy exceeded on " << queue << " by message " << m.position 
                     << " of size " << m.payload->contentSize() << " , policy: " << *this));
    }
}

void QueuePolicy::enqueued(const QueuedMessage& m)
{
    enqueued(m.payload->contentSize());
}

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


int QueuePolicy::getInt(const FieldTable& settings, const std::string& key, int defaultValue)
{
    FieldTable::ValuePtr v = settings.get(key);
    if (v && v->convertsTo<int>()) return v->get<int>();
    else return defaultValue;
}

std::string QueuePolicy::getType(const FieldTable& settings)
{
    FieldTable::ValuePtr v = settings.get(typeKey);
    if (v && v->convertsTo<std::string>()) {
        std::string t = v->get<std::string>();
        transform(t.begin(), t.end(), t.begin(), tolower);        
        if (t == REJECT || t == FLOW_TO_DISK || t == RING || t == RING_STRICT) return t;
    }
    return FLOW_TO_DISK;
}

void QueuePolicy::setDefaultMaxSize(uint64_t s)
{
    defaultMaxSize = s;
}





void QueuePolicy::encode(Buffer& buffer) const
{
  buffer.putLong(maxCount);
  buffer.putLongLong(maxSize);
  buffer.putLong(count.get());
  buffer.putLongLong(size.get());
}

void QueuePolicy::decode ( Buffer& buffer ) 
{
  maxCount = buffer.getLong();
  maxSize  = buffer.getLongLong();
  count    = buffer.getLong();
  size     = buffer.getLongLong();
}


uint32_t QueuePolicy::encodedSize() const {
  return sizeof(uint32_t) +  // maxCount
         sizeof(uint64_t) +  // maxSize
         sizeof(uint32_t) +  // count
         sizeof(uint64_t);   // size
}



const std::string QueuePolicy::maxCountKey("qpid.max_count");
const std::string QueuePolicy::maxSizeKey("qpid.max_size");
const std::string QueuePolicy::typeKey("qpid.policy_type");
const std::string QueuePolicy::REJECT("reject");
const std::string QueuePolicy::FLOW_TO_DISK("flow_to_disk");
const std::string QueuePolicy::RING("ring");
const std::string QueuePolicy::RING_STRICT("ring_strict");
uint64_t QueuePolicy::defaultMaxSize(0);

FlowToDiskPolicy::FlowToDiskPolicy(uint32_t _maxCount, uint64_t _maxSize) : 
    QueuePolicy(_maxCount, _maxSize, FLOW_TO_DISK) {}

bool FlowToDiskPolicy::checkLimit(const QueuedMessage& m)
{
    return QueuePolicy::checkLimit(m) || m.queue->releaseMessageContent(m);
}

RingQueuePolicy::RingQueuePolicy(uint32_t _maxCount, uint64_t _maxSize, const std::string& _type) : 
    QueuePolicy(_maxCount, _maxSize, _type), strict(_type == RING_STRICT) {}

void RingQueuePolicy::enqueued(const QueuedMessage& m)
{
    QueuePolicy::enqueued(m);
    qpid::sys::Mutex::ScopedLock l(lock);
    queue.push_back(m);
}

void RingQueuePolicy::dequeued(const QueuedMessage& m)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    //find and remove m from queue
    for (Messages::iterator i = queue.begin(); i != queue.end(); i++) {
        if (i->payload == m.payload) {
            queue.erase(i);
            //now update count and size
            QueuePolicy::dequeued(m);
            break;
        }
    }
}

bool RingQueuePolicy::isEnqueued(const QueuedMessage& m)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    //for non-strict ring policy, a message can be replaced (and
    //therefore dequeued) before it is accepted or released by
    //subscriber; need to detect this
    for (Messages::const_iterator i = queue.begin(); i != queue.end(); i++) {
        if (i->payload == m.payload) {
            return true;
        }
    }
    return false;
}

bool RingQueuePolicy::checkLimit(const QueuedMessage& m)
{
    if (QueuePolicy::checkLimit(m)) return true;//if haven't hit limit, ok to accept
    
    QueuedMessage oldest;
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        if (queue.empty()) {
            QPID_LOG(debug, "Message too large for ring queue " 
                     << (m.queue ? m.queue->getName() : std::string("unknown queue")) 
                     << " [" << *this  << "] "
                     << ": message size = " << m.payload->contentSize() << " bytes");
            return false;
        }
        oldest = queue.front();
    }
    if (oldest.queue->acquire(oldest) || !strict) {
        oldest.queue->dequeue(0, oldest);
        QPID_LOG(debug, "Ring policy triggered in queue " 
                 << (m.queue ? m.queue->getName() : std::string("unknown queue"))
                 << ": removed message " << oldest.position << " to make way for " << m.position);
        return true;
    } else {
        QPID_LOG(debug, "Ring policy could not be triggered in queue " 
                 << (m.queue ? m.queue->getName() : std::string("unknown queue")) 
                 << ": oldest message (seq-no=" << oldest.position << ") has been delivered but not yet acknowledged or requeued");
        //in strict mode, if oldest message has been delivered (hence
        //cannot be acquired) but not yet acked, it should not be
        //removed and the attempted enqueue should fail
        return false;
    }
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(const qpid::framing::FieldTable& settings)
{
    uint32_t maxCount = getInt(settings, maxCountKey, 0);
    uint32_t maxSize = getInt(settings, maxSizeKey, defaultMaxSize);
    if (maxCount || maxSize) {
        return createQueuePolicy(maxCount, maxSize, getType(settings));
    } else {
        return std::auto_ptr<QueuePolicy>();
    }
}

std::auto_ptr<QueuePolicy> QueuePolicy::createQueuePolicy(uint32_t maxCount, uint64_t maxSize, const std::string& type)
{
    if (type == RING || type == RING_STRICT) {
        return std::auto_ptr<QueuePolicy>(new RingQueuePolicy(maxCount, maxSize, type));
    } else if (type == FLOW_TO_DISK) {
        return std::auto_ptr<QueuePolicy>(new FlowToDiskPolicy(maxCount, maxSize));
    } else {
        return std::auto_ptr<QueuePolicy>(new QueuePolicy(maxCount, maxSize, type));
    }

}
 
namespace qpid {
    namespace broker {

std::ostream& operator<<(std::ostream& out, const QueuePolicy& p)
{
    if (p.maxSize) out << "size: max=" << p.maxSize << ", current=" << p.size.get();
    else out << "size: unlimited";
    out << "; ";
    if (p.maxCount) out << "count: max=" << p.maxCount << ", current=" << p.count.get();
    else out << "count: unlimited";    
    out << "; type=" << p.type;
    return out;
}

    }
}

