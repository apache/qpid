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

#include "qpid/log/Statement.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "SessionManager.h"
#include "ClassifierHandler.h"

namespace qpid {
namespace cluster {

using namespace framing;
using namespace sys;

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

SessionManager::SessionManager() {}

void SessionManager::update(FrameHandler::Chains& chains)
{
    Mutex::ScopedLock l(lock);
    // Create a new local session, store local chains.
    Uuid uuid(true);
    sessions[uuid] = chains;
    
    // Replace local incoming chain. Build from the back.
    // 
    // TODO aconway 2007-07-05: Currently mcast wiring, bypass
    // everythign else.
    assert(clusterSend);
    FrameHandler::Chain wiring(new FrameWrapperHandler(uuid, SessionFrame::IN, clusterSend));
    FrameHandler::Chain classify(new ClassifierHandler(wiring, chains.in));
    chains.in = classify;

    // FIXME aconway 2007-07-05: Need to stop bypassed frames
    // from overtaking mcast frames.
    // 

    // Leave outgoing chain unmodified.
    // TODO aconway 2007-07-05: Failover will require replication of
    // outgoing frames to session replicas.
    
}

void SessionManager::handle(SessionFrame& frame) {
    // Incoming from frame.
    FrameHandler::Chains chains;
    {
        Mutex::ScopedLock l(lock);
        SessionMap::iterator i = sessions.find(frame.uuid);
        if (i == sessions.end()) {
            QPID_LOG(trace, "Non-local frame cluster: " << frame.frame);
            chains = nonLocal;
        }
        else {
            QPID_LOG(trace, "Local frame from cluster: " << frame.frame);
            chains = i->second;
        }
    }
    FrameHandler::Chain chain =
        chain = frame.isIncoming ? chains.in : chains.out;
    // TODO aconway 2007-07-11: Should this be assert(chain)
    if (chain)
        chain->handle(frame.frame);

    // TODO aconway 2007-07-05: Here's where we should unblock frame
    // dispatch for the channel.
}

}} // namespace qpid::cluster
