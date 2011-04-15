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
namespace {
const qmf::org::apache::qpid::broker::EventQueueThresholdExceeded EVENT("dummy", 0, 0);
bool isQMFv2(const boost::intrusive_ptr<Message> message)
{
    const qpid::framing::MessageProperties* props = message->getProperties<qpid::framing::MessageProperties>();
    return props && props->getAppId() == "qmf2";
}

bool isThresholdEvent(const boost::intrusive_ptr<Message> message)
{
    if (message->getIsManagementMessage()) {
        //is this a qmf event? if so is it a threshold event?
        if (isQMFv2(message)) {
            const qpid::framing::FieldTable* headers = message->getApplicationHeaders();
            if (headers && headers->getAsString("qmf.content") == "_event") {
                //decode as list
                std::string content = message->getFrames().getContent();
                qpid::types::Variant::List list;
                qpid::amqp_0_10::ListCodec::decode(content, list);
                if (list.empty() || list.front().getType() != qpid::types::VAR_MAP) return false;
                qpid::types::Variant::Map map = list.front().asMap();
                try {
                    std::string eventName = map["_schema_id"].asMap()["_class_name"].asString();
                    return eventName == EVENT.getEventName();
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Error checking for recursive threshold alert: " << e.what());
                }
            }
        } else {
            std::string content = message->getFrames().getContent();
            qpid::framing::Buffer buffer(const_cast<char*>(content.data()), content.size());
            if (buffer.getOctet() == 'A' && buffer.getOctet() == 'M' && buffer.getOctet() == '2' && buffer.getOctet() == 'e') {
                buffer.getLong();//sequence
                std::string packageName;
                buffer.getShortString(packageName);
                if (packageName != EVENT.getPackageName()) return false;
                std::string eventName;
                buffer.getShortString(eventName);
                return eventName == EVENT.getEventName();
            }
        }
    }
    return false;
}
}

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
            //Note: Raising an event may result in messages being
            //enqueued on queues; it may even be that this event
            //causes a message to be enqueued on the queue we are
            //tracking, and so we need to avoid recursing
            if (isThresholdEvent(m.payload)) return;
            lastAlert = qpid::sys::now();
            agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdExceeded(name, count, size));
            QPID_LOG(info, "Threshold event triggered for " << name << ", count=" << count << ", size=" << size);
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
                              const qpid::framing::FieldTable& settings, uint16_t limitRatio)

{
    qpid::types::Variant::Map map;
    qpid::amqp_0_10::translate(settings, map);
    observe(queue, agent, map, limitRatio);
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
                              const qpid::types::Variant::Map& settings, uint16_t limitRatio)

{
    //Note: aliases are keys defined by java broker
    Option<int64_t> repeatInterval("qpid.alert_repeat_gap", 60);
    repeatInterval.addAlias("x-qpid-minimum-alert-repeat-gap");

    //If no explicit threshold settings were given use specified
    //percentage of any limit from the policy.
    const QueuePolicy* policy = queue.getPolicy();
    Option<uint32_t> countThreshold("qpid.alert_count", (uint32_t) (policy && limitRatio ? (policy->getMaxCount()*limitRatio/100) : 0));
    countThreshold.addAlias("x-qpid-maximum-message-count");
    Option<uint64_t> sizeThreshold("qpid.alert_size", (uint64_t) (policy && limitRatio ? (policy->getMaxSize()*limitRatio/100) : 0));
    sizeThreshold.addAlias("x-qpid-maximum-message-size");

    observe(queue, agent, countThreshold.get(settings), sizeThreshold.get(settings), repeatInterval.get(settings));
}

}}
