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

void SessionState::handleIn(AMQFrame& f) { semanticHandler->handle(f); }

void SessionState::handleOut(AMQFrame& f) {
    assert(handler);
    handler->out.handle(f);
}

SessionState::SessionState(
    SessionManager& f, SessionHandler& h, uint32_t timeout_, uint32_t ack) 
    : framing::SessionState(ack, timeout_ > 0),
      factory(f), handler(&h), id(true), timeout(timeout_),
      broker(h.getConnection().broker),
      version(h.getConnection().getVersion()),
      semanticHandler(new SemanticHandler(*this))
{
    // TODO aconway 2007-09-20: SessionManager may add plugin
    // handlers to the chain.
 }

SessionState::~SessionState() {
    // Remove ID from active session list.
    factory.erase(getId());
}

SessionHandler* SessionState::getHandler() {
    return handler;
}

AMQP_ClientProxy& SessionState::getProxy() {
    assert(isAttached());
    return getHandler()->getProxy();
}

Connection& SessionState::getConnection() {
    assert(isAttached());
    return getHandler()->getConnection();
}

void SessionState::detach() {
    handler = 0;
}

void SessionState::attach(SessionHandler& h) {
    handler = &h;
}

}} // namespace qpid::broker
