#ifndef _client_ClientMessage_h
#define _client_ClientMessage_h

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
#include "qpid/framing/BasicHeaderProperties.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/MethodContent.h"

#include "qpid/framing/BasicDeliverBody.h"
#include "qpid/framing/BasicGetOkBody.h"
#include "qpid/framing/MessageTransferBody.h"

namespace qpid {
namespace client {

/**
 * A representation of messages for sent or recived through the
 * client api.
 *
 * \ingroup clientapi
 */
// FIXME aconway 2007-04-05: Should be based on MessageTransfer properties not
// basic header properties.
class Message : public framing::BasicHeaderProperties, public framing::MethodContent {
  public:
    Message(const std::string& data_=std::string()) : data(data_) {}

    const std::string& getData() const { return data; }
    void setData(const std::string& _data) { data = _data; }

    std::string getDestination() const { return destination; }
    void setDestination(const std::string& dest) { destination = dest; }

    // TODO aconway 2007-03-22: only needed for Basic.deliver support.
    uint64_t getDeliveryTag() const { return deliveryTag; }
    void setDeliveryTag(uint64_t dt) { deliveryTag = dt; }

    bool isRedelivered() const { return redelivered; }
    void setRedelivered(bool _redelivered){  redelivered = _redelivered; }

    framing::AMQHeaderBody getHeader() const
    { 
        framing::AMQHeaderBody header;
        BasicHeaderProperties* properties = header.get<BasicHeaderProperties>(true);
        BasicHeaderProperties::copy<BasicHeaderProperties, Message>(*properties, *this);
        properties->setContentLength(data.size());
        return header;
    }

    //TODO: move this elsewhere (GRS 24/08/2007)
    void populate(framing::FrameSet& frameset)
    {
        const BasicHeaderProperties* properties = frameset.getHeaders()->get<BasicHeaderProperties>();
        if (properties) {
            BasicHeaderProperties::copy<Message, BasicHeaderProperties>(*this, *properties);
        }
        frameset.getContent(data);
    }

  private:
    std::string data;
    std::string destination;
    bool redelivered;
    uint64_t deliveryTag;
};

}}

#endif  /*!_client_ClientMessage_h*/
