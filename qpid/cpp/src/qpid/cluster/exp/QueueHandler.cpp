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

#include "QueueHandler.h"
#include "EventHandler.h"
#include "QueueReplica.h"
#include "QueueContext.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

// FIXME aconway 2011-05-11: make Multicaster+EventHandler available as Group, clean this up?
QueueHandler::QueueHandler(EventHandler& eh, Multicaster& m)
    : HandlerBase(eh), multicaster(m) {}

bool QueueHandler::invoke(const framing::AMQBody& body) {
    return framing::invoke(*this, body).wasHandled();
}

void QueueHandler::subscribe(const std::string& queue) {
    find(queue)->subscribe(sender());
}
void QueueHandler::unsubscribe(const std::string& queue) {
    find(queue)->unsubscribe(sender());
}
void QueueHandler::resubscribe(const std::string& queue) {
    find(queue)->resubscribe(sender());
}

void QueueHandler::left(const MemberId& member) {
    // Unsubscribe for members that leave.
    // FIXME aconway 2011-06-28: also need to re-queue acquired messages.
    for (QueueMap::iterator i = queues.begin(); i != queues.end(); ++i)
        i->second->unsubscribe(member);
}

// FIXME aconway 2011-06-08: do we need to hold on to the shared pointer for lifecycle?
void QueueHandler::add(boost::shared_ptr<broker::Queue> q) {
    // FIXME aconway 2011-06-08: move create operation from Wiring to Queue handler.
    // FIXME aconway 2011-05-10: assert not already in map.

    // Local queues already have a context, remote queues need one.
    if (!QueueContext::get(*q))
        new QueueContext(*q, multicaster); // Context attaches itself to the Queue
    queues[q->getName()] = boost::intrusive_ptr<QueueReplica>(
        new QueueReplica(q, self()));
}

boost::intrusive_ptr<QueueReplica> QueueHandler::find(const std::string& queue) {
    QueueMap::iterator i = queues.find(queue);
    if (i == queues.end())
        throw Exception(QPID_MSG("Unknown queue " << queue << " in cluster queue handler"));
    return i->second;
}

}} // namespace qpid::cluster
