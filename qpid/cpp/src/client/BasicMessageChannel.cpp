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
#include "BasicMessageChannel.h"
#include "../framing/AMQMethodBody.h"
#include "ClientChannel.h"
#include "ReturnedMessageHandler.h"
#include "MessageListener.h"
#include "../framing/FieldTable.h"
#include "Connection.h"
#include <queue>
#include <iostream>
#include <boost/format.hpp>
#include <boost/variant.hpp>

namespace qpid {
namespace client {

using namespace std;
using namespace sys;
using namespace framing;
using boost::format;

namespace {

// Destination name constants
const std::string BASIC_GET("__basic_get__");
const std::string BASIC_RETURN("__basic_return__");

// Reference name constant
const std::string BASIC_REF("__basic_reference__");
}
    
BasicMessageChannel::BasicMessageChannel(Channel& ch)
    : channel(ch), returnsHandler(0)
{
    incoming.addDestination(BASIC_RETURN, destDispatch);
}

void BasicMessageChannel::consume(
    Queue& queue, std::string& tag, MessageListener* listener, 
    AckMode ackMode, bool noLocal, bool synch, const FieldTable* fields)
{
    {
        // Note we create a consumer even if tag="". In that case
        // It will be renamed when we handle BasicConsumeOkBody.
        // 
        Mutex::ScopedLock l(lock);
        ConsumerMap::iterator i = consumers.find(tag);
        if (i != consumers.end())
            THROW_QPID_ERROR(CLIENT_ERROR,
                             "Consumer already exists with tag="+tag);
        Consumer& c = consumers[tag];
        c.listener = listener;
        c.ackMode = ackMode;
        c.lastDeliveryTag = 0;
    }

    // FIXME aconway 2007-03-23: get processed in both.
    
    // BasicConsumeOkBody is really processed in handle(), here
    // we just pick up the tag to return to the user.
    //
    // We can't process it here because messages for the consumer may
    // already be arriving.
    //
    BasicConsumeOkBody::shared_ptr ok =
        channel.sendAndReceiveSync<BasicConsumeOkBody>(
            synch,
            new BasicConsumeBody(
                channel.version, 0, queue.getName(), tag, noLocal,
                ackMode == NO_ACK, false, !synch,
                fields ? *fields : FieldTable()));
    tag = ok->getConsumerTag();
}


void BasicMessageChannel::cancel(const std::string& tag, bool synch) {
    Consumer c;
    {
        Mutex::ScopedLock l(lock);
        ConsumerMap::iterator i = consumers.find(tag);
        if (i == consumers.end())
            return;
        c = i->second;
        consumers.erase(i);
    }
    if(c.ackMode == LAZY_ACK && c.lastDeliveryTag > 0) 
        channel.send(new BasicAckBody(channel.version, c.lastDeliveryTag, true));
    channel.sendAndReceiveSync<BasicCancelOkBody>(
        synch, new BasicCancelBody(channel.version, tag, !synch));
}

void BasicMessageChannel::close(){
    ConsumerMap consumersCopy;
    {
        Mutex::ScopedLock l(lock);
        consumersCopy = consumers;
        consumers.clear();
    }
    destGet.shutdown();
    destDispatch.shutdown();
    for (ConsumerMap::iterator i=consumersCopy.begin();
         i  != consumersCopy.end(); ++i)
    {
        Consumer& c = i->second;
        if ((c.ackMode == LAZY_ACK || c.ackMode == AUTO_ACK)
            && c.lastDeliveryTag > 0)
        {
            channel.send(new BasicAckBody(channel.version, c.lastDeliveryTag, true));
        }
    }
}


bool BasicMessageChannel::get(
    Message& msg, const Queue& queue, AckMode ackMode)
{
    // Prepare for incoming response
    incoming.addDestination(BASIC_GET, destGet);
    channel.send(
        new BasicGetBody(channel.version, 0, queue.getName(), ackMode));
    bool got = destGet.wait(msg);
    return got;
}

void BasicMessageChannel::publish(
    const Message& msg, const Exchange& exchange,
    const std::string& routingKey, bool mandatory, bool immediate)
{
    const string e = exchange.getName();
    string key = routingKey;

    // Make a header for the message
    AMQHeaderBody::shared_ptr header(new AMQHeaderBody(BASIC));
    BasicHeaderProperties::copy(
        *static_cast<BasicHeaderProperties*>(header->getProperties()), msg);
    header->setContentSize(msg.getData().size());

    channel.send(
        new BasicPublishBody(
            channel.version, 0, e, key, mandatory, immediate));
    channel.send(header);
    string data = msg.getData();
    u_int64_t data_length = data.length();
    if(data_length > 0){
        //frame itself uses 8 bytes
        u_int32_t frag_size = channel.connection->getMaxFrameSize() - 8;
        if(data_length < frag_size){
            channel.send(new AMQContentBody(data));
        }else{
            u_int32_t offset = 0;
            u_int32_t remaining = data_length - offset;
            while (remaining > 0) {
                u_int32_t length = remaining > frag_size ? frag_size : remaining;
                string frag(data.substr(offset, length));
                channel.send(new AMQContentBody(frag));                          
                
                offset += length;
                remaining = data_length - offset;
            }
        }
    }
}

void BasicMessageChannel::handle(boost::shared_ptr<AMQMethodBody> method) {
    assert(method->amqpClassId() ==BasicGetBody::CLASS_ID);
    switch(method->amqpMethodId()) {
      case BasicGetOkBody::METHOD_ID: {
          incoming.openReference(BASIC_REF);
          incoming.createMessage(BASIC_GET, BASIC_REF);
          return;
      }
      case BasicGetEmptyBody::METHOD_ID: {
          incoming.getDestination(BASIC_GET).empty();
          incoming.removeDestination(BASIC_GET);
          return;
      }
      case BasicDeliverBody::METHOD_ID: {
          BasicDeliverBody::shared_ptr deliver=
              boost::shared_polymorphic_downcast<BasicDeliverBody>(method);
          incoming.openReference(BASIC_REF);
          Message& msg = incoming.createMessage(
              deliver->getConsumerTag(), BASIC_REF);
          msg.setDestination(deliver->getConsumerTag());
          msg.setDeliveryTag(deliver->getDeliveryTag());
          msg.setRedelivered(deliver->getRedelivered());
          return;
      }
      case BasicReturnBody::METHOD_ID: {
          incoming.openReference(BASIC_REF);
          incoming.createMessage(BASIC_RETURN, BASIC_REF);
          return;
      }
      case BasicConsumeOkBody::METHOD_ID: {
          Mutex::ScopedLock l(lock);
          BasicConsumeOkBody::shared_ptr consumeOk =
              boost::shared_polymorphic_downcast<BasicConsumeOkBody>(method);
          std::string tag = consumeOk->getConsumerTag();
          ConsumerMap::iterator i = consumers.find(std::string());
          if (i != consumers.end()) {
              // Need to rename the un-named consumer.
              if (consumers.find(tag) == consumers.end()) {
                  consumers[tag] = i->second;
                  consumers.erase(i);
              }
              else              // Tag already exists.
                  throw ChannelException(404, "Tag already exists: "+tag);
          }
          // FIXME aconway 2007-03-23: Integrate consumer & destination
          // maps.
          incoming.addDestination(tag, destDispatch);
          return;
      }
    }
    throw Channel::UnknownMethod();
}

void BasicMessageChannel::handle(AMQHeaderBody::shared_ptr header) {
    BasicHeaderProperties* props = 
        boost::polymorphic_downcast<BasicHeaderProperties*>(
            header->getProperties());
    IncomingMessage::Reference& ref = incoming.getReference(BASIC_REF);
    assert (ref.messages.size() == 1);
    ref.messages.front().BasicHeaderProperties::operator=(*props);
    incoming_size = header->getContentSize();
    if (incoming_size==0)
        incoming.closeReference(BASIC_REF);
}
    
void BasicMessageChannel::handle(AMQContentBody::shared_ptr content){
    incoming.appendReference(BASIC_REF, content->getData());
    size_t size = incoming.getReference(BASIC_REF).data.size();
    if (size >= incoming_size) {
        incoming.closeReference(BASIC_REF);
        if (size > incoming_size)
            throw ChannelException(502, "Content exceeded declared size");
    }
}

void BasicMessageChannel::deliver(Consumer& consumer, Message& msg){
    //record delivery tag:
    consumer.lastDeliveryTag = msg.getDeliveryTag();

    //allow registered listener to handle the message
    consumer.listener->received(msg);

    if(channel.isOpen()){
        bool multiple(false);
        switch(consumer.ackMode){
          case LAZY_ACK: 
            multiple = true;
            if(++(consumer.count) < channel.getPrefetch())
                break;
            //else drop-through
          case AUTO_ACK:
            consumer.lastDeliveryTag = 0;
            channel.send(
                new BasicAckBody(
                    channel.version,
                    msg.getDeliveryTag(),
                    multiple));
          case NO_ACK:          // Nothing to do
          case CLIENT_ACK:      // User code must ack.
            break;
            // TODO aconway 2007-02-22: Provide a way for user
            // to ack!
        }
    }

    //as it stands, transactionality is entirely orthogonal to ack
    //mode, though the acks will not be processed by the broker under
    //a transaction until it commits.
}


void BasicMessageChannel::run() {
    while(channel.isOpen()) {
        try {
            Message msg;
            bool gotMessge = destDispatch.wait(msg);
            if (gotMessge) {
                if(msg.getDestination() == BASIC_RETURN) {
                    ReturnedMessageHandler* handler=0;
                    {
                        Mutex::ScopedLock l(lock);
                        handler=returnsHandler;
                    }
                    if(handler != 0) 
                        handler->returned(msg);
                }
                else {
                    Consumer consumer;
                    {
                        Mutex::ScopedLock l(lock);
                        ConsumerMap::iterator i = consumers.find(
                            msg.getDestination());
                        if(i == consumers.end()) 
                            THROW_QPID_ERROR(PROTOCOL_ERROR+504,
                                             "Unknown consumer tag=" +
                                             msg.getDestination());
                        consumer = i->second;
                    }
                    deliver(consumer, msg);
                }
            }
        }
        catch (const ShutdownException&) {
            /* Orderly shutdown */
        }
        catch (const Exception& e) {
            // FIXME aconway 2007-02-20: Report exception to user.
            cout << "client::BasicMessageChannel::run() terminated by: "
                 << e.toString() << endl;
        }
    }
}

void BasicMessageChannel::setReturnedMessageHandler(ReturnedMessageHandler* handler){
    Mutex::ScopedLock l(lock);
    returnsHandler = handler;
}

void BasicMessageChannel::setQos(){
    channel.sendAndReceive<BasicQosOkBody>(
        new BasicQosBody(channel.version, 0, channel.getPrefetch(), false));
    if(channel.isTransactional())
        channel.sendAndReceive<TxSelectOkBody>(new TxSelectBody(channel.version));
}

}} // namespace qpid::client
