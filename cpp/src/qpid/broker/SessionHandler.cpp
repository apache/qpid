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
#include "qpid/framing/all_method_bodies.h"
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
      ignoring(false)
{}

SessionHandler::~SessionHandler() {}

namespace {
ClassId classId(AMQMethodBody* m) { return m ? m->amqpMethodId() : 0; }
MethodId methodId(AMQMethodBody* m) { return m ? m->amqpClassId() : 0; }
} // namespace

void SessionHandler::handleIn(AMQFrame& f) {
    // Note on channel states: a channel is attached if session != 0
    AMQMethodBody* m = f.getBody()->getMethod();
    try {
        if (ignoring && !(m && m->isA<SessionDetachedBody>())) {
            return;
        }
        if (m && isValid(m) && invoke(static_cast<AMQP_ServerOperations::SessionHandler&>(*this), *m)) {
            //frame was a valid session control and has been handled
            return;
        } else if (session.get()) {
            //we are attached and frame was not a session control so it is for upper layers
            session->handle(f);
        } else if (m && m->isA<SessionDetachedBody>()) {
            handleDetach();
            connection.closeChannel(channel.get()); 
        } else {
            throw NotAttachedException(QPID_MSG("Channel " << channel.get() << " is not attached"));                
        }
    }catch(const ChannelException& e){
        QPID_LOG(error, "Session detached due to: " << e.what());
        peerSession.detached(name, e.code);
        handleDetach();
        connection.closeChannel(channel.get()); 
    }catch(const ConnectionException& e){
        connection.close(e.code, e.what(), classId(m), methodId(m));
    }catch(const std::exception& e){
        connection.close(501, e.what(), classId(m), methodId(m));
    }
}

bool SessionHandler::isValid(AMQMethodBody* m) {
    return session.get() || m->isA<SessionAttachBody>() || m->isA<SessionAttachedBody>();
}

void SessionHandler::handleOut(AMQFrame& f) {
    channel.handle(f);          // Send it.
    if (session->sent(f))
        peerSession.flush(false, false, true);
}

void SessionHandler::assertAttached(const char* method) const {
    if (!session.get()) {
        std::cout << "SessionHandler::assertAttached() failed for " << method << std::endl;
        throw NotAttachedException(
            QPID_MSG(method << " failed: No session for channel "
                     << getChannel()));
    }
}

void SessionHandler::assertClosed(const char* method) const {
    if (session.get())
        throw SessionBusyException(
            QPID_MSG(method << " failed: channel " << channel.get()
                     << " is already open."));
}

ConnectionState& SessionHandler::getConnection() { return connection; }
const ConnectionState& SessionHandler::getConnection() const { return connection; }

//new methods:
void SessionHandler::attach(const std::string& _name, bool /*force*/)
{
    name = _name;//TODO: this should be used in conjunction with
                 //userid for connection as sessions identity

    //TODO: need to revise session manager to support resume as well
    assertClosed("attach");
    session.reset(new SessionState(0, this, 0, 0, name));
    peerSession.attached(name);
    peerSession.commandPoint(session->nextOut, 0);
}

void SessionHandler::attached(const std::string& _name)
{
    name = _name;//TODO: this should be used in conjunction with
                 //userid for connection as sessions identity
    session.reset(new SessionState(0, this, 0, 0, name));
    peerSession.commandPoint(session->nextOut, 0);
}

void SessionHandler::detach(const std::string& name)
{
    assertAttached("detach");
    peerSession.detached(name, session::NORMAL);
    handleDetach();
    assert(&connection.getChannel(channel.get()) == this);
    connection.closeChannel(channel.get()); 
}

void SessionHandler::detached(const std::string& name, uint8_t code)
{
    ignoring = false;
    handleDetach();
    if (code) {
        //no error
    } else {
        //error occured
        QPID_LOG(warning, "Received session.closed: "<< name << " " << code);
    }
    connection.closeChannel(channel.get()); 
}

void SessionHandler::handleDetach()
{
    if (session.get()) {
        session->detach();
        session.reset();
    }
}

void SessionHandler::requestDetach()
{
    //TODO: request timeout when python can handle it
    //peerSession.requestTimeout(0);
    ignoring = true;
    peerSession.detach(name);
}

void SessionHandler::requestTimeout(uint32_t t)
{
    session->setTimeout(t);
    peerSession.timeout(t);
}

void SessionHandler::timeout(uint32_t t)
{
    session->setTimeout(t);
}

void SessionHandler::commandPoint(const framing::SequenceNumber& id, uint64_t offset)
{
    if (offset) throw NotImplementedException("Non-zero byte offset not yet supported for command-point");
    
    session->nextIn = id;
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
        peerSession.expected(SequenceSet(session->nextIn), Array());
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
