#ifndef _AMQFrame_
#define _AMQFrame_

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
#include "ProtocolVersion.h"
#include "BodyHolder.h"
#include "qpid/sys/LatencyMetric.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/cast.hpp>

namespace qpid {
namespace framing {

class BodyHolder;

class AMQFrame : public AMQDataBlock, public sys::LatencyMetricTimestamp
{
  public:
    AMQFrame(boost::intrusive_ptr<BodyHolder> b=0) : body(b) { init(); }
    AMQFrame(const AMQBody& b) { setBody(b); init(); }
    ~AMQFrame();

    template <class InPlace>
    AMQFrame(const InPlace& ip, typename EnableInPlace<InPlace>::type* =0) {
        init(); setBody(ip);
    }

    ChannelId getChannel() const { return channel; }
    void setChannel(ChannelId c) { channel = c; }

    boost::intrusive_ptr<BodyHolder> getHolder() { return body; }
    
    AMQBody* getBody() { return body ? body->get() : 0; }
    const AMQBody* getBody() const { return body ? body->get() : 0; }

    AMQMethodBody* getMethod() { return getBody()->getMethod(); }
    const AMQMethodBody* getMethod() const { return getBody()->getMethod(); }

    void setBody(const AMQBody& b);

    template <class InPlace>
    typename EnableInPlace<InPlace>::type setBody(const InPlace& ip) {
        body = new BodyHolder(ip);
    }

    void setMethod(ClassId c, MethodId m);

    template <class T> T* castBody() {
        return boost::polymorphic_downcast<T*>(getBody());
    }

    template <class T> const T* castBody() const {
        return boost::polymorphic_downcast<const T*>(getBody());
    }

    void encode(Buffer& buffer) const; 
    bool decode(Buffer& buffer); 
    uint32_t encodedSize() const;

    // 0-10 terminology: first/last frame (in segment) first/last segment (in assembly)

    bool isFirstSegment() const { return bof; }
    bool isLastSegment() const { return eof; }
    bool isFirstFrame() const { return bos; }
    bool isLastFrame() const { return eos; }

    void setFirstSegment(bool set=true) { bof = set; }
    void setLastSegment(bool set=true) { eof = set; }
    void setFirstFrame(bool set=true) { bos = set; }
    void setLastFrame(bool set=true) { eos = set; }

    // 0-9 terminology: beginning/end of frameset, beginning/end of segment.

    bool getBof() const { return bof; }
    void setBof(bool isBof) { bof = isBof; }
    bool getEof() const { return eof; }
    void setEof(bool isEof) { eof = isEof; }

    bool getBos() const { return bos; }
    void setBos(bool isBos) { bos = isBos; }
    bool getEos() const { return eos; }
    void setEos(bool isEos) { eos = isEos; }

    static uint16_t DECODE_SIZE_MIN;
    static uint32_t frameOverhead();
    /** Must point to at least DECODE_SIZE_MIN bytes of data */
    static uint16_t decodeSize(char* data);
  private:
    void init() { bof = eof = bos = eos = true; subchannel=0; channel=0; }

    boost::intrusive_ptr<BodyHolder> body;
    uint16_t channel : 16;
    uint8_t subchannel : 8;
    bool bof : 1;
    bool eof : 1;
    bool bos : 1;
    bool eos : 1;
};

std::ostream& operator<<(std::ostream&, const AMQFrame&);

}} // namespace qpid::framing


#endif
