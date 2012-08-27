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
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/broker/EventQueueThresholdExceeded.h"

namespace qpid {
namespace broker {
namespace {
const qmf::org::apache::qpid::broker::EventQueueThresholdExceeded EVENT("dummy", 0, 0);
bool isQMFv2(const Message& message)
{
    const qpid::framing::MessageProperties* props = qpid::broker::amqp_0_10::MessageTransfer::get(message).getProperties<qpid::framing::MessageProperties>();
    return props && props->getAppId() == "qmf2";
}

bool isThresholdEvent(const Message& message)
{
    if (message.getIsManagementMessage()) {
        //is this a qmf event? if so is it a threshold event?
        if (isQMFv2(message)) {
            if (message.getPropertyAsString("qmf.content") == "_event") {
                //decode as list
                std::string content = qpid::broker::amqp_0_10::MessageTransfer::get(message).getFrames().getContent();
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
            std::string content = qpid::broker::amqp_0_10::MessageTransfer::get(message).getFrames().getContent();
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

void ThresholdAlerts::enqueued(const Message& m)
{
    size += m.getContentSize();
    ++count;
    if ((countThreshold && count >= countThreshold) || (sizeThreshold && size >= sizeThreshold)) {
        if ((repeatInterval == 0 && lastAlert == qpid::sys::EPOCH)
            || qpid::sys::Duration(lastAlert, qpid::sys::now()) > repeatInterval) {
            //Note: Raising an event may result in messages being
            //enqueued on queues; it may even be that this event
            //causes a message to be enqueued on the queue we are
            //tracking, and so we need to avoid recursing
            if (isThresholdEvent(m)) return;
            lastAlert = qpid::sys::now();
            agent.raiseEvent(qmf::org::apache::qpid::broker::EventQueueThresholdExceeded(name, count, size));
            QPID_LOG(info, "Threshold event triggered for " << name << ", count=" << count << ", size=" << size);
        }
    }
}

void ThresholdAlerts::dequeued(const Message& m)
{
    size -= m.getContentSize();
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
                              const QueueSettings& settings, uint16_t limitRatio)
{
    //If no explicit threshold settings were given use specified
    //percentage of any limit from the policy.
    uint32_t countThreshold = settings.alertThreshold.hasCount() ? settings.alertThreshold.getCount() : (settings.maxDepth.getCount()*limitRatio/100);
    uint32_t sizeThreshold = settings.alertThreshold.hasSize() ? settings.alertThreshold.getSize() : (settings.maxDepth.getSize()*limitRatio/100);

    observe(queue, agent, countThreshold, sizeThreshold, settings.alertRepeatInterval);
}

}}
