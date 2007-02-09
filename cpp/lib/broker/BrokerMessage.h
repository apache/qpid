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

#include <BrokerMessageBase.h>
#include <memory>
#include <boost/shared_ptr.hpp>
#include <AMQContentBody.h>
#include <AMQHeaderBody.h>
#include "AMQMethodBody.h"
#include <BasicHeaderProperties.h>
#include <ConnectionToken.h>
#include <Content.h>
#include <Mutex.h>
#include <TxBuffer.h>

namespace qpid {

namespace framing {
class MethodContext;
class ChannelAdapter;
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
    framing::AMQHeaderBody::shared_ptr header;
    std::auto_ptr<Content> content;
    sys::Mutex contentLock;
    u_int64_t size;

    void sendContent(framing::ChannelAdapter&, u_int32_t framesize);

  public:
    typedef boost::shared_ptr<BasicMessage> shared_ptr;

    BasicMessage(const ConnectionToken* const publisher, 
                 const string& exchange, const string& routingKey, 
                 bool mandatory, bool immediate,
                 framing::AMQMethodBody::shared_ptr respondTo);
    BasicMessage();
    ~BasicMessage();
    void setHeader(framing::AMQHeaderBody::shared_ptr header);
    void addContent(framing::AMQContentBody::shared_ptr data);
    bool isComplete();

    void deliver(framing::ChannelAdapter&, 
                 const string& consumerTag, 
                 u_int64_t deliveryTag, 
                 u_int32_t framesize);
    
    void sendGetOk(const framing::MethodContext&, 
				   const std::string& destination,
                   u_int32_t messageCount,
                   u_int64_t deliveryTag, 
                   u_int32_t framesize);

    framing::BasicHeaderProperties* getHeaderProperties();
    const framing::FieldTable& getApplicationHeaders();
    bool isPersistent();
    u_int64_t contentSize() const { return size; }

    void decode(framing::Buffer& buffer, bool headersOnly = false,
                u_int32_t contentChunkSize = 0);
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer, u_int32_t contentChunkSize = 0);

    void encode(framing::Buffer& buffer);
    void encodeHeader(framing::Buffer& buffer);
    void encodeContent(framing::Buffer& buffer);
    /**
     * @returns the size of the buffer needed to encode this
     * message in its entirety
     */
    u_int32_t encodedSize();
    /**
     * @returns the size of the buffer needed to encode the
     * 'header' of this message (not just the header frame,
     * but other meta data e.g.routing key and exchange)
     */
    u_int32_t encodedHeaderSize();
    /**
     * @returns the size of the buffer needed to encode the
     * (possibly partial) content held by this message
     */
    u_int32_t encodedContentSize();
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
    u_int64_t expectedContentSize();
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
