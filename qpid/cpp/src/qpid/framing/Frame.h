#ifndef QPID_FRAMING_FRAME_H
#define QPID_FRAMING_FRAME_H

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
#include "AMQDataBlock.h"
#include "AMQHeaderBody.h"
#include "AMQContentBody.h"
#include "AMQHeartbeatBody.h"
#include "MethodHolder.h"

namespace qpid {
namespace framing {

class Frame : public AMQDataBlock {
  public:
    typedef boost::variant<boost::blank,
                           AMQHeaderBody,
                           AMQContentBody,
                           AMQHeartbeatBody,
                           MethodHolder> Variant;

    Frame(ChannelId channel_=0, const Variant& body_=Variant())
        : body(body_), channel(channel_) {}

    void encode(Buffer& buffer);
    bool decode(Buffer& buffer); 
    uint32_t size() const;

    uint16_t getChannel() const { return channel; }

    AMQBody* getBody();
    const AMQBody* getBody() const;

    template <class T> T* castBody() {
        return boost::polymorphic_downcast<T*>(getBody());
    }

    Variant body;

  private:
    uint32_t decodeHead(Buffer& buffer); 
    void decodeBody(Buffer& buffer, uint32_t size); 

    uint8_t type;
    uint16_t channel;
};

std::ostream& operator<<(std::ostream&, const Frame&);

}} // namespace qpid::framing


#endif  /*!QPID_FRAMING_FRAME_H*/
