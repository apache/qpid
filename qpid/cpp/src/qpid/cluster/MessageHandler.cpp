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

#include "Core.h"
#include "MessageHandler.h"
#include "BrokerHandler.h"
#include "EventHandler.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/QueueRegistry.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/Buffer.h"
#include "qpid/sys/Thread.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace cluster {
using namespace broker;

MessageHandler::MessageHandler(EventHandler& e) :
    broker(e.getCore().getBroker()),
    eventHandler(e),
    brokerHandler(e.getCore().getBrokerHandler())
{}

MessageHandler::~MessageHandler() {}

MemberId MessageHandler::sender() { return eventHandler.getSender(); }
MemberId MessageHandler::self() { return eventHandler.getSelf(); }

void MessageHandler::routing(uint64_t sequence, const std::string& message) {
    MessageId id(sender(), sequence);
    boost::intrusive_ptr<Message> msg;
    if (sender() == self())
        msg = eventHandler.getCore().getRoutingMap().get(sequence);
    if (!msg) {
        framing::Buffer buf(const_cast<char*>(&message[0]), message.size());
        msg = new Message;
        msg->decodeHeader(buf);
        msg->decodeContent(buf);
    }
    routingMap[id] = msg;
}

void MessageHandler::enqueue(uint64_t sequence, const std::string& q) {
    MessageId id(sender(), sequence);
    boost::shared_ptr<Queue> queue = broker.getQueues().find(q);
    if (!queue) throw Exception(QPID_MSG("Cluster message for unknown queue " << q));
    boost::intrusive_ptr<Message> msg = routingMap[id];
    if (!msg) throw Exception(QPID_MSG("Unknown cluster message for queue " << q));
    BrokerHandler::ScopedSuppressReplication ssr;
    // TODO aconway 2010-10-21: configable option for strict (wait
    // for CPG deliver to do local deliver) vs.  loose (local deliver
    // immediately).
    queue->deliver(msg);
}

void MessageHandler::routed(uint64_t sequence) {
    MessageId id(sender(), sequence);
    routingMap.erase(id);
    eventHandler.getCore().getRoutingMap().erase(sequence);
}

}} // namespace qpid::cluster
