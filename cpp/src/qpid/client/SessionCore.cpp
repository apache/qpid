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
#include <boost/bind.hpp>
#include "Future.h"
#include "FutureResponse.h"
#include "FutureResult.h"

using namespace qpid::client;
using namespace qpid::framing;

SessionCore::SessionCore(uint16_t _id, boost::shared_ptr<framing::FrameHandler> out, 
                         uint64_t maxFrameSize) : l3(maxFrameSize), id(_id), sync(false), isClosed(false)
{
    l2.out = boost::bind(&FrameHandler::handle, out, _1);
    l2.in = boost::bind(&ExecutionHandler::handle, &l3, _1);
    l3.out = boost::bind(&ChannelHandler::outgoing, &l2, _1);
    l2.onClose = boost::bind(&SessionCore::closed, this, _1, _2);
}

void SessionCore::open()
{
    l2.open(id);
}

ExecutionHandler& SessionCore::getExecution()
{
    checkClosed();
    return l3;
}

FrameSet::shared_ptr SessionCore::get()
{
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

void SessionCore::close()
{
    l2.close();
    stop();
}

void SessionCore::stop()
{
    l3.getDemux().close();
    l3.getCompletionTracker().close();
}

void SessionCore::handle(AMQFrame& frame)
{
    l2.incoming(frame);
}

void SessionCore::closed(uint16_t code, const std::string& text)
{
    stop();

    isClosed = true;
    reason.code = code;
    reason.text = text;
}

void SessionCore::checkClosed()
{
    if (isClosed) {
        throw ChannelException(reason.code, reason.text);
    }
}

Future SessionCore::send(const AMQBody& command)
{ 
    checkClosed();

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
