#ifndef _broker_BrokerMessage_h
#define _broker_BrokerMessage_h

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

#include <memory>
#include <boost/shared_ptr.hpp>

#include "BrokerMessageBase.h"
#include "qpid/framing/BasicHeaderProperties.h"
#include "ConnectionToken.h"
#include "Content.h"
#include "qpid/sys/Mutex.h"
#include "TxBuffer.h"

namespace qpid {

namespace framing {
class MethodContext;
class ChannelAdapter;
class AMQHeaderBody;
}

namespace broker {

class MessageStore;
using framing::string;
	
/**
 * Represents an AMQP message, i.e. a header body, a list of
 * content bodies and some details about the publication
 * request.
 */
class BasicMessage : public Message {
    boost::shared_ptr<framing::AMQHeaderBody> header;
    std::auto_ptr<Content> content;
    mutable sys::Mutex contentLock;
    uint64_t size;

    void sendContent(framing::ChannelAdapter&, uint32_t framesize);

  public:
    typedef boost::shared_ptr<BasicMessage> shared_ptr;

    BasicMessage(const ConnectionToken* const publisher, 
                 const string& exchange, const string& routingKey, 
                 bool mandatory, bool immediate,
                 boost::shared_ptr<framing::AMQMethodBody> respondTo);
    BasicMessage();
    ~BasicMessage();
    void setHeader(boost::shared_ptr<framing::AMQHeaderBody> header);
    void addContent(framing::AMQContentBody::shared_ptr data);
    bool isComplete();

    void deliver(framing::ChannelAdapter&, 
                 const string& consumerTag, 
                 uint64_t deliveryTag, 
                 uint32_t framesize);
    
    void sendGetOk(const framing::MethodContext&, 
				   const std::string& destination,
                   uint32_t messageCount,
                   uint64_t deliveryTag, 
                   uint32_t framesize);

    framing::BasicHeaderProperties* getHeaderProperties();
    const framing::FieldTable& getApplicationHeaders();
    bool isPersistent();
    uint64_t contentSize() const { return size; }

    void decode(framing::Buffer& buffer, bool headersOnly = false,
                uint32_t contentChunkSize = 0);
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer, uint32_t contentChunkSize = 0);

    void encode(framing::Buffer& buffer) const;
    void encodeHeader(framing::Buffer& buffer) const;
    void encodeContent(framing::Buffer& buffer) const;
    /**
     * @returns the size of the buffer needed to encode this
     * message in its entirety
     */
    uint32_t encodedSize() const;
    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     */
    uint32_t encodedHeaderSize() const;
    /**
     * @returns the size of the buffer needed to encode the
     * (possibly partial) content held by this message
     */
    uint32_t encodedContentSize() const;
    /**
     * Releases the in-memory content data held by this
     * message. Must pass in a store from which the data can
     * be reloaded.
     */
    void releaseContent(MessageStore* store);
    /**
     * If headers have been received, returns the expected
     * content size else returns 0.
     */
    uint64_t expectedContentSize();
    /**
     * Sets the 'content' implementation of this message (the
     * message controls the lifecycle of the content instance
     * it uses).
     */
    void setContent(std::auto_ptr<Content>& content);
};

}
}


#endif  /*!_broker_BrokerMessage_h*/
