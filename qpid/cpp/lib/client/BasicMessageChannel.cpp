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
#include "BasicMessageChannel.h"
#include "AMQMethodBody.h"
#include "ClientChannel.h"
#include "ReturnedMessageHandler.h"
#include "MessageListener.h"
#include "framing/FieldTable.h"
#include "Connection.h"

using namespace std;

namespace qpid {
namespace client {

using namespace sys;
using namespace framing;

BasicMessageChannel::BasicMessageChannel(Channel& ch)
    : channel(ch), returnsHandler(0) {}

void BasicMessageChannel::consume(
    Queue& queue, std::string& tag, MessageListener* listener, 
    AckMode ackMode, bool noLocal, bool synch, const FieldTable* fields)
{
    channel.sendAndReceiveSync<BasicConsumeOkBody>(
        synch,
        new BasicConsumeBody(
            channel.version, 0, queue.getName(), tag, noLocal,
            ackMode == NO_ACK, false, !synch,
            fields ? *fields : FieldTable()));
    if (synch) {
        BasicConsumeOkBody::shared_ptr response =
            boost::shared_polymorphic_downcast<BasicConsumeOkBody>(
                channel.responses.getResponse());
        tag = response->getConsumerTag();
    }
    // FIXME aconway 2007-02-20: Race condition!
    // We could receive the first message for the consumer
    // before we create the consumer below.
    // Move consumer creation to handler for BasicConsumeOkBody
    {
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
    incoming.shutdown();
}



bool BasicMessageChannel::get(Message& msg, const Queue& queue, AckMode ackMode) {
    // Expect a message starting with a BasicGetOk
    incoming.startGet();
    channel.send(new BasicGetBody(channel.version, 0, queue.getName(), ackMode));
    return incoming.waitGet(msg);
}

void BasicMessageChannel::publish(
    const Message& msg, const Exchange& exchange,
    const std::string& routingKey, bool mandatory, bool immediate)
{
    msg.getHeader()->setContentSize(msg.getData().size());
    const string e = exchange.getName();
    string key = routingKey;
    channel.send(new BasicPublishBody(channel.version, 0, e, key, mandatory, immediate));
    //break msg up into header frame and content frame(s) and send these
    channel.send(msg.getHeader());
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
      case BasicDeliverBody::METHOD_ID:
      case BasicReturnBody::METHOD_ID:
      case BasicGetOkBody::METHOD_ID:
      case BasicGetEmptyBody::METHOD_ID:
        incoming.add(method);   
        return;
    }
    throw Channel::UnknownMethod();
}

void BasicMessageChannel::handle(AMQHeaderBody::shared_ptr body){
    incoming.add(body);
}
    
void BasicMessageChannel::handle(AMQContentBody::shared_ptr body){
    incoming.add(body);
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
                    channel.version, msg.getDeliveryTag(), multiple));
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
            Message msg = incoming.waitDispatch();
            if(msg.getMethod()->isA<BasicReturnBody>()) {
                ReturnedMessageHandler* handler=0;
                {
                    Mutex::ScopedLock l(lock);
                    handler=returnsHandler;
                }
                if(handler == 0) {
                    // TODO aconway 2007-02-20: proper logging.
                    cout << "Message returned: " << msg.getData() << endl;
                }
                else 
                    handler->returned(msg);
            }
            else {
                BasicDeliverBody::shared_ptr deliverBody =
                    boost::shared_polymorphic_downcast<BasicDeliverBody>(
                        msg.getMethod());
                std::string tag = deliverBody->getConsumerTag();
                Consumer consumer;
                {
                    Mutex::ScopedLock l(lock);
                    ConsumerMap::iterator i = consumers.find(tag);
                    if(i == consumers.end()) 
                        THROW_QPID_ERROR(PROTOCOL_ERROR+504,
                                         "Unknown consumer tag=" + tag);
                    consumer = i->second;
                }
                deliver(consumer, msg);
            }
        }
        catch (const ShutdownException&) {
            /* Orderly shutdown */
        }
        catch (const Exception& e) {
            // FIXME aconway 2007-02-20: Report exception to user.
            cout << "client::Basic::run() terminated by: " << e.toString()
                 << "(" << typeid(e).name() << ")" << endl;
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
