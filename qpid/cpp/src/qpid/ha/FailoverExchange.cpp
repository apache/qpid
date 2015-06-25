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
#include "FailoverExchange.h"
#include "Event.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQHeaderBody.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/Array.h"
#include "qpid/RefCounted.h"
#include "qpid/UrlArray.h"
#include <boost/bind.hpp>
#include <algorithm>


namespace qpid {
namespace ha {

using namespace std;

using namespace broker;
using namespace framing;
using broker::amqp_0_10::MessageTransfer;

const string FailoverExchange::typeName("amq.failover");

namespace {
struct OstreamUrls {
    OstreamUrls(const FailoverExchange::Urls& u) : urls(u) {}
    FailoverExchange::Urls urls;
};

ostream& operator<<(ostream& o, const OstreamUrls& urls) {
    ostream_iterator<qpid::Url> out(o, " ");
    copy(urls.urls.begin(), urls.urls.end(), out);
    return o;
}
}

FailoverExchange::FailoverExchange(management::Manageable& parent, Broker& b)
    : Exchange(typeName, &parent, &b)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type(typeName);
}

void FailoverExchange::setUrls(const vector<Url>& u) {
    QPID_LOG(debug, typeName << " URLs set to " << OstreamUrls(u));
    Lock l(lock);
    urls = u;
}

void FailoverExchange::updateUrls(const vector<Url>& u) {
    QPID_LOG(debug, typeName << " Updating URLs " << OstreamUrls(u) << " to "
             << queues.size() << " subscribers.");
    Lock l(lock);
    urls=u;
    if (!urls.empty() && !queues.empty()) {
        for (Queues::const_iterator i = queues.begin(); i != queues.end(); ++i)
            sendUpdate(*i, l);
    }
}

string FailoverExchange::getType() const { return typeName; }

bool FailoverExchange::bind(Queue::shared_ptr queue, const string&,
                            const framing::FieldTable*) {
    QPID_LOG(debug, typeName << " binding " << queue->getName());
    Lock l(lock);
    sendUpdate(queue, l);
    return queues.insert(queue).second;
}

bool FailoverExchange::unbind(Queue::shared_ptr queue, const string&,
                              const framing::FieldTable*) {
    QPID_LOG(debug, typeName << " un-binding " << queue->getName());
    Lock l(lock);
    return queues.erase(queue);
}

bool FailoverExchange::isBound(Queue::shared_ptr queue, const string* const,
                               const framing::FieldTable*) {
    Lock l(lock);
    return queues.find(queue) != queues.end();
}

bool FailoverExchange::hasBindings() {
    Lock l(lock);
    return !queues.empty();
}

void FailoverExchange::route(Deliverable&) {
    QPID_LOG(warning, typeName << " unexpected message, ignored.");
}

void FailoverExchange::sendUpdate(const Queue::shared_ptr& queue, sys::Mutex::ScopedLock&) {
    QPID_LOG(debug, typeName << " sending " << OstreamUrls(urls) << " to " << queue->getName());
    if (urls.empty()) return;
    framing::Array array = vectorToUrlArray(urls);
    const ProtocolVersion v;
    broker::Message message(makeMessage(std::string(), typeName, typeName));
    MessageTransfer& transfer = MessageTransfer::get(message);
    MessageProperties* props =
        transfer.getFrames().getHeaders()->get<framing::MessageProperties>(true);
    props->setContentLength(0);
    props->getApplicationHeaders().setArray(typeName, array);
    DeliverableMessage(message, 0).deliverTo(queue);
}

}} // namespace ha
