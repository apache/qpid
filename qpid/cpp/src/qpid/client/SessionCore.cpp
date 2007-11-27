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

#include "SessionCore.h"
#include "Future.h"
#include "FutureResponse.h"
#include "FutureResult.h"
#include "ConnectionImpl.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/constants.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/log/Statement.h"

#include <boost/bind.hpp>

namespace qpid {
namespace client {

using namespace qpid::framing;

namespace { const std::string OK="ok"; }

typedef sys::Monitor::ScopedLock  Lock;
typedef sys::Monitor::ScopedUnlock  UnLock;

inline void SessionCore::invariant() const {
    switch (state.get()) {
      case OPENING:
        assert(!session);
        assert(code==REPLY_SUCCESS);
        assert(connection);
        assert(channel.get());
        assert(channel.next == connection.get());
        break;
      case RESUMING:
        assert(session);
        assert(session->getState() == SessionState::RESUMING);
        assert(code==REPLY_SUCCESS);
        assert(connection);
        assert(channel.get());
        assert(channel.next == connection.get());
        break;
      case OPEN:
      case CLOSING:
      case SUSPENDING:
        assert(session);
        assert(connection);
        assert(channel.get());
        assert(channel.next == connection.get());
        break;
      case SUSPENDED:
        assert(session);
        assert(!connection);
        break;
      case CLOSED:
        assert(!session);
        assert(!connection);
        break;
    }
}

inline void SessionCore::setState(State s) {
    state = s;
    invariant();
}

inline void SessionCore::waitFor(State s) {
    invariant();
    // We can be CLOSED or SUSPENDED by error at any time.
    state.waitFor(States(s, CLOSED, SUSPENDED));
    check();
    invariant();
}

SessionCore::SessionCore(shared_ptr<ConnectionImpl> conn,
                         uint16_t ch, uint64_t maxFrameSize)
    : l3(maxFrameSize),
      sync(false),
      channel(ch),
      proxy(channel),
      state(OPENING),
      detachedLifetime(0)
{
    l3.out = &out;
    attaching(conn);
}

void SessionCore::attaching(shared_ptr<ConnectionImpl> c) {
    assert(c);
    assert(channel.get());
    connection = c;
    channel.next = connection.get();
    code = REPLY_SUCCESS;
    text = OK;
    state = session ? RESUMING : OPENING;
    invariant();
}

SessionCore::~SessionCore() {
    Lock l(state);
    detach(COMMAND_INVALID, "Session deleted");
    state.waitWaiters();
}

void SessionCore::detach(int c, const std::string& t) {
    connection.reset();
    channel.next = 0;
    code=c;
    text=t;
}

void SessionCore::doClose(int code, const std::string& text) {
    if (state != CLOSED) {
        session.reset();
        l3.getDemux().close();
        l3.getCompletionTracker().close();
        detach(code, text);
        setState(CLOSED);
    }
    invariant();
}

void SessionCore::doSuspend(int code, const std::string& text) {
    if (state != CLOSED) {
        invariant();
        detach(code, text);
        session->suspend();
        setState(SUSPENDED);
    }
}

ExecutionHandler& SessionCore::getExecution() { // user thread
    return l3;
}

void SessionCore::setSync(bool s) { // user thread
    sync = s;
}

bool SessionCore::isSync() { // user thread
    return sync;
}

FrameSet::shared_ptr SessionCore::get() { // user thread
    // No lock here: pop does a blocking wait.
    return l3.getDemux().getDefault()->pop();
}

static const std::string CANNOT_REOPEN_SESSION="Cannot re-open a session.";

void SessionCore::open(uint32_t timeout) { // user thread
    Lock l(state);
    check(state==OPENING && !session,
          COMMAND_INVALID, CANNOT_REOPEN_SESSION);
    detachedLifetime=timeout;
    proxy.open(detachedLifetime);
    waitFor(OPEN);
}

void SessionCore::close() { // user thread
    Lock l(state);
    check();
    if (state==OPEN) {
        setState(CLOSING);
        proxy.close();
        waitFor(CLOSED);
    }
    else
        doClose(REPLY_SUCCESS, OK);
}

void SessionCore::suspend() { // user thread
    Lock l(state);
    checkOpen();
    setState(SUSPENDING);
    proxy.suspend();
    waitFor(SUSPENDED);
}

void SessionCore::setChannel(uint16_t ch) { channel=ch; }

static const std::string CANNOT_RESUME_SESSION("Session cannot be resumed.");

void SessionCore::resume(shared_ptr<ConnectionImpl> c) {
    // user thread
    {
        Lock l(state);
        if (state==OPEN)
            doSuspend(REPLY_SUCCESS, OK);
        check(state==SUSPENDED, COMMAND_INVALID, CANNOT_RESUME_SESSION);
        SequenceNumber sendAck=session->resuming();
        attaching(c);
        proxy.resume(getId());
        waitFor(OPEN);
        proxy.ack(sendAck, SequenceNumberSet());
        // FIXME aconway 2007-10-23: Replay inside the lock might be a prolem
        // for large replay sets.
        SessionState::Replay replay=session->replay();
        for (SessionState::Replay::iterator i = replay.begin();
             i != replay.end(); ++i)
        {
            invariant();
            channel.handle(*i);     // Direct to channel.
            check();
        }
    }
}

void SessionCore::assertOpen() const {
    Lock l(state);
    checkOpen();
}

static const std::string UNEXPECTED_SESSION_ATTACHED(
    "Received unexpected session.attached");

static const std::string INVALID_SESSION_RESUME_ID(
    "session.resumed has invalid ID.");

// network thread
void SessionCore::attached(const Uuid& sessionId,
                           uint32_t /*detachedLifetime*/)
{
    Lock l(state);
    invariant();
    check(state == OPENING || state == RESUMING,
          COMMAND_INVALID, UNEXPECTED_SESSION_ATTACHED);
    if (state==OPENING) {        // New session
        // FIXME aconway 2007-10-17: arbitrary ack value of 100 for
        // client, allow configuration.
        session=in_place<SessionState>(100, sessionId);
        setState(OPEN);
    }
    else {                      // RESUMING
        check(sessionId == session->getId(),
              INVALID_ARGUMENT, INVALID_SESSION_RESUME_ID);
        // Don't setState yet, wait for first incoming ack.
    }
}

static const std::string UNEXPECTED_SESSION_DETACHED(
    "Received unexpected session.detached.");

static const std::string UNEXPECTED_SESSION_ACK(
    "Received unexpected session.ack");

void  SessionCore::detached() { // network thread
    Lock l(state);
    check(state == SUSPENDING,
          COMMAND_INVALID, UNEXPECTED_SESSION_DETACHED);
    connection->erase(channel);
    doSuspend(REPLY_SUCCESS, OK);
}

void SessionCore::ack(uint32_t ack, const SequenceNumberSet&) {
    Lock l(state);
    invariant();
    check(state==OPEN || state==RESUMING,
          COMMAND_INVALID, UNEXPECTED_SESSION_ACK);
    session->receivedAck(ack);
    if (state==RESUMING) {
        setState(OPEN);
    }
    invariant();
}

void SessionCore::closed(uint16_t code, const std::string& text)
{ // network thread
    Lock l(state);
    invariant();
    doClose(code, text);
}

// closed by connection
void SessionCore::connectionClosed(uint16_t code, const std::string& text) {
    Lock l(state);
    try {
        doClose(code, text);
    } catch(...) { assert (0); }
}

void SessionCore::connectionBroke(uint16_t code, const std::string& text) {
    Lock l(state);
    try {
        doSuspend(code, text);
    } catch (...) { assert(0); }
}

void SessionCore::check() const { // Called with lock held.
    invariant();
    if (code != REPLY_SUCCESS)
        throwReplyException(code, text);
}
 
void SessionCore::check(bool cond, int newCode, const std::string& msg)  const {
    check();
    if (!cond) {
        const_cast<SessionCore*>(this)->doClose(newCode, msg);
        throwReplyException(code, text);
    }
}

static const std::string SESSION_NOT_OPEN("Session is not open");

void SessionCore::checkOpen() const {
    if (state==SUSPENDED) {
        std::string cause;
        if (code != REPLY_SUCCESS)
            cause=" by :"+text;        
        throw CommandInvalidException(QPID_MSG("Session is suspended" << cause));
    }
    check(state==OPEN, COMMAND_INVALID, SESSION_NOT_OPEN);
}

Future SessionCore::send(const AMQBody& command)
{
    Lock l(state);
    checkOpen();
    command.getMethod()->setSync(sync);
    Future f;
    //any result/response listeners must be set before the command is sent
    if (command.getMethod()->resultExpected()) {
        boost::shared_ptr<FutureResult> r(new FutureResult());
        f.setFutureResult(r);
        //result listener is tied to command id, and is set when that
        //is allocated by the execution handler, so pass it to send
        f.setCommandId(l3.send(command, boost::bind(&FutureResult::received, r, _1)));
    } else {
        if (command.getMethod()->responseExpected()) {
            boost::shared_ptr<FutureResponse> r(new FutureResponse());
            f.setFutureResponse(r);
            l3.getCorrelator().listen(boost::bind(&FutureResponse::received, r, _1));
        }

        f.setCommandId(l3.send(command));
    }
    return f;
}

Future SessionCore::send(const AMQBody& command, const MethodContent& content)
{
    Lock l(state);
    checkOpen();
    //content bearing methods don't currently have responses or
    //results, if that changes should follow procedure for the other
    //send method impl:
    return Future(l3.send(command, content));
}

namespace {
bool isCloseResponse(const AMQFrame& frame) {
    return frame.getMethod() &&
        frame.getMethod()->amqpClassId() == SESSION_CLASS_ID &&
        frame.getMethod()->amqpMethodId() == SESSION_CLOSED_METHOD_ID;
}
}

// Network thread.
void SessionCore::handleIn(AMQFrame& frame) {
    {
        Lock l(state);
        // Ignore frames received while closing other than closed response.
        if (state==CLOSING && !isCloseResponse(frame))
            return;             
    }
    try {
        // Cast to expose private SessionHandler functions.
        if (!invoke(static_cast<SessionHandler&>(*this), *frame.getBody())) {
            session->received(frame);
            l3.handle(frame);
        }
    } catch (const ChannelException& e) {
        QPID_LOG(error, "Channel exception:" << e.what());
        doClose(e.code, e.what());
    } 
}

void SessionCore::handleOut(AMQFrame& frame)
{
    Lock l(state);
    if (state==OPEN) {
        if (detachedLifetime > 0 && session->sent(frame)) 
            proxy.solicitAck();
        channel.handle(frame);
    }
}

void  SessionCore::solicitAck( ) {
    Lock l(state);
    checkOpen();
    proxy.ack(session->sendingAck(), SequenceNumberSet());
}

void SessionCore::flow(bool) {
    assert(0); throw NotImplementedException("session.flow");
}

void  SessionCore::flowOk(bool /*active*/) {
    assert(0); throw NotImplementedException("session.flow");
}

void  SessionCore::highWaterMark(uint32_t /*lastSentMark*/) {
    // TODO aconway 2007-10-02: may be removed from spec.
    assert(0); throw NotImplementedException("session.highWaterMark");
}

const Uuid SessionCore::getId() const {
    if (session)
        return session->getId();
    throw Exception(QPID_MSG("Closed session, no ID."));
}

}} // namespace qpid::client
