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

namespace qpid {
namespace cluster {

Core::Core(const Settings& s, broker::Broker& b) :
    broker(b),
    settings(s)
{
    // FIXME aconway 2011-09-23: multi-group
    groups.push_back(new Group(*this));
    boost::intrusive_ptr<QueueHandler> queueHandler(
        new QueueHandler(groups[0]->getEventHandler(), groups[0]->getMulticaster(), settings));
    groups[0]->getEventHandler().add(queueHandler);
    groups[0]->getEventHandler().add(boost::intrusive_ptr<HandlerBase>(
                              new WiringHandler(groups[0]->getEventHandler(), queueHandler, broker)));
    groups[0]->getEventHandler().add(boost::intrusive_ptr<HandlerBase>(
                              new MessageHandler(groups[0]->getEventHandler(), *this)));

    std::auto_ptr<BrokerContext> bh(new BrokerContext(*this, queueHandler));
    brokerHandler = bh.get();
    // BrokerContext belongs to Broker
    broker.setCluster(std::auto_ptr<broker::Cluster>(bh));
    // FIXME aconway 2011-09-26: multi-group
    groups[0]->getEventHandler().start();
    groups[0]->getEventHandler().getCpg().join(s.name);
    // TODO aconway 2010-11-18: logging standards
    // FIXME aconway 2011-09-26: multi-group
    QPID_LOG(notice, "cluster: joined " << s.name << ", member-id="<< groups[0]->getEventHandler().getSelf());
}

void Core::initialize() {}

void Core::fatal() {
    // FIXME aconway 2010-10-20: error handling
    assert(0);
    broker::SignalHandler::shutdown();
}

Group& Core::getGroup(size_t hashValue) {
    return *groups[hashValue % groups.size()];
}

}} // namespace qpid::cluster
