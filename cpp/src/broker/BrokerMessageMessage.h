#ifndef _broker_BrokerMessageMessage_h
#define _broker_BrokerMessageMessage_h

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
#include "BrokerMessageBase.h"
#include "MessageTransferBody.h"
#include "../framing/amqp_types.h"

#include <vector>

namespace qpid {

namespace framing {
class MessageTransferBody;
}
	
namespace broker {
class ConnectionToken;
class Reference;

class MessageMessage: public Message{
  public:
    typedef boost::shared_ptr<MessageMessage> shared_ptr;
    typedef boost::shared_ptr<framing::MessageTransferBody> TransferPtr;
    typedef boost::shared_ptr<Reference> ReferencePtr;

    MessageMessage(ConnectionToken* publisher, framing::RequestId, TransferPtr transfer);
    MessageMessage(ConnectionToken* publisher, framing::RequestId, TransferPtr transfer, ReferencePtr reference);
    MessageMessage();
            
    // Default destructor okay

    framing::RequestId getRequestId() {return requestId; }
    TransferPtr getTransfer() { return transfer; }
    ReferencePtr getReference() { return reference; }
    
    void deliver(framing::ChannelAdapter& channel, 
                 const std::string& consumerTag, 
                 uint64_t deliveryTag, 
                 uint32_t framesize);
    
    void sendGetOk(const framing::MethodContext& context, 
				   const std::string& destination,
                   uint32_t messageCount,
                   uint64_t deliveryTag, 
                   uint32_t framesize);

    bool isComplete();

    uint64_t contentSize() const;
    framing::BasicHeaderProperties* getHeaderProperties();
    const framing::FieldTable& getApplicationHeaders();
    bool isPersistent();
            
    void encode(framing::Buffer& buffer) const;
    void encodeHeader(framing::Buffer& buffer) const;
    uint32_t encodedSize() const;
    uint32_t encodedHeaderSize() const;
    uint32_t encodedContentSize() const;
    uint64_t expectedContentSize();
    void decodeHeader(framing::Buffer& buffer);
    void decodeContent(framing::Buffer& buffer, uint32_t contentChunkSize = 0);

  private:
    void transferMessage(framing::ChannelAdapter& channel, 
                         const std::string& consumerTag, 
                         uint32_t framesize);
    framing::MessageTransferBody* copyTransfer(const framing::ProtocolVersion& version,
                                               const std::string& destination, 
                                               const framing::Content& body) const;
  
    framing::RequestId requestId;
    const TransferPtr transfer;
    const ReferencePtr reference;
};

}}


#endif  /*!_broker_BrokerMessage_h*/
