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
struct BrokerHandler : public FrameHandler, private ChannelAdapter {
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
        channel(connection, 1, 0),
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
    virtual RequestId send(shared_ptr<AMQBody>, Correlator::Action) { return 0; }
};

/** Wrap plain AMQFrames in SessionFrames */
struct FrameWrapperHandler : public FrameHandler {

    FrameWrapperHandler(const Uuid& id, bool dir, SessionFrameHandler::Chain next_)
        : uuid(id), direction(dir), next(next_) {
        assert(!uuid.isNull());
    }
    
    void handle(AMQFrame& frame) {
        SessionFrame sf(uuid, frame, direction);
        assert(next);
        next->handle(sf);
    }

    Uuid uuid;
    bool direction;
    SessionFrameHandler::Chain next;
};

SessionManager::SessionManager(Broker& b) : localBroker(new BrokerHandler(b)) {}

void SessionManager::update(FrameHandler::Chains& chains) {
    Mutex::ScopedLock l(lock);
    // Create a new local session, store local chains.
    Uuid uuid(true);
    sessions[uuid] = chains;
    
    // Replace local in chain. Build from the back.
    // TODO aconway 2007-07-05: Currently mcast wiring, bypass
    // everythign else.
    assert(clusterSend);
    FrameHandler::Chain wiring(new FrameWrapperHandler(uuid, SessionFrame::IN, clusterSend));
    FrameHandler::Chain classify(new ClassifierHandler(wiring, chains.in));
    chains.in = classify;

    // Leave out chain unmodified.
    // TODO aconway 2007-07-05: Failover will require replication of
    // outgoing frames to session replicas.
}

void SessionManager::handle(SessionFrame& frame) {
    // Incoming from cluster.
    {
        Mutex::ScopedLock l(lock);
        assert(frame.isIncoming); // FIXME aconway 2007-07-24: Drop isIncoming?
        SessionMap::iterator i = sessions.find(frame.uuid);
        if (i == sessions.end()) {
            // Non local method frame, invoke.
            localBroker->handle(frame.frame);
        }
        else {
            // Local frame, continue on local chain
            i->second.in->handle(frame.frame);
        }
    }
}

}} // namespace qpid::cluster
