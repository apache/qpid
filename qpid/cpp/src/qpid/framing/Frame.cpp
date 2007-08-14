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
#include <boost/format.hpp>

#include "Frame.h"
#include "qpid/QpidError.h"


namespace qpid {
namespace framing {

namespace {
struct GetBodyVisitor : public NoBlankVisitor<AMQBody*> {
    QPID_USING_NOBLANK(AMQBody*);
    AMQBody* operator()(MethodHolder& h) const { return h.getMethod(); }
    template <class T> AMQBody* operator()(T& t) const { return &t; }
};
}

AMQBody* Frame::getBody() {
    return boost::apply_visitor(GetBodyVisitor(), body);
}

const AMQBody* Frame::getBody() const {
    return boost::apply_visitor(GetBodyVisitor(), const_cast<Variant&>(body));
}

void Frame::encode(Buffer& buffer)
{
    buffer.putOctet(getBody()->type());
    buffer.putShort(channel);    
    buffer.putLong(getBody()->size());
    getBody()->encode(buffer);
    buffer.putOctet(0xCE);
}

uint32_t Frame::size() const{
    return 1/*type*/ + 2/*channel*/ + 4/*body size*/ + getBody()->size()
        + 1/*0xCE*/;
}

bool Frame::decode(Buffer& buffer)
{    
    if(buffer.available() < 7)
        return false;
    buffer.record();
    uint32_t frameSize = decodeHead(buffer);
    if(buffer.available() < frameSize + 1){
        buffer.restore();
        return false;
    }
    decodeBody(buffer, frameSize);
    uint8_t end = buffer.getOctet();
    if(end != 0xCE) THROW_QPID_ERROR(FRAMING_ERROR, "Frame end not found");
    return true;
}

uint32_t Frame::decodeHead(Buffer& buffer){    
    type = buffer.getOctet();
    channel = buffer.getShort();
    return buffer.getLong();
}

void Frame::decodeBody(Buffer& buffer, uint32_t size)
{    
    switch(type)
    {
      case METHOD_BODY:
      case REQUEST_BODY:
      case RESPONSE_BODY: {
        ClassId c=buffer.getShort();
        MethodId m=buffer.getShort();
        body = MethodHolder(c,m);
        break;
      }
      case HEADER_BODY: 
	body = AMQHeaderBody();
	break;
      case CONTENT_BODY: 
	body = AMQContentBody();
	break;
      case HEARTBEAT_BODY: 
	body = AMQHeartbeatBody();
	break;
      default:
	THROW_QPID_ERROR(
            FRAMING_ERROR,
            boost::format("Unknown frame type %d") % type);
    }
    getBody()->decode(buffer, size);
}

std::ostream& operator<<(std::ostream& out, const Frame& f)
{
    return out << "Frame[channel=" << f.getChannel() << "; " << f.body << "]";
}


}} // namespace qpid::framing
