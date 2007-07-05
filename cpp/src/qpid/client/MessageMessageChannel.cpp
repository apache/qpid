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
#include <iostream>
#include <boost/format.hpp>
#include "MessageMessageChannel.h"
#include "qpid/framing/AMQMethodBody.h"
#include "ClientChannel.h"
#include "ReturnedMessageHandler.h"
#include "MessageListener.h"
#include "qpid/framing/FieldTable.h"
#include "Connection.h"
#include "qpid/shared_ptr.h"
#include <boost/bind.hpp>

namespace qpid {
namespace client {

using namespace std;
using namespace sys;
using namespace framing;

MessageMessageChannel::MessageMessageChannel(Channel& ch)
    : channel(ch), tagCount(0) {}

string MessageMessageChannel::newTag() {
    Mutex::ScopedLock l(lock);
    return (boost::format("__tag%d")%++tagCount).str();
}

void MessageMessageChannel::consume(
    Queue& queue, std::string& tag, MessageListener* /*listener*/, 
    AckMode ackMode, bool noLocal, bool /*synch*/, const FieldTable* fields)
{
    if (tag.empty())
        tag = newTag();
    channel.sendAndReceive<MessageOkBody>(
        make_shared_ptr(new MessageConsumeBody(
            channel.getVersion(), 0, queue.getName(), tag, noLocal,
            ackMode == NO_ACK, false, fields ? *fields : FieldTable())));
    
//     // FIXME aconway 2007-02-20: Race condition!
//     // We could receive the first message for the consumer
//     // before we create the consumer below.
//     // Move consumer creation to handler for MessageConsumeOkBody
//     {
//         Mutex::ScopedLock l(lock);
//         ConsumerMap::iterator i = consumers.find(tag);
//         if (i != consumers.end())
//             THROW_QPID_ERROR(CLIENT_ERROR,
//                              "Consumer already exists with tag="+tag);
//         Consumer& c = consumers[tag];
//         c.listener = listener;
//         c.ackMode = ackMode;
//         c.lastDeliveryTag = 0;
//     }
}


void MessageMessageChannel::cancel(const std::string& /*tag*/, bool /*synch*/) {
    // FIXME aconway 2007-02-23: 
//     Consumer c;
//     {
//         Mutex::ScopedLock l(lock);
//         ConsumerMap::iterator i = consumers.find(tag);
//         if (i == consumers.end())
//             return;
//         c = i->second;
//         consumers.erase(i);
//     }
//     if(c.ackMode == LAZY_ACK && c.lastDeliveryTag > 0) 
//         channel.send(new MessageAckBody(channel.version, c.lastDeliveryTag, true));
//     channel.sendAndReceiveSync<MessageCancelOkBody>(
//         synch, new MessageCancelBody(channel.version, tag, !synch));
}

void MessageMessageChannel::close(){
    // FIXME aconway 2007-02-23: 
//     ConsumerMap consumersCopy;
//     {
//         Mutex::ScopedLock l(lock);
//         consumersCopy = consumers;
//         consumers.clear();
//     }
//     for (ConsumerMap::iterator i=consumersCopy.begin();
//          i  != consumersCopy.end(); ++i)
//     {
//         Consumer& c = i->second;
//         if ((c.ackMode == LAZY_ACK || c.ackMode == AUTO_ACK)
//             && c.lastDeliveryTag > 0)
//         {
//             channel.send(new MessageAckBody(channel.version, c.lastDeliveryTag, true));
//         }
//     }
//     incoming.shutdown();
}

void MessageMessageChannel::cancelAll(){
}

/** Destination ID for the current get.
 * Must not clash with a generated consumer ID.
 * TODO aconway 2007-03-06: support multiple outstanding gets?
 */
const string getDestinationId("__get__");

/**
 * A destination that provides a Correlator::Action to handle
 * MessageEmpty responses.
 */
struct MessageGetDestination : public IncomingMessage::WaitableDestination
{
    void response(shared_ptr<AMQResponseBody> response) {
        if (response->amqpClassId() == MessageOkBody::CLASS_ID) {
            switch (response->amqpMethodId()) {
              case MessageOkBody::METHOD_ID:
                // Nothing to do, wait for transfer.
                return;
              case MessageEmptyBody::METHOD_ID:
                empty();        // Wake up waiter with empty queue.
                return;
            }
        }
        throw QPID_ERROR(PROTOCOL_ERROR, "Invalid response");
    }

    Correlator::Action action() {
        return boost::bind(&MessageGetDestination::response, this, _1);
    }
};

bool MessageMessageChannel::get(
    Message& msg, const Queue& queue, AckMode ackMode)
{
    Mutex::ScopedLock l(lock);
    std::string destName=newTag();
    MessageGetDestination dest;
    incoming.addDestination(destName, dest);
    channel.send(
        make_shared_ptr(
            new MessageGetBody(
                channel.version, 0, queue.getName(), destName, ackMode)),
        dest.action());
    return dest.wait(msg);
}


/** Convert a message to a transfer command. */
MessageTransferBody::shared_ptr makeTransfer(
    ProtocolVersion version,
    const Message& msg, const string& destination,
    const std::string& routingKey, bool mandatory, bool immediate)
{
    return MessageTransferBody::shared_ptr(
        new MessageTransferBody(
            version,
            0,                  // FIXME aconway 2007-04-03: ticket.
            destination,
            msg.isRedelivered(),
            immediate,
            0,                  // FIXME aconway 2007-02-23: ttl
            msg.getPriority(),
            msg.getTimestamp(),
            static_cast<uint8_t>(msg.getDeliveryMode()),
            0,                  // FIXME aconway 2007-04-03: Expiration
            string(),           // Exchange: for broker use only.
            routingKey,
            msg.getMessageId(),
            msg.getCorrelationId(),
            msg.getReplyTo(),
            msg.getContentType(),
            msg.getContentEncoding(),
            msg.getUserId(),
            msg.getAppId(),
            string(),       // FIXME aconway 2007-04-03: TransactionId
            string(),        //FIXME aconway 2007-04-03: SecurityToken
            msg.getHeaders(),
            Content(INLINE, msg.getData()),
            mandatory
        ));
}

// FIXME aconway 2007-04-05: Generated code should provide this.
/**
 * Calculate the size of a frame containing the given body type
 * if all variable-lengths parts are empty.
 */
template <class T> size_t overhead() {
    static AMQFrame frame(
        ProtocolVersion(), 0, make_shared_ptr(new T(ProtocolVersion())));
    return frame.size();
}

void MessageMessageChannel::publish(
    const Message& msg, const Exchange& exchange,
    const std::string& routingKey, bool mandatory, bool immediate)
{
    MessageTransferBody::shared_ptr transfer = makeTransfer(
        channel.getVersion(),
        msg, exchange.getName(), routingKey, mandatory, immediate);
    // Frame itself uses 8 bytes.
    u_int32_t frameMax = channel.connection->getMaxFrameSize() - 8;
    if (transfer->size()  <= frameMax) {
        channel.sendAndReceive<MessageOkBody>(transfer);
    }
    else {
        std::string ref = newTag();
        std::string data = transfer->getBody().getValue();
        size_t chunk =
            channel.connection->getMaxFrameSize() -
            (overhead<MessageAppendBody>() + ref.size());
        // TODO aconway 2007-04-05: cast around lack of generated setters
        const_cast<Content&>(transfer->getBody()) = Content(REFERENCE,ref);
        channel.send(
            make_shared_ptr(new MessageOpenBody(channel.version, ref)));
        channel.send(transfer);
        const char* p = data.data();
        const char* end = data.data()+data.size();
        while (p+chunk <= end) {
            channel.send(
                make_shared_ptr(
                    new MessageAppendBody(channel.version, ref, std::string(p, chunk))));
            p += chunk;
        }
        if (p < end) {
            channel.send(
                make_shared_ptr(
                    new MessageAppendBody(channel.version, ref, std::string(p, end-p))));
        }
        channel.send(make_shared_ptr(new MessageCloseBody(channel.version, ref)));
    }
}
        
void copy(Message& msg, MessageTransferBody& transfer) {
    // FIXME aconway 2007-04-05: Verify all required fields
    // are copied.
    msg.setContentType(transfer.getContentType());
    msg.setContentEncoding(transfer.getContentEncoding());
    msg.setHeaders(transfer.getApplicationHeaders());
    msg.setDeliveryMode(DeliveryMode(transfer.getDeliveryMode()));
    msg.setPriority(transfer.getPriority());
    msg.setCorrelationId(transfer.getCorrelationId());
    msg.setReplyTo(transfer.getReplyTo());
    // FIXME aconway 2007-04-05: TTL/Expiration
    msg.setMessageId(transfer.getMessageId());
    msg.setTimestamp(transfer.getTimestamp());
    msg.setUserId(transfer.getUserId());
    msg.setAppId(transfer.getAppId());
    msg.setDestination(transfer.getDestination());
    msg.setRedelivered(transfer.getRedelivered());
    msg.setDeliveryTag(0); // No meaning in 0-9
    if (transfer.getBody().isInline()) 
        msg.setData(transfer.getBody().getValue());
}

void MessageMessageChannel::handle(boost::shared_ptr<AMQMethodBody> method) {
    assert(method->amqpClassId() ==MessageTransferBody::CLASS_ID);
    switch(method->amqpMethodId()) {
      case MessageAppendBody::METHOD_ID: {
          MessageAppendBody::shared_ptr append =
              shared_polymorphic_downcast<MessageAppendBody>(method);
          incoming.appendReference(append->getReference(), append->getBytes());
          break;
      }
      case MessageOpenBody::METHOD_ID: {
          MessageOpenBody::shared_ptr open =
              shared_polymorphic_downcast<MessageOpenBody>(method);
          incoming.openReference(open->getReference());
          break;
      }

      case MessageCloseBody::METHOD_ID: {
          MessageCloseBody::shared_ptr close =
              shared_polymorphic_downcast<MessageCloseBody>(method);
          incoming.closeReference(close->getReference());
          break;
      }

      case MessageTransferBody::METHOD_ID: {
          MessageTransferBody::shared_ptr transfer=
              shared_polymorphic_downcast<MessageTransferBody>(method);
          if (transfer->getBody().isInline()) {
              Message msg;
              copy(msg, *transfer);
              // Deliver it.
              incoming.getDestination(transfer->getDestination()).message(msg);
          }
          else {
              Message& msg=incoming.createMessage(
                  transfer->getDestination(), transfer->getBody().getValue());
              copy(msg, *transfer);
              // Will be delivered when reference closes.
          }
          break;
      }

      case MessageEmptyBody::METHOD_ID: 
      case MessageOkBody::METHOD_ID:
        // Nothing to do
        break;

        // FIXME aconway 2007-04-03:  TODO
      case MessageCancelBody::METHOD_ID:
      case MessageCheckpointBody::METHOD_ID:
      case MessageOffsetBody::METHOD_ID:
      case MessageQosBody::METHOD_ID:
      case MessageRecoverBody::METHOD_ID:
      case MessageRejectBody::METHOD_ID:
      case MessageResumeBody::METHOD_ID:
        break;
      default:
        throw Channel::UnknownMethod();
    }
}

void MessageMessageChannel::handle(AMQHeaderBody::shared_ptr ){
    throw QPID_ERROR(INTERNAL_ERROR, "Basic protocol not supported");
}
    
void MessageMessageChannel::handle(AMQContentBody::shared_ptr ){
    throw QPID_ERROR(INTERNAL_ERROR, "Basic protocol not supported");
}

// FIXME aconway 2007-02-23: 
// void MessageMessageChannel::deliver(IncomingMessage::Destination& consumer, Message& msg){
//     //record delivery tag:
//     consumer.lastDeliveryTag = msg.getDeliveryTag();

//     //allow registered listener to handle the message
//     consumer.listener->received(msg);

//     if(channel.isOpen()){
//         bool multiple(false);
//         switch(consumer.ackMode){
//           case LAZY_ACK: 
//             multiple = true;
//             if(++(consumer.count) < channel.getPrefetch())
//                 break;
//             //else drop-through
//           case AUTO_ACK:
//             consumer.lastDeliveryTag = 0;
//             channel.send(
//                 new MessageAckBody(
//                     channel.version, msg.getDeliveryTag(), multiple));
//           case NO_ACK:          // Nothing to do
//           case CLIENT_ACK:      // User code must ack.
//             break;
//             // TODO aconway 2007-02-22: Provide a way for user
//             // to ack!
//         }
//     }

//     //as it stands, transactionality is entirely orthogonal to ack
//     //mode, though the acks will not be processed by the broker under
//     //a transaction until it commits.
// }


void MessageMessageChannel::run() {
    // FIXME aconway 2007-02-23: 
//     while(channel.isOpen()) {
//         try {
//             Message msg = incoming.waitDispatch();
//             if(msg.getMethod()->isA<MessageReturnBody>()) {
//                 ReturnedMessageHandler* handler=0;
//                 {
//                     Mutex::ScopedLock l(lock);
//                     handler=returnsHandler;
//                 }
//                 if(handler == 0) {
//                     // TODO aconway 2007-02-20: proper logging.
//                     QPID_LOG(warn, "No handler for message.");
//                 }
//                 else 
//                     handler->returned(msg);
//             }
//             else {
//                 MessageDeliverBody::shared_ptr deliverBody =
//                     boost::shared_polymorphic_downcast<MessageDeliverBody>(
//                         msg.getMethod());
//                 std::string tag = deliverBody->getConsumerTag();
//                 Consumer consumer;
//                 {
//                     Mutex::ScopedLock l(lock);
//                     ConsumerMap::iterator i = consumers.find(tag);
//                     if(i == consumers.end()) 
//                         THROW_QPID_ERROR(PROTOCOL_ERROR+504,
//                                          "Unknown consumer tag=" + tag);
//                     consumer = i->second;
//                 }
//                 deliver(consumer, msg);
//             }
//         }
//         catch (const ShutdownException&) {
//             /* Orderly shutdown */
//         }
//         catch (const Exception& e) {
//             QPID_LOG(error, e.what());
//         }
//     }
}

void MessageMessageChannel::setReturnedMessageHandler(
    ReturnedMessageHandler* )
{
    throw QPID_ERROR(INTERNAL_ERROR, "Message class does not support returns");
}

void MessageMessageChannel::setQos(){
    channel.sendAndReceive<MessageOkBody>(
        make_shared_ptr(new MessageQosBody(channel.version, 0, channel.getPrefetch(), false)));
    if(channel.isTransactional())
        channel.sendAndReceive<TxSelectOkBody>(
            make_shared_ptr(new TxSelectBody(channel.version)));
}

}} // namespace qpid::client
