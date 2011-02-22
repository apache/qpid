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
#include "BrokerHandler.h"
#include "WiringHandler.h"
#include "MessageHandler.h"
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
    eventHandler(new EventHandler(*this))
{
    eventHandler->add(boost::shared_ptr<HandlerBase>(new WiringHandler(*eventHandler)));
    eventHandler->add(boost::shared_ptr<HandlerBase>(new MessageHandler(*eventHandler)));

    std::auto_ptr<BrokerHandler> bh(new BrokerHandler(*this));
    brokerHandler = bh.get();
    // BrokerHandler belongs to Broker
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
    // FIXME aconway 2010-10-20: use Multicaster, or bring in its features.
    // here we multicast Frames rather than Events.
    framing::AMQFrame f(body);
    std::string data(f.encodedSize(), char());
    framing::Buffer buf(&data[0], data.size());
    f.encode(buf);
    iovec iov = { buf.getPointer(), buf.getSize() };
    while (!eventHandler->getCpg().mcast(&iov, 1))
        ::usleep(1000);      // FIXME aconway 2010-10-20: flow control
}

}} // namespace qpid::cluster
