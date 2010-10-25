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
#include "BrokerHandler.h"
#include "qpid/framing/ClusterMessageRoutingBody.h"
#include "qpid/framing/ClusterMessageRoutedBody.h"
#include "qpid/framing/ClusterMessageEnqueueBody.h"
#include "qpid/sys/Thread.h"
#include "qpid/broker/QueuedMessage.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/Buffer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace cluster {

using namespace framing;
using namespace broker;

namespace {
// noReplicate means the current thread is handling a message
// received from the cluster so it should not be replciated.
QPID_TSS bool noReplicate = false;

// Sequence number of the message currently being routed.
// 0 if we are not currently routing a message.
QPID_TSS SequenceNumber routeSeq = 0;
}

BrokerHandler::ScopedSuppressReplication::ScopedSuppressReplication() {
    assert(!noReplicate);
    noReplicate = true;
}

BrokerHandler::ScopedSuppressReplication::~ScopedSuppressReplication() {
    assert(noReplicate);
    noReplicate = false;
}

BrokerHandler::BrokerHandler(Core& c) : core(c) {}

SequenceNumber BrokerHandler::nextSequenceNumber() {
    SequenceNumber s = ++sequence;
    if (!s) s = ++sequence;     // Avoid 0 on wrap-around.
    return s;
}

void BrokerHandler::routing(const boost::intrusive_ptr<Message>&) { }

bool BrokerHandler::enqueue(Queue& queue, const boost::intrusive_ptr<Message>& msg)
{
    if (noReplicate) return true;
    if (!routeSeq) {             // This is the first enqueue, so send the message
        routeSeq = nextSequenceNumber();
        // FIXME aconway 2010-10-20: replicate message in fixed size buffers.
        std::string data(msg->encodedSize(),char());
        framing::Buffer buf(&data[0], data.size());
        msg->encode(buf);
        core.mcast(ClusterMessageRoutingBody(ProtocolVersion(), routeSeq, data));
        core.getRoutingMap().put(routeSeq, msg);
    }
    core.mcast(ClusterMessageEnqueueBody(ProtocolVersion(), routeSeq, queue.getName()));
    // TODO aconway 2010-10-21: configable option for strict (wait
    // for CPG deliver to do local deliver) vs.  loose (local deliver
    // immediately).
    return false;
}

void BrokerHandler::routed(const boost::intrusive_ptr<Message>&) {
    if (routeSeq) {             // we enqueued at least one message.
        core.mcast(ClusterMessageRoutedBody(ProtocolVersion(), routeSeq));
        // Note: routingMap is cleaned up on CPG delivery in MessageHandler.
        routeSeq = 0;
    }
}

}} // namespace qpid::cluster
