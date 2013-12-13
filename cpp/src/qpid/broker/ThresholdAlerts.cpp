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
#include "qpid/broker/ThresholdAlerts.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/Message.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/broker/EventQueueThresholdCrossedUpward.h"
#include "qmf/org/apache/qpid/broker/EventQueueThresholdCrossedDownward.h"
#include "qmf/org/apache/qpid/broker/EventQueueThresholdExceeded.h"

namespace qpid {
namespace broker {

ThresholdAlerts::ThresholdAlerts(const std::string& n,
                                 qpid::management::ManagementAgent& a,
                                 const uint32_t ctu,
                                 const uint32_t ctd,
                                 const uint64_t stu,
                                 const uint64_t std,
                                 const bool bw)
    : name(n), agent(a),
      countThreshold(ctu), countThresholdDown(ctd),
      sizeThreshold(stu), sizeThresholdDown(std),
      count(0), size(0), countGoingUp(true), sizeGoingUp(true), backwardCompat(bw)  {}

void ThresholdAlerts::enqueued(const Message& m)
{
    size += m.getMessageSize();
    ++count;

    if (sizeGoingUp && sizeThreshold && size >= sizeThreshold) {
        sizeGoingUp = false;
        agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdCrossedUpward(name, count, size));
        if (backwardCompat)
            agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdExceeded(name, count, size));
    }

    if (countGoingUp && countThreshold && count >= countThreshold) {
        countGoingUp = false;
        agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdCrossedUpward(name, count, size));
        if (backwardCompat)
            agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdExceeded(name, count, size));
    }
}

void ThresholdAlerts::dequeued(const Message& m)
{
    size -= m.getMessageSize();
    --count;

    if (!sizeGoingUp && sizeThreshold && size <= sizeThresholdDown) {
        sizeGoingUp = true;
        agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdCrossedDownward(name, count, size));
    }

    if (!countGoingUp && countThreshold && count <= countThresholdDown) {
        countGoingUp = true;
        agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdCrossedDownward(name, count, size));
    }
}



void ThresholdAlerts::observe(Queue& queue, qpid::management::ManagementAgent& agent,
                              const uint64_t ctu,
                              const uint64_t _ctd,
                              const uint64_t stu,
                              const uint64_t _std)
{
    if (ctu || stu) {
        uint64_t ctd = (_ctd == 0 || _ctd >= ctu) ? ctu >> 1 : _ctd;
        uint64_t std = (_std == 0 || _std >= stu) ? stu >> 1 : _std;

        boost::shared_ptr<QueueObserver> observer(
        new ThresholdAlerts(queue.getName(), agent, ctu, ctd, stu, std, (_ctd == 0 && _std == 0))
        );
        queue.getObservers().add(observer);
    }
}

void ThresholdAlerts::observe(Queue& queue, qpid::management::ManagementAgent& agent,
                              const QueueSettings& settings, uint16_t limitRatio)
{
    //If no explicit threshold settings were given use specified
    //percentage of any limit from the policy.
    uint32_t countThreshold = settings.alertThreshold.hasCount() ? settings.alertThreshold.getCount() : (settings.maxDepth.getCount()*limitRatio/100);
    uint32_t sizeThreshold = settings.alertThreshold.hasSize() ? settings.alertThreshold.getSize() : (settings.maxDepth.getSize()*limitRatio/100);
    uint32_t countThresholdDown = settings.alertThresholdDown.hasCount() ? settings.alertThresholdDown.getCount() : 0;
    uint32_t sizeThresholdDown = settings.alertThresholdDown.hasSize() ? settings.alertThresholdDown.getSize() : 0;

    observe(queue, agent, countThreshold, countThresholdDown , sizeThreshold, sizeThresholdDown);
}

}}
