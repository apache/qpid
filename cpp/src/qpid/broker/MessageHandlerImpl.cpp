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

#include <boost/format.hpp>
#include <boost/cast.hpp>

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
MessageHandlerImpl::append(const framing::AMQMethodBody& )
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
    channel.consume(MessageMessage::getToken(destination), tag, queue, noLocal, !noAck, exclusive, &filter);
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
    throw ConnectionException(540, "Message.Ok no longer supported");    
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
MessageHandlerImpl::transfer(const framing::AMQMethodBody& context)
{
    const MessageTransferBody* transfer = boost::polymorphic_downcast<const MessageTransferBody*>(&context);
    if (transfer->getBody().isInline()) {
        MessageMessage::shared_ptr message(new MessageMessage(&connection, transfer));
        channel.handleInlineTransfer(message);
    } else { 
        throw ConnectionException(540, "References no longer supported");
    }
}



void MessageHandlerImpl::flow(const std::string& destination, u_int8_t unit, u_int32_t value)
{
    
    if (unit == 0) {
        //message
        channel.addMessageCredit(destination, value);
    } else if (unit == 1) {
        //bytes
        channel.addByteCredit(destination, value);
    } else {
        //unknown
        throw ConnectionException(502, boost::format("Invalid value for unit %1%") % unit);
    }
    
}
    
void MessageHandlerImpl::flowMode(const std::string& destination, u_int8_t mode)
{
    if (mode == 0) {
        //credit
        channel.setCreditMode(destination);
    } else if (mode == 1) {
        //window
        channel.setWindowMode(destination);
    } else{
        throw ConnectionException(502, boost::format("Invalid value for mode %1%") % mode);        
    }
}
    
void MessageHandlerImpl::flush(const std::string& destination)
{
    channel.flush(destination);        
}

void MessageHandlerImpl::stop(const std::string& destination)
{
    channel.stop(destination);        
}


}} // namespace qpid::broker
