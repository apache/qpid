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
#include "MethodHolder.h"
#include "ProtocolVersion.h"

#include <boost/cast.hpp>
#include <boost/variant.hpp>

namespace qpid {
namespace framing {
	
class AMQFrame : public AMQDataBlock
{
  public:
    AMQFrame() : bof(true), eof(true), bos(true), eos(true), subchannel(0), channel(0) {}

    /** Construct a frame with a copy of b */
    AMQFrame(ChannelId c, const AMQBody* b) : bof(true), eof(true), bos(true), eos(true), subchannel(0), channel(c) {
        setBody(*b);
    }
    
    AMQFrame(ChannelId c, const AMQBody& b) : bof(true), eof(true), bos(true), eos(true), subchannel(0), channel(c) {
        setBody(b);
    }
    
    AMQFrame(const AMQBody& b) : bof(true), eof(true), bos(true), eos(true), subchannel(0), channel(0) {
        setBody(b);
    }
    
    ChannelId getChannel() const { return channel; }
    void setChannel(ChannelId c) { channel = c; }

    AMQBody* getBody();
    const AMQBody* getBody() const;

    AMQMethodBody* getMethod() { return getBody()->getMethod(); }
    const AMQMethodBody* getMethod() const { return getBody()->getMethod(); }

    /** Copy a body instance to the frame */
    void setBody(const AMQBody& b) { CopyVisitor cv(*this); b.accept(cv); }

    /** Convenience template to cast the body to an expected type. */
    template <class T> T* castBody() {
        return boost::polymorphic_downcast<T*>(getBody());
    }

    template <class T> const T* castBody() const {
        return boost::polymorphic_downcast<const T*>(getBody());
    }

    bool empty() { return boost::get<boost::blank>(&body); }

    void encode(Buffer& buffer) const; 
    bool decode(Buffer& buffer); 
    uint32_t size() const;

    bool getBof() const { return bof; }
    void setBof(bool isBof) { bof = isBof; }
    bool getEof() const { return eof; }
    void setEof(bool isEof) { eof = isEof; }

    bool getBos() const { return bos; }
    void setBos(bool isBos) { bos = isBos; }
    bool getEos() const { return eos; }
    void setEos(bool isEos) { eos = isEos; }

    static uint32_t frameOverhead();

  private:
    struct CopyVisitor : public AMQBodyConstVisitor {
        AMQFrame& frame;
        CopyVisitor(AMQFrame& f) : frame(f) {}
        void visit(const AMQHeaderBody& x) { frame.body=x; }
        void visit(const AMQContentBody& x) { frame.body=x; }
        void visit(const AMQHeartbeatBody& x) { frame.body=x; }
        void visit(const AMQMethodBody& x) { frame.body=MethodHolder(x); }
    };
    friend struct CopyVisitor;

    typedef boost::variant<boost::blank,
                           AMQHeaderBody,
                           AMQContentBody,
                           AMQHeartbeatBody,
                           MethodHolder> Variant;

    void visit(AMQHeaderBody& x) { body=x; }

    void decodeBody(Buffer& buffer, uint32_t size, uint8_t type);

    bool bof;
    bool eof;
    bool bos;
    bool eos;
    uint8_t subchannel;
    uint16_t channel;
    Variant body;
};

std::ostream& operator<<(std::ostream&, const AMQFrame&);

}} // namespace qpid::framing


#endif
