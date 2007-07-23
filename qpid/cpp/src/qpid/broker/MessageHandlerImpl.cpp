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
#include "ConsumeAdapter.h"
#include "GetAdapter.h"
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
    client.ok();
}

void
MessageHandlerImpl::open(const string& reference)
{
    references.open(reference);
    client.ok();
}

void
MessageHandlerImpl::append(const framing::MethodContext& context)
{
    MessageAppendBody::shared_ptr body(boost::shared_polymorphic_downcast<MessageAppendBody>(context.methodBody));
    references.get(body->getReference())->append(body);
    client.ok();
}

void
MessageHandlerImpl::close(const string& reference)
{
	Reference::shared_ptr ref = references.get(reference);
    client.ok();
    
    // Send any transfer messages to their correct exchanges and okay them
    const Reference::Messages& msgs = ref->getMessages();
    for (Reference::Messages::const_iterator m = msgs.begin(); m != msgs.end(); ++m) {
        channel.handleInlineTransfer(*m);
    	client.setResponseTo((*m)->getRequestId());
    	client.ok();
    }
    ref->close();
}

void
MessageHandlerImpl::checkpoint(const string& /*reference*/,
                               const string& /*identifier*/ )
{
    // Initial implementation (which is conforming) is to do nothing here
    // and return offset zero for the resume
    client.ok();
}

void
MessageHandlerImpl::resume(const string& reference,
                           const string& /*identifier*/ )
{
    // Initial (null) implementation
    // open reference and return 0 offset
    references.open(reference);
    client.offset(0);
}

void
MessageHandlerImpl::offset(uint64_t /*value*/ )
{
    // Shouldn't ever receive this as it is reponse to resume
    // which is never sent
    // TODO astitcher 2007-02-16 What is the correct exception to throw here?    
    THROW_QPID_ERROR(INTERNAL_ERROR, "impossible");
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
    channel.consume(std::auto_ptr<DeliveryAdapter>(new ConsumeAdapter(adapter, destination, connection.getFrameMax())),
        tag, queue, !noAck, exclusive,
        noLocal ? &connection : 0, &filter);
    client.ok();
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
    
    GetAdapter out(adapter, queue, destination, connection.getFrameMax());
    if(channel.get(out, queue, !noAck))
        client.ok();
    else 
        client.empty();
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
    client.ok();
}

void
MessageHandlerImpl::recover(bool requeue)
{
    channel.recover(requeue);
    client.ok();
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
         client.ok();
    } else { 
        Reference::shared_ptr ref(references.get(transfer->getBody().getValue()));
        MessageMessage::shared_ptr message(new MessageMessage(&connection, requestId, transfer, ref));
        ref->addMessage(message);
    }
}


}} // namespace qpid::broker
