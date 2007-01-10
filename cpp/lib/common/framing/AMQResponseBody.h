#ifndef _framing_AMQResponseBody_h
#define _framing_AMQResponseBody_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "AMQMethodBody.h"

namespace qpid {
namespace framing {

class AMQP_MethodVersionMap;

/**
 * Body of an AMQP Response frame.
 */
class AMQResponseBody : public AMQBody
{

  public:
    typedef boost::shared_ptr<AMQResponseBody> shared_ptr;

    AMQResponseBody(AMQP_MethodVersionMap&, ProtocolVersion);
    AMQResponseBody(
        AMQP_MethodVersionMap&, ProtocolVersion,
        u_int64_t responseId, u_int64_t requestId, u_int32_t batchOffset,
        AMQMethodBody::shared_ptr method);

    const AMQMethodBody& getMethodBody() const { return *method; }
    AMQMethodBody& getMethodBody()  { return *method; }
    u_int64_t getResponseId() { return responseId; }
    u_int64_t getRequestId() { return requestId; }
    u_int32_t getBatchOffset() { return batchOffset; }
    
    u_int32_t size() const  { return 20 + method->size(); }
    u_int8_t type() const { return RESPONSE_BODY; }
    
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer, u_int32_t size);
    void print(std::ostream& out) const;

  private:
    AMQP_MethodVersionMap& versionMap;
    ProtocolVersion version;
    u_int64_t responseId;
    u_int64_t requestId;
    u_int32_t batchOffset;
    AMQMethodBody::shared_ptr method;
};

}} // namespace qpid::framing



#endif  /*!_framing_AMQResponseBody_h*/
