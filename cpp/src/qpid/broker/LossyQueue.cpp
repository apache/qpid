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
#include "LossyQueue.h"
#include "QueueDepth.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

namespace {
bool isLowerPriorityThan(uint8_t priority, const Message& m)
{
    return m.getPriority() <= priority;
}
}

LossyQueue::LossyQueue(const std::string& n, const QueueSettings& s, MessageStore* const ms, management::Manageable* p, Broker* b)
    : Queue(n, s, ms, p, b) {}

bool LossyQueue::checkDepth(const QueueDepth& increment, const Message& message)
{
    if (settings.maxDepth.hasSize() && increment.getSize() > settings.maxDepth.getSize()) {
        if (mgmtObject) {
            mgmtObject->inc_discardsOverflow();
            if (brokerMgmtObject)
                brokerMgmtObject->inc_discardsOverflow();
        }
        throw qpid::framing::ResourceLimitExceededException(QPID_MSG("Message larger than configured maximum depth on "
                                                                     << name << ": size=" << increment.getSize() << ", max-size=" << settings.maxDepth.getSize()));
    }

    while (settings.maxDepth && (settings.maxDepth - current < increment)) {
        QPID_LOG(debug, "purging " << name << ": current depth is [" << current << "], max depth is [" << settings.maxDepth << "], new message has size " << increment.getSize());
        qpid::sys::Mutex::ScopedUnlock u(messageLock);
        //TODO: arguably we should try and purge expired messages first but that
        //is potentially expensive

        // Note: in the case of a priority queue we are only comparing the new mesage
        // with single lowest-priority message, hence the final parameter maxTests
        // is 1 in this case, so we only test one message for removal.
        if (remove(1,
                   settings.priorities ?
                   boost::bind(&isLowerPriorityThan, message.getPriority(), _1) :
                   MessagePredicate(), boost::bind(&reroute, alternateExchange, _1),
                   PURGE, false,
                   settings.priorities ? 1 : 0))
        {
            if (mgmtObject) {
                mgmtObject->inc_discardsRing(1);
                if (brokerMgmtObject)
                    brokerMgmtObject->inc_discardsRing(1);
            }
        } else {
            //should only be the case for a non-empty queue if we are
            //testing priority and there was no lower (or equal)
            //priority message available to purge
            break;
        }
    }
    if (settings.maxDepth && (settings.maxDepth - current < increment)) {
        //will only be the case where we were unable to purge another
        //message, which should only be the case if we are purging
        //based on priority and there was no message with a lower (or
        //equal) priority than this one, meaning that we drop this
        //current message
        if (mgmtObject) {
            mgmtObject->inc_discardsRing(1);
            if (brokerMgmtObject)
                brokerMgmtObject->inc_discardsRing(1);
        }
        return false;
    } else {
        //have sufficient space for this message
        current += increment;
        return true;
    }
}
}} // namespace qpid::broker
