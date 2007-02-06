#ifndef _broker_BrokerMessageBase_h
#define _broker_BrokerMessageBase_h

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

#include "AMQContentBody.h"
#include "AMQHeaderBody.h"
#include "AMQMethodBody.h"
#include "Content.h"
#include "framing/amqp_types.h"

#include <string>
#include <boost/shared_ptr.hpp>

namespace qpid {
	
namespace framing {
class MethodContext;
class ChannelAdapter;
class BasicHeaderProperties;
class FieldTable;
}
	
namespace broker {

class MessageStore;
class ConnectionToken;

/**
 * Base class for all types of internal broker messages
 * abstracting away the operations
 * TODO; AMS: for the moment this is mostly a placeholder
 */
class Message{
    std::string exchange;
    std::string routingKey;
    const bool mandatory;
    const bool immediate;
    u_int64_t persistenceId;
    bool redelivered;
    framing::AMQMethodBody::shared_ptr respondTo;

  public:
    typedef boost::shared_ptr<Message> shared_ptr;

    Message(const std::string& _exchange, const std::string& _routingKey, 
            bool _mandatory, bool _immediate,
            framing::AMQMethodBody::shared_ptr respondTo_) :
        exchange(_exchange),
        routingKey(_routingKey),
        mandatory(_mandatory),
        immediate(_immediate),
        persistenceId(0),
        redelivered(false),
        respondTo(respondTo_)
    {}
            
    Message() :
        mandatory(false),
        immediate(false),
        persistenceId(0),
        redelivered(false)
    {}

    virtual ~Message() {};
            
    // Accessors
    const std::string& getRoutingKey() const { return routingKey; }
    const std::string& getExchange() const { return exchange; }
    u_int64_t getPersistenceId() const { return persistenceId; }
    bool getRedelivered() const { return redelivered; }
    framing::AMQMethodBody::shared_ptr getRespondTo() const {
        return respondTo;
    }
    
    void setRouting(const std::string& _exchange, const std::string& _routingKey)
    { exchange = _exchange; routingKey = _routingKey; } 
    void setPersistenceId(u_int64_t _persistenceId) { persistenceId = _persistenceId; } // XXXX: Only used in tests?
    void redeliver() { redelivered = true; }

    /**
     * Used to deliver the message from the queue
     */
    virtual void deliver(framing::ChannelAdapter& channel,
                         const std::string& consumerTag, 
                         u_int64_t deliveryTag, 
                         u_int32_t framesize) = 0;
    /**
     * Used to return a message in response to a get from a queue
     */
    virtual void sendGetOk(const framing::MethodContext& context,
                           u_int32_t messageCount,
                           u_int64_t deliveryTag, 
                           u_int32_t framesize) = 0;
            
    virtual bool isComplete() = 0;
            
    virtual u_int64_t contentSize() const = 0;
    // FIXME aconway 2007-02-06: Get rid of BasicHeaderProperties
    // at this level. Expose only generic properties available from both
    // message types (e.g. getApplicationHeaders below).
    // 
    virtual framing::BasicHeaderProperties* getHeaderProperties() = 0;
    virtual const framing::FieldTable& getApplicationHeaders() = 0;
    virtual bool isPersistent() = 0;
    virtual const ConnectionToken* const getPublisher() = 0;

    virtual void encode(framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?
    virtual void encodeHeader(framing::Buffer& /*buffer*/) {}; // XXXX: Only used in tests?

    /**
     * @returns the size of the buffer needed to encode this
     * message in its entirety
     * 
     * XXXX: Only used in tests?
     */
    virtual u_int32_t encodedSize() = 0;
    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     * 
     * XXXX: Only used in tests?
     */
    virtual u_int32_t encodedHeaderSize() = 0;
    /**
     * @returns the size of the buffer needed to encode the
     * (possibly partial) content held by this message
     */
    virtual u_int32_t encodedContentSize() = 0;
    /**
     * If headers have been received, returns the expected
     * content size else returns 0.
     */
    virtual u_int64_t expectedContentSize() = 0;
            
    // TODO: AMS 29/1/2007 Don't think these are really part of base class
            
    /**
     * Sets the 'content' implementation of this message (the
     * message controls the lifecycle of the content instance
     * it uses).
     */
    virtual void setContent(std::auto_ptr<Content>& /*content*/) {};
    virtual void setHeader(framing::AMQHeaderBody::shared_ptr /*header*/) {};
    virtual void addContent(framing::AMQContentBody::shared_ptr /*data*/) {};
    /**
     * Releases the in-memory content data held by this
     * message. Must pass in a store from which the data can
     * be reloaded.
     */
    virtual void releaseContent(MessageStore* /*store*/) {};
};

}}


#endif  /*!_broker_BrokerMessage_h*/
