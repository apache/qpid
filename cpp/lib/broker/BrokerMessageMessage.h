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
#include "Reference.h"         

#include <vector>

namespace qpid {

namespace framing {
class MessageTransferBody;
class MessageApppendBody;
}
	
namespace broker {
class Reference;

class MessageMessage: public Message{
  public:
    typedef Reference::TransferPtr TransferPtr;
    typedef Reference::AppendPtr AppendPtr;
    typedef Reference::Appends Appends;

    MessageMessage(TransferPtr transfer);
    MessageMessage(TransferPtr transfer, const Reference&);
            
    // Default destructor okay
			            
    void deliver(framing::ChannelAdapter& channel, 
                 const std::string& consumerTag, 
                 u_int64_t deliveryTag, 
                 u_int32_t framesize);
    
    void sendGetOk(const framing::MethodContext& context, 
                   u_int32_t messageCount,
                   u_int64_t deliveryTag, 
                   u_int32_t framesize);

    bool isComplete();

    u_int64_t contentSize() const;
    framing::BasicHeaderProperties* getHeaderProperties();
    const framing::FieldTable& getApplicationHeaders();
    bool isPersistent();
    const ConnectionToken* const getPublisher();
            
    u_int32_t encodedSize();
    u_int32_t encodedHeaderSize();
    u_int32_t encodedContentSize();
    u_int64_t expectedContentSize();

    TransferPtr getTransfer() { return transfer; }
    const Appends& getAppends() { return appends; }
  private:

    const TransferPtr transfer;
    const Appends appends;
};

}}


#endif  /*!_broker_BrokerMessage_h*/
