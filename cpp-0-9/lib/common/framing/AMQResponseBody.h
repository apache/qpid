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
 * Body of a response method frame.
 */
class AMQResponseBody : public AMQMethodBody
{

  public:
    typedef boost::shared_ptr<AMQResponseBody> shared_ptr;
    
    struct Data {
        Data(ResponseId id=0, RequestId req=0, BatchOffset off=0)
            : responseId(id), requestId(req), batchOffset(off) {}
        void encode(Buffer&) const;
        void decode(Buffer&);

        uint64_t responseId;
        uint64_t requestId;
        uint32_t batchOffset;
    };

    static Data& getData(const AMQBody::shared_ptr& body) {
        return boost::dynamic_pointer_cast<AMQResponseBody>(body)->getData();
    }

    static shared_ptr create(
        AMQP_MethodVersionMap& versionMap, ProtocolVersion version,
        Buffer& buffer);

    AMQResponseBody(
        ProtocolVersion v, ResponseId id=0, RequestId req=0, BatchOffset off=0)
        : AMQMethodBody(v), data(id, req, off) {}

    uint8_t type() const { return RESPONSE_BODY; }
    void encode(Buffer& buffer) const;

    Data& getData() { return data; }
    ResponseId getResponseId() const { return data.responseId; }
    RequestId getRequestId() const { return data.requestId; }
    BatchOffset getBatchOffset() const { return data.batchOffset; }
    void setResponseId(ResponseId id) { data.responseId = id; }
    void setRequestId(RequestId id) { data.requestId = id; }
    void setBatchOffset(BatchOffset id) { data.batchOffset = id; }

    bool isResponse() const { return true; }
  protected:
    static const uint32_t baseSize() { return AMQMethodBody::baseSize()+20; }
    void printPrefix(std::ostream& out) const;

  private:
    Data data;
};

}} // namespace qpid::framing



#endif  /*!_framing_AMQResponseBody_h*/
