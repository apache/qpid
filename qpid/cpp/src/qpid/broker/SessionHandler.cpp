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
#include "ConnectionState.h"
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
    : InOutHandler(0, &out),
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
        if (!ignoring) {
            if (m && invoke(static_cast<AMQP_ServerOperations::Session010Handler&>(*this), *m)) {
                return;
            } else if (session.get()) {
                session->handle(f);
            } else {
                throw ChannelErrorException(
                    QPID_MSG("Channel " << channel.get() << " is not open"));
            }
        }
    } catch(const ChannelException& e) {
        ignoring=true;          // Ignore trailing frames sent by client.
        session->detach();
        session.reset();
        //TODO: implement new exception handling mechanism
        //peerSession.closed(e.code, e.what());
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
        peerSession.flush(false, false, true);
}

void SessionHandler::assertAttached(const char* method) const {
    if (!session.get()) {
        std::cout << "SessionHandler::assertAttached() failed for " << method << std::endl;
        throw ChannelErrorException(
            QPID_MSG(method << " failed: No session for channel "
                     << getChannel()));
    }
}

void SessionHandler::assertClosed(const char* method) const {
    if (session.get())
        throw ChannelBusyException(
            QPID_MSG(method << " failed: channel " << channel.get()
                     << " is already open."));
}

void SessionHandler::localSuspend() {
    if (session.get() && session->isAttached()) {
        session->detach();
        connection.broker.getSessionManager().suspend(session);
        session.reset();
    }
}


ConnectionState& SessionHandler::getConnection() { return connection; }
const ConnectionState& SessionHandler::getConnection() const { return connection; }

//new methods:
void SessionHandler::attach(const std::string& name, bool /*force*/)
{
    //TODO: need to revise session manager to support resume as well
    assertClosed("attach");
    std::auto_ptr<SessionState> state(
        connection.broker.getSessionManager().open(*this, 0));
    session.reset(state.release());
    peerSession.attached(name);
}

void SessionHandler::attached(const std::string& /*name*/)
{
    std::auto_ptr<SessionState> state(connection.broker.getSessionManager().open(*this, 0));
    session.reset(state.release());
}

void SessionHandler::detach(const std::string& name)
{
    assertAttached("detach");
    localSuspend();
    peerSession.detached(name, 0);
    assert(&connection.getChannel(channel.get()) == this);
    connection.closeChannel(channel.get()); 
}

void SessionHandler::detached(const std::string& name, uint8_t code)
{
    ignoring=false;
    session->detach();
    session.reset();
    if (code) {
        //no error
    } else {
        //error occured
        QPID_LOG(warning, "Received session.closed: "<< name << " " << code);
    }
}

void SessionHandler::requestTimeout(uint32_t t)
{
    session->setTimeout(t);
    //proxy.timeout(t);
}

void SessionHandler::timeout(uint32_t)
{
    //not sure what we need to do on the server for this...
}

void SessionHandler::commandPoint(const framing::SequenceNumber& id, uint64_t offset)
{
    if (offset) throw NotImplementedException("Non-zero byte offset not yet supported for command-point");
    
    session->next = id;
}

void SessionHandler::expected(const framing::SequenceSet& commands, const framing::Array& fragments)
{
    if (!commands.empty() || fragments.size()) {
        throw NotImplementedException("Session resumption not yet supported");
    }
}

void SessionHandler::confirmed(const framing::SequenceSet& /*commands*/, const framing::Array& /*fragments*/)
{
    //don't really care too much about this yet
}

void SessionHandler::completed(const framing::SequenceSet& commands, bool timelyReply)
{
    session->complete(commands);
    if (timelyReply) {
        peerSession.knownCompleted(session->knownCompleted);
        session->knownCompleted.clear();
    }
}

void SessionHandler::knownCompleted(const framing::SequenceSet& commands)
{
    session->completed.remove(commands);
}

void SessionHandler::flush(bool expected, bool confirmed, bool completed)
{
    if (expected) {
        peerSession.expected(SequenceSet(session->next), Array());
    }
    if (confirmed) {
        peerSession.confirmed(session->completed, Array());
    }
    if (completed) {
        peerSession.completed(session->completed, true);
    }
}


void SessionHandler::sendCompletion()
{
    peerSession.completed(session->completed, true);
}

void SessionHandler::gap(const framing::SequenceSet& /*commands*/)
{
    throw NotImplementedException("gap not yet supported");
}
    
}} // namespace qpid::broker
