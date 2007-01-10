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
    u_int32_t size() const { return 4 + bodySize(); }
    AMQMethodBody(u_int8_t major, u_int8_t minor) : version(major, minor) {}
    AMQMethodBody(ProtocolVersion version) : version(version) {}
    virtual ~AMQMethodBody() {}

    virtual u_int16_t amqpMethodId() const = 0;
    virtual u_int16_t amqpClassId() const = 0;
    virtual void invoke(AMQP_ServerOperations& target, u_int16_t channel);
    virtual void encodeContent(Buffer& buffer) const = 0;
    virtual void decodeContent(Buffer& buffer) = 0;
    virtual u_int32_t bodySize() const = 0;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer, u_int32_t size);
    bool match(AMQMethodBody* other) const;
};

}} // namespace qpid::framing


#endif
