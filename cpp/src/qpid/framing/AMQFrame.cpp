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

void AMQFrame::encode(Buffer& buffer)
{
    buffer.putOctet(getBody()->type());
    buffer.putShort(channel);    
    buffer.putLong(boost::apply_visitor(SizeVisitor(), body));
    boost::apply_visitor(EncodeVisitor(buffer), body);
    buffer.putOctet(0xCE);
}

uint32_t AMQFrame::size() const{
    return 1/*type*/ + 2/*channel*/ + 4/*body size*/ +
        boost::apply_visitor(SizeVisitor(), body) + 1/*0xCE*/;
}

bool AMQFrame::decode(Buffer& buffer)
{    
    if(buffer.available() < 7)
        return false;
    buffer.record();

    uint8_t type = buffer.getOctet();
    channel = buffer.getShort();
    uint32_t size =  buffer.getLong();

    if(buffer.available() < size+1){
        buffer.restore();
        return false;
    }
    decodeBody(buffer, size, type);
    uint8_t end = buffer.getOctet();
    if(end != 0xCE) THROW_QPID_ERROR(FRAMING_ERROR, "Frame end not found");
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
    return out << "Frame[channel=" << f.getChannel() << "; " << *f.getBody()
               << "]";
}


}} // namespace qpid::framing
