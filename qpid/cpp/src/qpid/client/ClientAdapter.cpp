/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/framing/AMQP_ClientOperations.h"
#include "ClientAdapter.h"
#include "Connection.h"
#include "qpid/Exception.h"
#include "qpid/framing/AMQMethodBody.h"

namespace qpid {
namespace client {

using namespace qpid;
using namespace qpid::framing;

typedef std::vector<Queue::shared_ptr>::iterator queue_iterator;

void ClientAdapter::handleMethodInContext(
    boost::shared_ptr<qpid::framing::AMQMethodBody> method,
    const MethodContext& context
)
{
    try{
        method->invoke(*clientOps, context);
    }catch(ChannelException& e){
        connection.client->getChannel().close(
            context, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
        connection.closeChannel(getId());
    }catch(ConnectionException& e){
        connection.client->getConnection().close(
            context, e.code, e.toString(),
            method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        connection.client->getConnection().close(
            context, 541/*internal error*/, e.what(),
            method->amqpClassId(), method->amqpMethodId());
    }
}

void ClientAdapter::handleHeader(AMQHeaderBody::shared_ptr body) {
    channel->handleHeader(body);
}

void ClientAdapter::handleContent(AMQContentBody::shared_ptr body) {
    channel->handleContent(body);
}

void ClientAdapter::handleHeartbeat(AMQHeartbeatBody::shared_ptr) {
    // TODO aconway 2007-01-17: Implement heartbeats.
}



}} // namespace qpid::client

