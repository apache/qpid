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
#include "qpid/framing/constants.h"
#include "Future.h"
#include "FutureResponse.h"
#include "FutureResult.h"

#include <boost/bind.hpp>

using namespace qpid::client;
using namespace qpid::framing;

SessionCore::SessionCore(FrameHandler& out_, uint16_t ch, uint64_t maxFrameSize)
    : channel(ch), l2(*this), l3(maxFrameSize), uuid(false), sync(false)
{
    l2.next = &l3;
    l3.out = &out;
    out.next = &out_;
}

SessionCore::~SessionCore() {}

ExecutionHandler& SessionCore::getExecution()
{
    checkClosed();
    return l3;
}

FrameSet::shared_ptr SessionCore::get()
{
    checkClosed();
    return l3.getDemux().getDefault().pop();
}

void SessionCore::setSync(bool s)
{
    sync = s;
}

bool SessionCore::isSync()
{
    return sync;
}

namespace {
struct ClosedOnExit {
    SessionCore& core;
    int code;
    std::string text;
    ClosedOnExit(SessionCore& s, int c, const std::string& t)
        : core(s), code(c), text(t) {}
    ~ClosedOnExit() { core.closed(code, text); }
};
}

void SessionCore::close()
{
    checkClosed();
    ClosedOnExit closer(*this, CHANNEL_ERROR, "Session closed by user.");
    l2.close();
}

void SessionCore::suspend() {
    checkClosed();
    ClosedOnExit closer(*this, CHANNEL_ERROR, "Client session is suspended");
    l2.suspend();
}

void SessionCore::closed(uint16_t code, const std::string& text)
{
    out.next = 0;
    reason.code = code;
    reason.text = text;
    l2.closed();
    l3.getDemux().close();
    l3.getCompletionTracker().close();
}

void SessionCore::checkClosed() const
{
    // TODO: could have been a connection exception
    if(out.next == 0)
        throw ChannelException(reason.code, reason.text);
}

void SessionCore::open(uint32_t detachedLifetime) {
    assert(out.next);
    l2.open(detachedLifetime);
}

void SessionCore::resume(FrameHandler& out_) {
    out.next = &out_;
    l2.resume();
}

Future SessionCore::send(const AMQBody& command)
{ 
    checkClosed();

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
    checkClosed();
    //content bearing methods don't currently have responses or
    //results, if that changes should follow procedure for the other
    //send method impl:
    return Future(l3.send(command, content));
}

void SessionCore::handleIn(AMQFrame& frame) {
    l2.handle(frame);
}

void SessionCore::handleOut(AMQFrame& frame)
{
    checkClosed();
    frame.setChannel(channel);
    out.next->handle(frame);
}

