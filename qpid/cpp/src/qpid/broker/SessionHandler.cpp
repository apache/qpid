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
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>

namespace qpid {
namespace broker {
using namespace framing;
using namespace std;
using namespace qpid::sys;

SessionHandler::SessionHandler(Connection& c, ChannelId ch)
    : SessionContext(c.getOutput()),
      connection(c), channel(ch, &c.getOutput()),
      proxy(out),               // Via my own handleOut() for L2 data.
      peerSession(channel),     // Direct to channel for L2 commands.
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
        if (m && invoke(static_cast<AMQP_ServerOperations::SessionHandler&>(*this), *m)) {
            return;
        } else if (session.get()) {
            boost::optional<SequenceNumber> ack=session->received(f);
            session->in.handle(f);
            if (ack)
                peerSession.ack(*ack, SequenceNumberSet());
        } else if (m && invoke(static_cast<AMQP_ClientOperations::SessionHandler&>(*this), *m)) {
            return;
        } else if (!ignoring) {
            throw ChannelErrorException(
                QPID_MSG("Channel " << channel.get() << " is not open"));
        }
    } catch(const ChannelException& e) {
        ignoring=true;          // Ignore trailing frames sent by client.
        session->detach();
        session.reset();
        peerSession.closed(e.code, e.what());
    }catch(const ConnectionException& e){
        connection.close(e.code, e.what(), classId(m), methodId(m));
    }catch(const std::exception& e){
        connection.close(
            framing::INTERNAL_ERROR, e.what(), classId(m), methodId(m));
    }
}

void SessionHandler::handleOut(AMQFrame& f) {
    channel.handle(f);          // Send it.
    if (session->sent(f))
        peerSession.solicitAck();
}

void SessionHandler::assertAttached(const char* method) const {
    if (!session.get())
        throw ChannelErrorException(
            QPID_MSG(method << " failed: No session for channel "
                     << getChannel()));
}

void SessionHandler::assertClosed(const char* method) const {
    if (session.get())
        throw ChannelBusyException(
            QPID_MSG(method << " failed: channel " << channel.get()
                     << " is already open."));
}

void  SessionHandler::open(uint32_t detachedLifetime) {
    assertClosed("open");
    std::auto_ptr<SessionState> state(
        connection.broker.getSessionManager().open(*this, detachedLifetime));
    session.reset(state.release());
    peerSession.attached(session->getId(), session->getTimeout());
}

void  SessionHandler::resume(const Uuid& id) {
    assertClosed("resume");
    session = connection.broker.getSessionManager().resume(id);
    session->attach(*this);
    SequenceNumber seq = session->resuming();
    peerSession.attached(session->getId(), session->getTimeout());
    proxy.getSession().ack(seq, SequenceNumberSet());
}

void  SessionHandler::flow(bool /*active*/) {
    assertAttached("flow");
    // TODO aconway 2007-09-19: Removed in 0-10, remove 
    assert(0); throw NotImplementedException("session.flow");
}

void  SessionHandler::flowOk(bool /*active*/) {
    assertAttached("flowOk");
    // TODO aconway 2007-09-19: Removed in 0-10, remove 
    assert(0); throw NotImplementedException("session.flowOk");
}

void  SessionHandler::close() {
    assertAttached("close");
    QPID_LOG(info, "Received session.close");
    ignoring=false;
    session->detach();
    session.reset();
    peerSession.closed(REPLY_SUCCESS, "ok");
    assert(&connection.getChannel(channel.get()) == this);
    connection.closeChannel(channel.get()); 
}

void  SessionHandler::closed(uint16_t replyCode, const string& replyText) {
    QPID_LOG(warning, "Received session.closed: "<<replyCode<<" "<<replyText);
    ignoring=false;
    session->detach();
    session.reset();
}

void SessionHandler::localSuspend() {
    if (session.get() && session->isAttached()) {
        session->detach();
        connection.broker.getSessionManager().suspend(session);
        session.reset();
    }
}

void  SessionHandler::suspend() {
    assertAttached("suspend");
    localSuspend();
    peerSession.detached();
    assert(&connection.getChannel(channel.get()) == this);
    connection.closeChannel(channel.get()); 
}

void  SessionHandler::ack(uint32_t     cumulativeSeenMark,
                          const SequenceNumberSet& /*seenFrameSet*/)
{
    assertAttached("ack");
    if (session->getState() == SessionState::RESUMING) {
        session->receivedAck(cumulativeSeenMark);
        framing::SessionState::Replay replay=session->replay();
        std::for_each(replay.begin(), replay.end(),
                      boost::bind(&SessionHandler::handleOut, this, _1));
    }
    else
        session->receivedAck(cumulativeSeenMark);
}

void  SessionHandler::highWaterMark(uint32_t /*lastSentMark*/) {
    // TODO aconway 2007-10-02: may be removed from spec.
    assert(0); throw NotImplementedException("session.high-water-mark");
}

void  SessionHandler::solicitAck() {
    assertAttached("solicit-ack");
    peerSession.ack(session->sendingAck(), SequenceNumberSet());    
}

void SessionHandler::attached(const Uuid& /*sessionId*/, uint32_t detachedLifetime)
{
    std::auto_ptr<SessionState> state(
        connection.broker.getSessionManager().open(*this, detachedLifetime));
    session.reset(state.release());
}

void SessionHandler::detached()
{
    connection.broker.getSessionManager().suspend(session);
    session.reset();
}


ConnectionState& SessionHandler::getConnection() { return connection; }
const ConnectionState& SessionHandler::getConnection() const { return connection; }

}} // namespace qpid::broker
