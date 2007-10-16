/*
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
#include "SessionState.h"
#include "Connection.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/constants.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
using namespace framing;
using namespace std;

SessionHandler::SessionHandler(Connection& c, ChannelId ch)
    : InOutHandler(0, &c.getOutput()),
      connection(c), channel(ch), proxy(out),
      ignoring(false) {}

SessionHandler::~SessionHandler() {}

namespace {
ClassId classId(AMQMethodBody* m) { return m ? m->amqpMethodId() : 0; }
MethodId methodId(AMQMethodBody* m) { return m ? m->amqpClassId() : 0; }
} // namespace

void SessionHandler::handleIn(AMQFrame& f) {
    // Note on channel states: a channel is open if session != 0.  A
    // channel that is closed (session == 0) can be in the "ignoring"
    // state. This is a temporary state after we have sent a channel
    // exception, where extra frames might arrive that should be
    // ignored.
    //
    AMQMethodBody* m = f.getBody()->getMethod();
    try {
        if (m && invoke(*this, *m))
            return;
        else if (session.get())
            session->in(f);
        else if (!ignoring)
            throw ChannelErrorException(
                QPID_MSG("Channel " << channel << " is not open"));
    } catch(const ChannelException& e) {
        ignoring=true;          // Ignore trailing frames sent by client.
        session.reset();
        getProxy().getSession().closed(e.code, e.toString());
    }catch(const ConnectionException& e){
        connection.close(e.code, e.what(), classId(m), methodId(m));
    }catch(const std::exception& e){
        connection.close(
            framing::INTERNAL_ERROR, e.what(), classId(m), methodId(m));
    }
}

void SessionHandler::handleOut(AMQFrame& f) {
    f.setChannel(getChannel());
    out.next->handle(f);
}

void SessionHandler::assertOpen(const char* method) {
     if (!session.get())
        throw ChannelErrorException(
            QPID_MSG(method << " failed: No session for channel "
                     << getChannel()));
}

void SessionHandler::assertClosed(const char* method) {
    if (session.get())
        throw ChannelBusyException(
            QPID_MSG(method << " failed: channel " << channel
                     << " is already open."));
}

void  SessionHandler::open(uint32_t detachedLifetime) {
    assertClosed("open");
    std::auto_ptr<SessionState> state(
        connection.broker.getSessionManager().open(*this, detachedLifetime));
    session.reset(state.release());
    getProxy().getSession().attached(session->getId(), session->getTimeout());
}

void  SessionHandler::resume(const Uuid& id) {
    assertClosed("resume");
    session = connection.broker.getSessionManager().resume(*this, id);
    getProxy().getSession().attached(session->getId(), session->getTimeout());
}

void  SessionHandler::flow(bool /*active*/) {
    // FIXME aconway 2007-09-19: Removed in 0-10, remove 
    assert(0); throw NotImplementedException();
}

void  SessionHandler::flowOk(bool /*active*/) {
    // FIXME aconway 2007-09-19: Removed in 0-10, remove 
    assert(0); throw NotImplementedException();
}

void  SessionHandler::close() {
    QPID_LOG(info, "Received session.close");
    ignoring=false;
    session.reset();
    getProxy().getSession().closed(REPLY_SUCCESS, "ok");
    assert(&connection.getChannel(channel) == this);
    connection.closeChannel(channel); 
}

void  SessionHandler::closed(uint16_t replyCode, const string& replyText) {
    QPID_LOG(warning, "Received session.closed: "<<replyCode<<" "<<replyText);
    ignoring=false;
    session.reset();
}

void  SessionHandler::suspend() {
    assertOpen("suspend");
    connection.broker.getSessionManager().suspend(session);
    assert(!session.get());
    getProxy().getSession().detached();
    assert(&connection.getChannel(channel) == this);
    connection.closeChannel(channel); 
}

void  SessionHandler::ack(uint32_t     /*cumulativeSeenMark*/,
                          const SequenceNumberSet& /*seenFrameSet*/) {
    assert(0); throw NotImplementedException();
}

void  SessionHandler::highWaterMark(uint32_t /*lastSentMark*/) {
    assert(0); throw NotImplementedException();
}

void  SessionHandler::solicitAck() {
    assert(0); throw NotImplementedException();
}

}} // namespace qpid::broker
