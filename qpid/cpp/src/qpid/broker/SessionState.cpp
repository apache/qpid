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
#include "SessionState.h"
#include "SessionManager.h"
#include "SessionHandler.h"
#include "Connection.h"
#include "Broker.h"
#include "SemanticHandler.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace broker {

using namespace framing;

SessionState::SessionState(SessionManager& f, SessionHandler& h, uint32_t timeout_) 
    : factory(f), handler(&h), id(true), timeout(timeout_),
      broker(h.getConnection().broker),
      version(h.getConnection().getVersion())
{
    // FIXME aconway 2007-09-21: Break dependnecy - broker updates session.
    chain.push_back(new SemanticHandler(*this));
    in = &chain[0];             // Incoming frame to handler chain.
    out = &handler->out;        // Outgoing frames to SessionHandler

    // FIXME aconway 2007-09-20: use broker to add plugin
    // handlers to the chain. 
    // FIXME aconway 2007-08-31: Shouldn't be passing channel ID.
    broker.update(handler->getChannel(), *this);       
}

SessionState::~SessionState() {
    // Remove ID from active session list.
    factory.active.erase(getId());
}

SessionHandler& SessionState::getHandler() {
    assert(isAttached());
    return *handler;
}

AMQP_ClientProxy& SessionState::getProxy() {
    return getHandler().getProxy();
}

Connection& SessionState::getConnection() {
    return getHandler().getConnection();
}

}} // namespace qpid::broker
