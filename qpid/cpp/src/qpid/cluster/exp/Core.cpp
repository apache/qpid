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
#include "EventHandler.h"
#include "BrokerContext.h"
#include "WiringHandler.h"
#include "MessageHandler.h"
#include "QueueContext.h"
#include "QueueHandler.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/SignalHandler.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/Buffer.h"
#include "qpid/log/Statement.h"
#include <sys/uio.h>            // For iovec
#include <boost/lexical_cast.hpp>

namespace qpid {
namespace cluster {

Core::Core(const Settings& s, broker::Broker& b) : broker(b), settings(s)
{
    // FIXME aconway 2011-09-26: S.concurrency has to be consistent in a
    // cluster, negotiate as part of join protocol.
    uint32_t nGroups = s.concurrency ? s.concurrency : 1;
    for (size_t i = 0; i < nGroups; ++i) {
        // FIXME aconway 2011-09-26: review naming. Create group for non-message traffic, e.g. initial join protocol?
        std::string groupName = s.name + "-" + boost::lexical_cast<std::string>(i);
        groups.push_back(new Group(*this));
        boost::intrusive_ptr<Group> group(groups.back());
 
        EventHandler& eh(group->getEventHandler());
        typedef boost::intrusive_ptr<HandlerBase>  HandlerBasePtr;
        boost::intrusive_ptr<QueueHandler> queueHandler(new QueueHandler(*group, settings));
        eh.add(queueHandler);
        eh.add(HandlerBasePtr(new WiringHandler(*group, queueHandler, broker)));
        eh.add(HandlerBasePtr(new MessageHandler(*group, *this)));

        std::auto_ptr<BrokerContext> bh(new BrokerContext(*this));
        brokerHandler = bh.get();
        // BrokerContext belongs to Broker
        broker.setCluster(std::auto_ptr<broker::Cluster>(bh));
        // FIXME aconway 2011-09-26: multi-group
        eh.start();
        eh.getCpg().join(groupName);
        // TODO aconway 2010-11-18: logging standards        // FIXME aconway 2011-09-26: multi-group
        QPID_LOG(debug, "cluster: joined CPG group " << groupName << ", member-id=" << eh.getSelf());
    }
    QPID_LOG(notice, "cluster: joined cluster " << s.name
             << ", member-id="<< groups[0]->getEventHandler().getSelf());
    QPID_LOG(debug, "cluster: consume-lock=" << s.consumeLockMicros << "us "
             << " concurrency=" << s.concurrency);
}

void Core::initialize() {}

void Core::fatal() {
    broker::SignalHandler::shutdown();
}

Group& Core::getGroup(size_t hashValue) {
    return *groups[hashValue % groups.size()];
}

}} // namespace qpid::cluster
