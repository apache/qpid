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

#include "qpid/QpidError.h"
#include "MessageHandlerImpl.h"
#include "BrokerChannel.h"
#include "qpid/framing/FramingContent.h"
#include "Connection.h"
#include "Broker.h"
#include "BrokerMessageMessage.h"
#include "qpid/framing/MessageAppendBody.h"
#include "qpid/framing/MessageTransferBody.h"
#include "BrokerAdapter.h"

namespace qpid {
namespace broker {

using namespace framing;

MessageHandlerImpl::MessageHandlerImpl(CoreRefs& parent)
    : HandlerImplType(parent) {}

//
// Message class method handlers
//

void
MessageHandlerImpl::cancel(const string& destination )
{
    channel.cancel(destination);
}

void
MessageHandlerImpl::open(const string& /*reference*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::append(const framing::MethodContext& /*context*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::close(const string& /*reference*/)
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::checkpoint(const string& /*reference*/,
                               const string& /*identifier*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::resume(const string& /*reference*/,
                           const string& /*identifier*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::offset(uint64_t /*value*/ )
{
    throw ConnectionException(540, "References no longer supported");
}

void
MessageHandlerImpl::consume(uint16_t /*ticket*/,
                            const string& queueName,
                            const string& destination,
                            bool noLocal,
                            bool noAck,
                            bool exclusive,
                            const framing::FieldTable& filter )
{
    Queue::shared_ptr queue = getQueue(queueName);
    if(!destination.empty() && channel.exists(destination))
        throw ConnectionException(530, "Consumer tags must be unique");
    string tag = destination;
    channel.consume(MessageMessage::getToken(destination), tag, queue, !noAck, exclusive, noLocal ? &connection : 0, &filter);
    // Dispatch messages as there is now a consumer.
    queue->requestDispatch();
}


void
MessageHandlerImpl::get(uint16_t /*ticket*/,
                         const string& queueName,
                         const string& destination,
                         bool noAck )
{
    Queue::shared_ptr queue = getQueue(queueName);
    
    if (channel.get(MessageMessage::getToken(destination), queue, !noAck)){
        //don't send any response... rely on execution completion
    } else {
        //temporarily disabled:
        //client.empty();
    }
}

void
MessageHandlerImpl::empty()
{
    // Shouldn't ever receive this as it is a response to get
    // which is never sent
    // TODO astitcher 2007-02-09 What is the correct exception to throw here?
    THROW_QPID_ERROR(INTERNAL_ERROR, "Impossible");
}

void
MessageHandlerImpl::ok()
{
    channel.ack(adapter.getFirstAckRequest(), adapter.getLastAckRequest());
}

void
MessageHandlerImpl::qos(uint32_t prefetchSize,
                        uint16_t prefetchCount,
                        bool /*global*/ )
{
    //TODO: handle global
    channel.setPrefetchSize(prefetchSize);
    channel.setPrefetchCount(prefetchCount);
}

void
MessageHandlerImpl::recover(bool requeue)
{
    channel.recover(requeue);
}

void
MessageHandlerImpl::reject(uint16_t /*code*/, const string& /*text*/ )
{
}

void
MessageHandlerImpl::transfer(const framing::MethodContext& context)
{
    MessageTransferBody::shared_ptr transfer(
        boost::shared_polymorphic_downcast<MessageTransferBody>(
            context.methodBody));
    RequestId requestId = context.getRequestId();
    
    if (transfer->getBody().isInline()) {
        MessageMessage::shared_ptr message(new MessageMessage(&connection, requestId, transfer));
        channel.handleInlineTransfer(message);
    } else { 
        throw ConnectionException(540, "References no longer supported");
    }
}


}} // namespace qpid::broker
