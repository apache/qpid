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

#include "EventHandler.h"
#include "Group.h"
#include "QueueContext.h"
#include "QueueHandler.h"
#include "QueueReplica.h"
#include "Settings.h"
#include "qpid/Exception.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/framing/AllInvoker.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

QueueHandler::QueueHandler(Group& g, Settings& s)
    : HandlerBase(g.getEventHandler()), group(g), consumeTicks(s.consumeTicks)
{}

bool QueueHandler::handle(const framing::AMQFrame& frame) {
    return framing::invoke(*this, *frame.getBody()).wasHandled();
}

void QueueHandler::subscribe(const std::string& queue) {
    find(queue)->subscribe(sender());
}

void QueueHandler::unsubscribe(const std::string& queue,
                               bool resubscribe) {
    find(queue)->unsubscribe(sender(), resubscribe);
}

void QueueHandler::consumed(const std::string& queue,
                            const framing::SequenceSet& acquired,
                            const framing::SequenceSet& dequeued)
{
    find(queue)->consumed(sender(), acquired, dequeued);
}

void QueueHandler::left(const MemberId& member) {
    // Unsubscribe for members that leave.
    for (QueueMap::iterator i = queues.begin(); i != queues.end(); ++i)
        i->second->unsubscribe(member, false);
}

void QueueHandler::add(broker::Queue& q) {
    // Local queues already have a context, remote queues need one.
    if (!QueueContext::get(q))
        new QueueContext(q, group, consumeTicks); // Context attaches to the Queue
    assert(QueueContext::get(q));
    queues[q.getName()] = boost::intrusive_ptr<QueueReplica>(
        new QueueReplica(*QueueContext::get(q), self()));
}

void QueueHandler::remove(broker::Queue& q) {
    queues.erase(q.getName());
}

boost::intrusive_ptr<QueueReplica> QueueHandler::find(const std::string& queue) {
    QueueMap::iterator i = queues.find(queue);
    if (i == queues.end())
        throw Exception(QPID_MSG("Unknown queue " << queue << " in cluster queue handler"));
    return i->second;
}

}} // namespace qpid::cluster
