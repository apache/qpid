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

#include <string>
#include <boost/shared_ptr.hpp>
#include "Content.h"
#include "PersistableMessage.h"
#include "qpid/framing/amqp_types.h"

namespace qpid {
	
namespace framing {
class MethodContext;
class ChannelAdapter;
class BasicHeaderProperties;
class FieldTable;
class AMQMethodBody;
class AMQContentBody;
class AMQHeaderBody;
}
	

namespace broker {
class ConnectionToken;
class MessageStore;

/**
 * Base class for all types of internal broker messages
 * abstracting away the operations
 * TODO; AMS: for the moment this is mostly a placeholder
 */
class Message : public PersistableMessage{
  public:
    typedef boost::shared_ptr<Message> shared_ptr;
    typedef boost::shared_ptr<framing::AMQMethodBody> AMQMethodBodyPtr;


    Message(const ConnectionToken* publisher_,
            const std::string& _exchange,
            const std::string& _routingKey, 
            bool _mandatory, bool _immediate,
            AMQMethodBodyPtr respondTo_) :
        publisher(publisher_),
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
    uint64_t getPersistenceId() const { return persistenceId; }
    bool getRedelivered() const { return redelivered; }
    AMQMethodBodyPtr getRespondTo() const { return respondTo; }
    
    void setRouting(const std::string& _exchange, const std::string& _routingKey)
    { exchange = _exchange; routingKey = _routingKey; } 
    void setPersistenceId(uint64_t _persistenceId) const { persistenceId = _persistenceId; }
    void redeliver() { redelivered = true; }

    /**
     * Used to deliver the message from the queue
     */
    virtual void deliver(framing::ChannelAdapter& channel,
                         const std::string& consumerTag, 
                         uint64_t deliveryTag, 
                         uint32_t framesize) = 0;
    /**
     * Used to return a message in response to a get from a queue
     */
    virtual void sendGetOk(const framing::MethodContext& context,
    					   const std::string& destination,
                           uint32_t messageCount,
                           uint64_t deliveryTag, 
                           uint32_t framesize) = 0;
            
    virtual bool isComplete() = 0;
            
    virtual uint64_t contentSize() const = 0;
    virtual framing::BasicHeaderProperties* getHeaderProperties() = 0;
    virtual const framing::FieldTable& getApplicationHeaders() = 0;
    virtual bool isPersistent() = 0;
    virtual const ConnectionToken* getPublisher() const {
        return publisher;
    }

    virtual void encode(framing::Buffer& buffer) const = 0;
    virtual void encodeHeader(framing::Buffer& buffer) const = 0;

    /**
     * @returns the size of the buffer needed to encode this
     * message in its entirety
     */
    virtual uint32_t encodedSize() const = 0;
    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     */
    virtual uint32_t encodedHeaderSize() const = 0;
    /**
     * @returns the size of the buffer needed to encode the
     * (possibly partial) content held by this message
     */
    virtual uint32_t encodedContentSize() const = 0;
    /**
     * If headers have been received, returns the expected
     * content size else returns 0.
     */
    virtual uint64_t expectedContentSize() = 0;

    virtual void decodeHeader(framing::Buffer& buffer) = 0;
    virtual void decodeContent(framing::Buffer& buffer, uint32_t contentChunkSize = 0) = 0;

    static shared_ptr decode(framing::Buffer& buffer); 
            
    // TODO: AMS 29/1/2007 Don't think these are really part of base class
            
    /**
     * Sets the 'content' implementation of this message (the
     * message controls the lifecycle of the content instance
     * it uses).
     */
    virtual void setContent(std::auto_ptr<Content>& /*content*/) {};
    virtual void setHeader(boost::shared_ptr<framing::AMQHeaderBody>) {};
    virtual void addContent(boost::shared_ptr<framing::AMQContentBody>) {};
    /**
     * Releases the in-memory content data held by this
     * message. Must pass in a store from which the data can
     * be reloaded.
     */
    virtual void releaseContent(MessageStore* /*store*/) {};

  private:
    const ConnectionToken* publisher;
    std::string exchange;
    std::string routingKey;
    const bool mandatory;
    const bool immediate;
    mutable uint64_t persistenceId;
    bool redelivered;
    AMQMethodBodyPtr respondTo;
};

}}


#endif  /*!_broker_BrokerMessage_h*/
