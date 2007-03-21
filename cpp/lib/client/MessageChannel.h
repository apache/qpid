#ifndef _client_MessageChannel_h
#define _client_MessageChannel_h

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

#include "shared_ptr.h"
#include "sys/Runnable.h"
#include "AckMode.h"

namespace qpid {

namespace framing {
class AMQMethodBody;
class AMQHeaderBody;
class AMQContentBody;
class FieldTable;
}

namespace client {

class Channel;
class Message;
class Queue;
class Exchange;
class MessageListener;
class ReturnedMessageHandler;

/**
 * Abstract interface for messaging implementation for a channel.
 * 
 *@see Channel for documentation.
  */
class MessageChannel : public sys::Runnable
{
  public:
    /**@see Channel::consume */
    virtual void consume(
        Queue& queue, std::string& tag, MessageListener* listener, 
        AckMode ackMode = NO_ACK, bool noLocal = false, bool synch = true,
        const framing::FieldTable* fields = 0) = 0;
        
    /**@see Channel::cancel */
    virtual void cancel(const std::string& tag, bool synch = true) = 0;

    /**@see Channel::get */
    virtual bool get(
        Message& msg, const Queue& queue, AckMode ackMode = NO_ACK) = 0;

    /**@see Channel::get */
    virtual void publish(const Message& msg, const Exchange& exchange,
                 const std::string& routingKey, 
                 bool mandatory = false, bool immediate = false) = 0;

    /**@see Channel::setReturnedMessageHandler */
    virtual void setReturnedMessageHandler(
        ReturnedMessageHandler* handler) = 0;

    /** Handle an incoming method. */
    virtual void handle(shared_ptr<framing::AMQMethodBody>) = 0;

    /** Handle an incoming header */
    virtual void handle(shared_ptr<framing::AMQHeaderBody>) = 0;

    /** Handle an incoming content */
    virtual void handle(shared_ptr<framing::AMQContentBody>) = 0;
    
    /** Send channel's QOS settings */
    virtual void setQos() = 0;

    /** Channel is closing */
    virtual void close() = 0;
};

}} // namespace qpid::client



#endif  /*!_client_MessageChannel_h*/
