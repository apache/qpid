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
#include "Session.h"
#include "Connection.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/constants.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
using namespace framing;
using namespace std;

SessionHandler::SessionHandler(Connection& c, ChannelId ch)
    : InOutHandler(0, &c.getOutput()),
      connection(c), channel(ch), proxy(out),
      ignoring(false), channelHandler(*this),
      useChannelClose(false) {}

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
    AMQMethodBody* m=f.getMethod();
    try {
        if (m && (m->invoke(this) || m->invoke(&channelHandler)))
            return;
        else if (session)
            session->in(f);
        else if (!ignoring)
            throw ChannelErrorException(
                QPID_MSG("Channel " << channel << " is not open"));
    } catch(const ChannelException& e) {
        ignoring=true;          // Ignore trailing frames sent by client.
        session.reset();
        // FIXME aconway 2007-09-19: Dual-mode hack.
        if (useChannelClose)
            getProxy().getChannel().close(
                e.code, e.toString(), classId(m), methodId(m));
        else
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
    if (!session)
        throw ChannelErrorException(
            QPID_MSG(""<<method<<" failed: No session for channel "
                     << getChannel()));
}

void SessionHandler::assertClosed(const char* method) {
    // FIXME aconway 2007-08-31: Should raise channel-busy, need
    // to update spec.
    if (session)
        throw PreconditionFailedException(
            QPID_MSG(""<<method<<" failed: "
                     << channel << " already open on channel "
                     << getChannel()));
}

void SessionHandler::ChannelMethods::open(const string& /*outOfBand*/){
    parent.useChannelClose=true;
    parent.assertClosed("open");
    parent.session.reset(new Session(parent, 0));
    parent.getProxy().getChannel().openOk();
} 

// FIXME aconway 2007-08-31: flow is no longer in the spec.
void SessionHandler::ChannelMethods::flow(bool active){
    parent.session->flow(active);
    parent.getProxy().getChannel().flowOk(active);
}

void SessionHandler::ChannelMethods::flowOk(bool /*active*/){}
        
void SessionHandler::ChannelMethods::close(uint16_t replyCode,
                           const string& replyText,
                           uint16_t classId, uint16_t methodId)
{
    // FIXME aconway 2007-08-31: Extend constants.h to map codes & ids
    // to text names.
    QPID_LOG(warning, "Received channel.close("<<replyCode<<","
             <<replyText << ","
             << "classid=" <<classId<< ","
             << "methodid=" <<methodId);
    parent.ignoring=false;
    parent.getProxy().getChannel().closeOk();
    // FIXME aconway 2007-08-31: sould reset session BEFORE
    // sending closeOK to avoid races. SessionHandler
    // needs its own private proxy, see getProxy() above.
    parent.session.reset();
    // No need to remove from connection map, will be re-used
    // if channel is re-opened.
} 
        
void SessionHandler::ChannelMethods::closeOk(){
    parent.ignoring=false;
}

void SessionHandler::ChannelMethods::ok() 
{
    //no specific action required, generic response handling should be
    //sufficient
}

void  SessionHandler::open(uint32_t detachedLifetime) {
    assertClosed("open");
    session.reset(new Session(*this, detachedLifetime));
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
    // No need to remove from connection map, will be re-used
    // if channel is re-opened.
}

void  SessionHandler::closed(uint16_t replyCode, const string& replyText) {
    // FIXME aconway 2007-08-31: Extend constants.h to map codes & ids
    // to text names.
    QPID_LOG(warning, "Received session.closed: "<<replyCode<<" "<<replyText);
    ignoring=false;
    session.reset();
    // No need to remove from connection map, will be re-used
    // if channel is re-opened.
}

void  SessionHandler::resume(const Uuid& /*sessionId*/) {
    assert(0); throw NotImplementedException();
}

void  SessionHandler::suspend() {
    assert(0); throw NotImplementedException();
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
