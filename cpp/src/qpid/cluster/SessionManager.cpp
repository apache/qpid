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

#include <boost/utility/in_place_factory.hpp>

namespace qpid {
namespace cluster {

using namespace framing;
using namespace sys;
using namespace broker;

/** Handler to send frames direct to local broker (bypass correlation etc.) */
struct SessionManager::BrokerHandler : public FrameHandler
{
    Connection connection;
    SessionHandler sessionAdapter;
    broker::Session session;
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
        sessionAdapter(connection, 0),
        session(sessionAdapter, 1),
        adapter(session, 0) {}  // FIXME aconway 2008-01-29: 

    void handle(AMQFrame& frame) {
        AMQMethodBody* body=dynamic_cast<AMQMethodBody*>(frame.getBody());
        assert(body);
        body->invoke(adapter);
    }

    // Dummy methods.
    virtual void handleHeader(AMQHeaderBody*){}
    virtual void handleContent(AMQContentBody*){}
    virtual void handleHeartbeat(AMQHeartbeatBody*){}
    virtual bool isOpen() const{ return true; }
    virtual void handleMethod(AMQMethodBody*){}
    // No-op send.
    virtual void send(const AMQBody&) {}

    //delivery adapter methods, also no-ops:
    virtual DeliveryId deliver(intrusive_ptr<Message>&, DeliveryToken::shared_ptr) { return 0; }
    virtual void redeliver(intrusive_ptr<Message>&, DeliveryToken::shared_ptr, DeliveryId) {}
};

SessionManager::~SessionManager(){}

SessionManager::SessionManager(Broker& b, FrameHandler& c)
    : cluster(c), localBroker(new BrokerHandler(b)) {}

void SessionManager::update(ChannelId channel, FrameHandler::Chains& chains) {
    Mutex::ScopedLock l(lock);
    // Create a new local session, store local chains.
    assert(!sessions[channel]);
    boost::optional<Session>& session=sessions[channel];
    session = boost::in_place(boost::ref(cluster), boost::ref(chains.in));
    chains.in = &session->classifier;
}

void SessionManager::handle(AMQFrame& frame) {
    // Incoming from cluster.
    {
        Mutex::ScopedLock l(lock);
        SessionMap::iterator i=sessions.find(frame.getChannel());
        if (i == sessions.end()) {
            // Non-local wiring method frame, invoke locally.
            (*localBroker)(frame);
        }
        else {
            // Local frame continuing on local chain
            assert(i->second);
            i->second->cont(frame);
        }
    }
}

}} // namespace qpid::cluster
