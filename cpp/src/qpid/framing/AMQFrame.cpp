
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

#include "AMQFrame.h"
#include "qpid/QpidError.h"
#include "AMQRequestBody.h"
#include "AMQResponseBody.h"


namespace qpid {
namespace framing {


AMQP_MethodVersionMap AMQFrame::versionMap;

AMQFrame::AMQFrame(ProtocolVersion _version)
    : channel(0), type(0), version(_version)
 {
     assert(version != ProtocolVersion(0,0));
 }

AMQFrame::AMQFrame(ProtocolVersion _version, uint16_t _channel, AMQBody* _body) : channel(_channel), body(_body),version(_version) {}

AMQFrame::AMQFrame(ProtocolVersion _version, uint16_t _channel, const AMQBody::shared_ptr& _body) :
    channel(_channel), body(_body), version(_version)
{}

AMQFrame::~AMQFrame() {}

void AMQFrame::encode(Buffer& buffer)
{
    buffer.putOctet(body->type());
    buffer.putShort(channel);    
    buffer.putLong(body->size());
    body->encode(buffer);
    buffer.putOctet(0xCE);
}

uint32_t AMQFrame::size() const{
    assert(body.get());
    return 1/*type*/ + 2/*channel*/ + 4/*body size*/ + body->size()
        + 1/*0xCE*/;
}

bool AMQFrame::decode(Buffer& buffer)
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

uint32_t AMQFrame::decodeHead(Buffer& buffer){    
    type = buffer.getOctet();
    channel = buffer.getShort();
    return buffer.getLong();
}

void AMQFrame::decodeBody(Buffer& buffer, uint32_t size)
{    
    switch(type)
    {
      case METHOD_BODY:
        body = AMQMethodBody::create(versionMap, version, buffer);
        break;
      case REQUEST_BODY:
        body = AMQRequestBody::create(versionMap, version, buffer);
        break;
      case RESPONSE_BODY:
        body = AMQResponseBody::create(versionMap, version, buffer);
        break;
      case HEADER_BODY: 
	body = AMQBody::shared_ptr(new AMQHeaderBody()); 
	break;
      case CONTENT_BODY: 
	body = AMQBody::shared_ptr(new AMQContentBody()); 
	break;
      case HEARTBEAT_BODY: 
	body = AMQBody::shared_ptr(new AMQHeartbeatBody()); 
	break;
      default:
	THROW_QPID_ERROR(
            FRAMING_ERROR,
            boost::format("Unknown frame type %d") % type);
    }
    body->decode(buffer, size);
}

std::ostream& operator<<(std::ostream& out, const AMQFrame& t)
{
    out << "Frame[channel=" << t.channel << "; ";
    if (t.body.get() == 0)
        out << "empty";
    else
        out << *t.body;
    out << "]";
    return out;
}


}} // namespace qpid::framing
