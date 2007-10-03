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
#include "AMQFrame.h"

#include "qpid/QpidError.h"
#include "qpid/framing/variant.h"
#include "qpid/framing/AMQMethodBody.h"

#include <boost/format.hpp>

#include <iostream>

namespace qpid {
namespace framing {

namespace {
struct GetBodyVisitor : public NoBlankVisitor<AMQBody*> {
    QPID_USING_NOBLANK(AMQBody*);
    AMQBody* operator()(MethodHolder& t) const { return t.get(); }
    template <class T> AMQBody* operator()(T& t) const { return &t; }
};

struct EncodeVisitor : public NoBlankVisitor<void> {
    Buffer& buffer;
    EncodeVisitor(Buffer& b) : buffer(b) {}
    
    QPID_USING_NOBLANK(void);
    template <class T> void operator()(const T& t) const { return t.encode(buffer); }
};

struct SizeVisitor : public NoBlankVisitor<uint32_t> {
    QPID_USING_NOBLANK(uint32_t);
    template <class T> uint32_t operator()(const T& t) const { return t.size(); }
};

struct DecodeVisitor : public NoBlankVisitor<void> {
    Buffer& buffer;
    uint32_t size;
    DecodeVisitor(Buffer& b, uint32_t s) : buffer(b), size(s) {}
    QPID_USING_NOBLANK(void);
    void operator()(MethodHolder& t) const { return t.decode(buffer); }
    template <class T> void operator()(T& t) const { return t.decode(buffer, size); }
};

}

AMQBody* AMQFrame::getBody() {
    return boost::apply_visitor(GetBodyVisitor(), body);
}

const AMQBody* AMQFrame::getBody() const {
    return boost::apply_visitor(GetBodyVisitor(), const_cast<Variant&>(body));
}

// This is now misleadingly named as it is not the frame size as defined in the spec 
// (as it also includes the end marker)
uint32_t AMQFrame::size() const{
    return frameOverhead() + boost::apply_visitor(SizeVisitor(), body);
}

uint32_t AMQFrame::frameOverhead() {
    return 12 /*frame header*/ + 1/*0xCE*/;
}

void AMQFrame::encode(Buffer& buffer) const
{
    uint8_t flags = (bof ? 0x08 : 0) | (eof ? 0x04 : 0) | (bos ? 0x02 : 0) | (eos ? 0x01 : 0);
    buffer.putOctet(flags);
    buffer.putOctet(getBody()->type());
    buffer.putShort(size() - 1); // Don't include end marker (it's not part of the frame itself)
    buffer.putOctet(0);
    buffer.putOctet(0x0f & subchannel);
    buffer.putShort(channel);    
    buffer.putLong(0);
    boost::apply_visitor(EncodeVisitor(buffer), body);
    buffer.putOctet(0xCE);
}

bool AMQFrame::decode(Buffer& buffer)
{    
    if(buffer.available() < frameOverhead() - 1)
        return false;
    buffer.record();

    uint8_t  flags = buffer.getOctet();
    uint8_t framing_version = (flags & 0xc0) >> 6;
    if (framing_version != 0)
        THROW_QPID_ERROR(FRAMING_ERROR, "Framing version unsupported");
    bof = flags & 0x08;
    eof = flags & 0x04;
    bos = flags & 0x02;
    eos = flags & 0x01;
    uint8_t  type = buffer.getOctet();
    uint16_t frame_size =  buffer.getShort();
    if (frame_size < frameOverhead()-1)
        THROW_QPID_ERROR(FRAMING_ERROR, "Frame size too small");    
    uint8_t  reserved1 = buffer.getOctet();
    uint8_t  field1 = buffer.getOctet();
    subchannel = field1 & 0x0f;
    channel = buffer.getShort();
    (void) buffer.getLong(); // reserved2
    
    // Verify that the protocol header meets current spec
    // TODO: should we check reserved2 against zero as well? - the spec isn't clear
    if ((flags & 0x30) != 0 || reserved1 != 0 || (field1 & 0xf0) != 0)
        THROW_QPID_ERROR(FRAMING_ERROR, "Reserved bits not zero");

    // TODO: should no longer care about body size and only pass up B,E,b,e flags
    uint16_t body_size = frame_size + 1 - frameOverhead(); 
    if (buffer.available() < body_size+1u){
        buffer.restore();
        return false;
    }
    decodeBody(buffer, body_size, type);

    uint8_t end = buffer.getOctet();
    if (end != 0xCE)
        THROW_QPID_ERROR(FRAMING_ERROR, "Frame end not found");
    return true;
}

void AMQFrame::decodeBody(Buffer& buffer, uint32_t size, uint8_t type)
{    
    switch(type)
    {
      case METHOD_BODY: body = MethodHolder(); break;
      case HEADER_BODY: body = AMQHeaderBody();	break;
      case CONTENT_BODY: body = AMQContentBody(); break;
      case HEARTBEAT_BODY: body = AMQHeartbeatBody(); break;

      default:
	THROW_QPID_ERROR(
            FRAMING_ERROR,
            boost::format("Unknown frame type %d") % type);
    }
    boost::apply_visitor(DecodeVisitor(buffer,size), body);
}

std::ostream& operator<<(std::ostream& out, const AMQFrame& f)
{
    return out << "Frame[" 
        //<< "B=" << f.getBof() << "E=" << f.getEof() << "b=" << f.getBos() << "e=" << f.getEos() << "; "
               << "channel=" << f.getChannel() << "; " << *f.getBody()
               << "]";
}


}} // namespace qpid::framing
