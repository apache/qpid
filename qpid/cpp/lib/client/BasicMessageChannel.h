#ifndef _client_BasicMessageChannel_h
#define _client_BasicMessageChannel_h

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

#include "MessageChannel.h"
#include "IncomingMessage.h"
#include <boost/scoped_ptr.hpp>

namespace qpid {
namespace client {
/**
 * Messaging implementation using AMQP 0-8 BasicMessageChannel class
 * to send and receiving messages.
 */
class BasicMessageChannel : public MessageChannel
{
  public:
    BasicMessageChannel(Channel& parent);
    
    void consume(
        Queue& queue, std::string& tag, MessageListener* listener, 
        AckMode ackMode = NO_ACK, bool noLocal = false, bool synch = true,
        const framing::FieldTable* fields = 0);
        
    void cancel(const std::string& tag, bool synch = true);

    bool get(Message& msg, const Queue& queue, AckMode ackMode = NO_ACK);

    void publish(const Message& msg, const Exchange& exchange,
                 const std::string& routingKey, 
                 bool mandatory = false, bool immediate = false);

    void setReturnedMessageHandler(ReturnedMessageHandler* handler);

    void run();

    void handle(boost::shared_ptr<framing::AMQMethodBody>);

    void handle(shared_ptr<framing::AMQHeaderBody>);

    void handle(shared_ptr<framing::AMQContentBody>);
    
    void setQos();
    
    void close();

  private:

    class WaitableDestination;
    struct Consumer{
        MessageListener* listener;
        AckMode ackMode;
        int count;
        u_int64_t lastDeliveryTag;
    };
    typedef std::map<std::string, Consumer> ConsumerMap;

    void deliver(Consumer& consumer, Message& msg);
    
    sys::Mutex lock;
    Channel& channel;
    IncomingMessage incoming;
    uint64_t incoming_size;
    ConsumerMap consumers ;
    ReturnedMessageHandler* returnsHandler;
    boost::scoped_ptr<WaitableDestination> destGet;
    boost::scoped_ptr<WaitableDestination> destDispatch;
};

}} // namespace qpid::client



#endif  /*!_client_BasicMessageChannel_h*/
