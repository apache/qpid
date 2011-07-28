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
    eventHandler(new EventHandler(*this)),
    multicaster(eventHandler->getCpg(), b.getPoller(), boost::bind(&Core::fatal, this))
{
    boost::intrusive_ptr<QueueHandler> queueHandler(
        new QueueHandler(*eventHandler, multicaster));
    eventHandler->add(queueHandler);
    eventHandler->add(boost::intrusive_ptr<HandlerBase>(
                          new WiringHandler(*eventHandler, queueHandler)));
    eventHandler->add(boost::intrusive_ptr<HandlerBase>(
                          new MessageHandler(*eventHandler)));

    std::auto_ptr<BrokerContext> bh(new BrokerContext(*this, queueHandler));
    brokerHandler = bh.get();
    // BrokerContext belongs to Broker
    broker.setCluster(std::auto_ptr<broker::Cluster>(bh));
    eventHandler->start();
    eventHandler->getCpg().join(s.name);
    // TODO aconway 2010-11-18: logging standards
    QPID_LOG(notice, "cluster: joined " << s.name << ", member-id="<< eventHandler->getSelf());
}

void Core::initialize() {}

void Core::fatal() {
    // FIXME aconway 2010-10-20: error handling
    assert(0);
    broker::SignalHandler::shutdown();
}

void Core::mcast(const framing::AMQBody& body) {
    QPID_LOG(trace, "cluster multicast: " << body);
    multicaster.mcast(body);
}

}} // namespace qpid::cluster
