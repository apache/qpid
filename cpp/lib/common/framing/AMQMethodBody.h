#ifndef _AMQMethodBody_
#define _AMQMethodBody_

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
#include <iostream>
#include <amqp_types.h>
#include <AMQBody.h>
#include <Buffer.h>
#include <AMQP_ServerOperations.h>
#include <MethodContext.h>

namespace qpid {
namespace framing {

class AMQP_MethodVersionMap;

class AMQMethodBody : public AMQBody
{
  public:
    typedef boost::shared_ptr<AMQMethodBody> shared_ptr;

    static shared_ptr create(
        AMQP_MethodVersionMap& map, ProtocolVersion version, Buffer& buf);

    ProtocolVersion version;    
    u_int8_t type() const { return METHOD_BODY; }
    AMQMethodBody(u_int8_t major, u_int8_t minor) : version(major, minor) {}
    AMQMethodBody(ProtocolVersion ver) : version(ver) {}
    virtual ~AMQMethodBody() {}
    void decode(Buffer&, u_int32_t);

    virtual MethodId amqpMethodId() const = 0;
    virtual ClassId  amqpClassId() const = 0;
    
    virtual void invoke(AMQP_ServerOperations&, const MethodContext&);

    template <class T> bool isA() {
        return amqpClassId()==T::CLASS_ID && amqpMethodId()==T::METHOD_ID;
    }

    /** Return request ID or response correlationID */
    virtual RequestId getRequestId() const { return 0; }

  protected:
    static u_int32_t baseSize() { return 4; }

    struct ClassMethodId {
        u_int16_t classId;
        u_int16_t methodId;
        void decode(Buffer& b);
    };
    
    void encodeId(Buffer& buffer) const;
    virtual void encodeContent(Buffer& buffer) const = 0;
    virtual void decodeContent(Buffer& buffer) = 0;
};


}} // namespace qpid::framing


#endif
