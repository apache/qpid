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

// FIXME aconway 2007-08-31: the SessionHandler should create its
// private proxy directly on the connections out handler.
// Session/channel methods should not go thru the other layers.
// Need to get rid of ChannelAdapter and allow proxies to be created
// directly on output handlers.
// 
framing::AMQP_ClientProxy& SessionHandler::getProxy() {
    return session->getProxy();
}

SessionHandler::SessionHandler(Connection& c, ChannelId ch)
    : connection(c), channel(ch), ignoring(false)
{
    in = this;
    out = &c.getOutput();
}

SessionHandler::~SessionHandler() {}

namespace {
ClassId classId(AMQMethodBody* m) { return m ? m->amqpMethodId() : 0; }
MethodId methodId(AMQMethodBody* m) { return m ? m->amqpClassId() : 0; }
} // namespace

void SessionHandler::handle(AMQFrame& f) {
    // Note on channel states: a channel is open if session != 0.  A
    // channel that is closed (session == 0) can be in the "ignoring"
    // state. This is a temporary state after we have sent a channel
    // exception, where extra frames might arrive that should be
    // ignored.
    // 
    AMQMethodBody* m=f.getMethod();
    try {
        if (m && m->invoke(static_cast<Invocable*>(this)))
            return;
        else if (session)
            session->in(f);
        else if (!ignoring)
            throw ChannelErrorException(
                QPID_MSG("Channel " << channel << " is not open"));
    } catch(const ChannelException& e){
        getProxy().getChannel().close(
            e.code, e.toString(), classId(m), methodId(m));
        session.reset();
        ignoring=true;          // Ignore trailing frames sent by client.
    }catch(const ConnectionException& e){
        connection.close(e.code, e.what(), classId(m), methodId(m));
    }catch(const std::exception& e){
        connection.close(
            framing::INTERNAL_ERROR, e.what(), classId(m), methodId(m));
    }
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

void SessionHandler::open(const string& /*outOfBand*/){
    assertClosed("open");
    session.reset(new Session(*this, 0));
    getProxy().getChannel().openOk();
} 

// FIXME aconway 2007-08-31: flow is no longer in the spec.
void SessionHandler::flow(bool active){
    session->flow(active);
    getProxy().getChannel().flowOk(active);
}

void SessionHandler::flowOk(bool /*active*/){}
        
void SessionHandler::close(uint16_t replyCode,
                           const string& replyText,
                           uint16_t classId, uint16_t methodId)
{
    // FIXME aconway 2007-08-31: Extend constants.h to map codes & ids
    // to text names.
    QPID_LOG(warning, "Received session.close("<<replyCode<<","
             <<replyText << ","
             << "classid=" <<classId<< ","
             << "methodid=" <<methodId);
    ignoring=false;
    getProxy().getChannel().closeOk();
    // FIXME aconway 2007-08-31: sould reset session BEFORE
    // sending closeOK to avoid races. SessionHandler
    // needs its own private proxy, see getProxy() above.
    session.reset();
    // No need to remove from connection map, will be re-used
    // if channel is re-opened.
} 
        
void SessionHandler::closeOk(){
    ignoring=false;
}

void SessionHandler::ok() 
{
    //no specific action required, generic response handling should be
    //sufficient
}

}} // namespace qpid::broker
