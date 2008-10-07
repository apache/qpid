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
#include "BodyHolder.h"
#include "AMQMethodBody.h"
#include "AMQHeaderBody.h"
#include "AMQContentBody.h"
#include "AMQHeartbeatBody.h"
#include "Buffer.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace framing {


// BodyHolder::operator=(const AMQBody&)  is defined
// in generated file BodyHolder_gen.cpp


void BodyHolder::encode(Buffer& b) const {
    const AMQMethodBody* method=getMethod();
    if (method) {
        b.putOctet(method->amqpClassId());
        b.putOctet(method->amqpMethodId());
        method->encode(b);
    }
    else
        get()->encode(b);
}

void BodyHolder::decode(uint8_t type, Buffer& buffer, uint32_t size) {
    switch(type)
    {
      case 0://CONTROL 
      case METHOD_BODY: {
          ClassId c = buffer.getOctet();
          MethodId m = buffer.getOctet();
          setMethod(c, m);
          break;
      }
      case HEADER_BODY: *this=in_place<AMQHeaderBody>(); break;
      case CONTENT_BODY: *this=in_place<AMQContentBody>(); break;
      case HEARTBEAT_BODY: *this=in_place<AMQHeartbeatBody>(); break;
      default:
	throw IllegalArgumentException(QPID_MSG("Invalid frame type " << type));
    }
    get()->decode(buffer, size);
}

uint32_t BodyHolder::encodedSize() const {
    const AMQMethodBody* method=getMethod();
    if (method) 
        return sizeof(ClassId)+sizeof(MethodId)+method->encodedSize();
    else
        return get()->encodedSize();
}

}} // namespace qpid::framing

