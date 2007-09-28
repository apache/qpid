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

#include "SessionHandler.h"
#include "qpid/framing/amqp_framing.h"
#include "qpid/framing/all_method_bodies.h"
#include "qpid/client/SessionCore.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"

using namespace qpid::client;
using namespace qpid::framing;
using namespace boost;

namespace {
// TODO aconway 2007-09-28: hack till we have multi-version support.
ProtocolVersion version;
}

SessionHandler::SessionHandler(SessionCore& parent)
    : StateManager(CLOSED), core(parent) {}

SessionHandler::~SessionHandler() {}

void SessionHandler::handle(AMQFrame& frame)
{
    AMQBody* body = frame.getBody();
    if (getState() == OPEN) {
        core.checkClosed();
        SessionClosedBody* closedBody=
            dynamic_cast<SessionClosedBody*>(body->getMethod());
        if (closedBody) {
            closed();
            core.closed(closedBody->getReplyCode(), closedBody->getReplyText());
        } else {
            try {
                next->handle(frame);
            }
            catch(const ChannelException& e){
                QPID_LOG(error, "Channel exception:" << e.what());
                closed();
                AMQFrame f(0, SessionClosedBody(version, e.code, e.toString()));
                core.out(f);
                core.closed(closedBody->getReplyCode(), closedBody->getReplyText());
            }
        }
    } else {
        if (body->getMethod()) 
            handleMethod(body->getMethod());
        else
            throw ConnectionException(504, "Channel not open for content.");
    }
}

void SessionHandler::attach(const AMQMethodBody& command)
{
    setState(OPENING);
    AMQFrame f(0, command);
    core.out(f);
    std::set<int> states;
    states.insert(OPEN);
    states.insert(CLOSED);
    waitFor(states);
    if (getState() != OPEN) 
        throw Exception(QPID_MSG("Failed to attach session to channel "<<core.getChannel()));
}

void SessionHandler::open(uint32_t detachedLifetime) {
    attach(SessionOpenBody(version, detachedLifetime));
}

void SessionHandler::resume() {
    attach(SessionResumeBody(version, core.getId()));
}

void SessionHandler::detach(const AMQMethodBody& command)
{
    setState(CLOSING);
    AMQFrame f(0, command);
    core.out(f);
    waitFor(CLOSED);
}

void SessionHandler::close() { detach(SessionCloseBody(version)); }
void SessionHandler::suspend() { detach(SessionSuspendBody(version)); }
void SessionHandler::closed() { setState(CLOSED); }

void SessionHandler::handleMethod(AMQMethodBody* method)
{
    switch (getState()) {
      case OPENING: {
          SessionAttachedBody* attached = dynamic_cast<SessionAttachedBody*>(method);
          if (attached) {
              core.setId(attached->getSessionId());
              setState(OPEN);
          } else 
              throw ChannelErrorException();
          break;
      }
      case CLOSING:
        if (method->isA<SessionClosedBody>() ||
            method->isA<SessionDetachedBody>())
            closed();
        break;
        
      case CLOSED:
        throw ChannelErrorException();
        
      default:
        assert(0);
        throw InternalErrorException(QPID_MSG("Internal Error."));
    }
}

