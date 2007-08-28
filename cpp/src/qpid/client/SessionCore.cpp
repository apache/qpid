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

using namespace qpid::client;
using namespace qpid::framing;

SessionCore::SessionCore(uint16_t _id, boost::shared_ptr<framing::FrameHandler> out, 
                         uint64_t maxFrameSize) : l3(maxFrameSize), id(_id), sync(false)
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

void SessionCore::flush()
{
    l3.sendFlush();
}

Response SessionCore::send(const AMQMethodBody& method, bool expectResponse)
{
    boost::shared_ptr<FutureResponse> f(futures.createResponse());
    if (expectResponse) {
        l3.send(method, boost::bind(&FutureResponse::completed, f), boost::bind(&FutureResponse::received, f, _1));    
    } else {
        l3.send(method, boost::bind(&FutureResponse::completed, f));    
    }
    if (sync) {
        flush();
        f->waitForCompletion();
    }
    return Response(f);
}

Response SessionCore::send(const AMQMethodBody& method, const MethodContent& content, bool expectResponse)
{
    //TODO: lots of duplication between these two send methods; refactor
    boost::shared_ptr<FutureResponse> f(futures.createResponse());
    if (expectResponse) {
        l3.sendContent(method, dynamic_cast<const BasicHeaderProperties&>(content.getMethodHeaders()), content.getData(), 
                       boost::bind(&FutureResponse::completed, f), boost::bind(&FutureResponse::received, f, _1));    
    } else {
        l3.sendContent(method, dynamic_cast<const BasicHeaderProperties&>(content.getMethodHeaders()), content.getData(), 
                       boost::bind(&FutureResponse::completed, f));    
    }
    if (sync) {
        flush();
        f->waitForCompletion();
    }
    return Response(f);
}

FrameSet::shared_ptr SessionCore::get()
{
    return l3.received.pop();
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
    l3.received.close();
}

void SessionCore::stop()
{
    l3.received.close();
}

void SessionCore::handle(AMQFrame& frame)
{
    l2.incoming(frame);
}

void SessionCore::closed(uint16_t code, const std::string& text)
{
    l3.received.close();
    futures.close(code, text);
}
