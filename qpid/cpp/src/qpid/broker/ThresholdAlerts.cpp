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
#include "qpid/broker/QueuedMessage.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/broker/EventQueueThresholdExceeded.h"

namespace qpid {
namespace broker {
ThresholdAlerts::ThresholdAlerts(const std::string& n,
                                 qpid::management::ManagementAgent& a,
                                 const uint32_t ct,
                                 const uint64_t st,
                                 const long repeat)
    : name(n), agent(a), countThreshold(ct), sizeThreshold(st),
      repeatInterval(repeat ? repeat*qpid::sys::TIME_SEC : 0),
      count(0), size(0), lastAlert(qpid::sys::EPOCH) {}

void ThresholdAlerts::enqueued(const QueuedMessage& m)
{
    size += m.payload->contentSize();
    ++count;
    if ((countThreshold && count >= countThreshold) || (sizeThreshold && size >= sizeThreshold)) {
        if ((repeatInterval == 0 && lastAlert == qpid::sys::EPOCH)
            || qpid::sys::Duration(lastAlert, qpid::sys::now()) > repeatInterval) {
            agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdExceeded(name, count, size));
            lastAlert = qpid::sys::now();
        }
    }
}

void ThresholdAlerts::dequeued(const QueuedMessage& m)
{
    size -= m.payload->contentSize();
    --count;
    if ((countThreshold && count < countThreshold) || (sizeThreshold && size < sizeThreshold)) {
        lastAlert = qpid::sys::EPOCH;
    }
}



void ThresholdAlerts::observe(Queue& queue, qpid::management::ManagementAgent& agent,
                              const uint64_t countThreshold,
                              const uint64_t sizeThreshold,
                              const long repeatInterval)
{
    if (countThreshold || sizeThreshold) {
        boost::shared_ptr<QueueObserver> observer(
            new ThresholdAlerts(queue.getName(), agent, countThreshold, sizeThreshold, repeatInterval)
        );
        queue.addObserver(observer);
    }
}

void ThresholdAlerts::observe(Queue& queue, qpid::management::ManagementAgent& agent,
                              const qpid::framing::FieldTable& settings)

{
    qpid::types::Variant::Map map;
    qpid::amqp_0_10::translate(settings, map);
    observe(queue, agent, map);
}

template <class T>
class Option
{
  public:
    Option(const std::string& name, T d) : defaultValue(d) { names.push_back(name); }
    void addAlias(const std::string& name) { names.push_back(name); }
    T get(const qpid::types::Variant::Map& settings) const
    {
        T value(defaultValue);
        for (std::vector<std::string>::const_iterator i = names.begin(); i != names.end(); ++i) {
            if (get(settings, *i, value)) break;
        }
        return value;
    }
  private:
    std::vector<std::string> names;
    T defaultValue;

    bool get(const qpid::types::Variant::Map& settings, const std::string& name, T& value) const
    {
        qpid::types::Variant::Map::const_iterator i = settings.find(name);
        if (i != settings.end()) {
            try {
                value = (T) i->second;
            } catch (const qpid::types::InvalidConversion&) {
                QPID_LOG(warning, "Bad value for" << name << ": " << i->second);
            }
            return true;
        } else {
            return false;
        }
    }
};

void ThresholdAlerts::observe(Queue& queue, qpid::management::ManagementAgent& agent,
                              const qpid::types::Variant::Map& settings)

{
    //Note: aliases are keys defined by java broker
    Option<int64_t> repeatInterval("qpid.alert_repeat_gap", 60);
    repeatInterval.addAlias("x-qpid-minimum-alert-repeat-gap");

    //If no explicit threshold settings were given use 80% of any
    //limit from the policy.
    const QueuePolicy* policy = queue.getPolicy();
    Option<uint32_t> countThreshold("qpid.alert_count", (uint32_t) (policy ? policy->getMaxCount()*0.8 : 0));
    countThreshold.addAlias("x-qpid-maximum-message-count");
    Option<uint64_t> sizeThreshold("qpid.alert_size", (uint64_t) (policy ? policy->getMaxSize()*0.8 : 0));
    sizeThreshold.addAlias("x-qpid-maximum-message-size");

    observe(queue, agent, countThreshold.get(settings), sizeThreshold.get(settings), repeatInterval.get(settings));
}

}}
