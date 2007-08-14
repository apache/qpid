/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "SessionManager.h"
#include "ClassifierHandler.h"

#include "qpid/log/Statement.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQP_ServerOperations.h"
#include "qpid/broker/BrokerAdapter.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/BrokerChannel.h"
#include "qpid/framing/ChannelAdapter.h"

namespace qpid {
namespace cluster {

using namespace framing;
using namespace sys;
using namespace broker;

/** Handler to send frames direct to local broker (bypass correlation etc.) */
    struct BrokerHandler : public FrameHandler, private ChannelAdapter, private DeliveryAdapter {
    Connection connection;
    Channel channel;
    BrokerAdapter adapter;

    // TODO aconway 2007-07-23: Lots of needless flab here (Channel,
    // Connection, ChannelAdapter) As these classes are untangled the
    // flab can be reduced. The real requirements are:
    // - Dispatch methods direct to broker bypassing all the correlation muck
    // - Efficiently suppress responses
    // For the latter we are now using a ChannelAdapter with noop send()
    // A more efficient solution would be a no-op proxy.
    // 
    BrokerHandler(Broker& broker) :
        connection(0, broker),
        channel(connection, *this, 1, 0),
        adapter(channel, connection, broker, *this) {}

    void handle(AMQFrame& frame) {
        AMQMethodBody* body=dynamic_cast<AMQMethodBody*>(frame.body.get());
        assert(body);
        body->invoke(adapter, MethodContext()); // TODO aconway 2007-07-24: Remove MethodContext
    }

    // Dummy methods.
    virtual void handleHeader(boost::shared_ptr<AMQHeaderBody>){}
    virtual void handleContent(boost::shared_ptr<AMQContentBody>){}
    virtual void handleHeartbeat(boost::shared_ptr<AMQHeartbeatBody>){}
    virtual bool isOpen() const{ return true; }
    virtual void handleMethodInContext(shared_ptr<AMQMethodBody>, const MethodContext&){}
    // No-op send.
    virtual RequestId send(shared_ptr<AMQBody>) { return 0; }

    //delivery adapter methods, also no-ops:
    virtual DeliveryId deliver(Message::shared_ptr&, DeliveryToken::shared_ptr) { return 0; }
    virtual void redeliver(Message::shared_ptr&, DeliveryToken::shared_ptr, DeliveryId) {}
};

SessionManager::SessionManager(Broker& b) : localBroker(new BrokerHandler(b)) {}

void SessionManager::update(ChannelId channel, FrameHandler::Chains& chains) {
    Mutex::ScopedLock l(lock);
    // Create a new local session, store local chains.
    sessions[channel] = chains;
    
    // Replace local "in" chain to mcast wiring and process other frames
    // as normal.
    assert(clusterSend);
    chains.in = make_shared_ptr(
        new ClassifierHandler(clusterSend, chains.in));
}

void SessionManager::handle(AMQFrame& frame) {
    // Incoming from cluster.
    {
        Mutex::ScopedLock l(lock);
        SessionMap::iterator i = sessions.find(frame.getChannel());
        if (i == sessions.end()) {
            // Non-local wiring method frame, invoke locally.
            localBroker->handle(frame);
        }
        else {
            // Local frame continuing on local chain
            i->second.in->handle(frame);
        }
    }
}

void SessionManager::setClusterSend(const FrameHandler::Chain& send) {
    clusterSend=send;
}

}} // namespace qpid::cluster
