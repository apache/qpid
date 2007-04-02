#ifndef _framing_AMQRequestBody_h
#define _framing_AMQRequestBody_h

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

/**
 * Body of a request method frame.
 */
class AMQRequestBody : public AMQMethodBody
{
 public:
    typedef boost::shared_ptr<AMQRequestBody> shared_ptr;

    struct Data {
        Data(RequestId id=0, ResponseId mark=0)
            : requestId(id), responseMark(mark) {}
        void encode(Buffer&) const;
        void decode(Buffer&);

        RequestId requestId;
        ResponseId responseMark;
    };

    static Data& getData(const AMQBody::shared_ptr& body) {
        return boost::dynamic_pointer_cast<AMQRequestBody>(body)->getData();
    }

    static shared_ptr create(
        AMQP_MethodVersionMap& versionMap, ProtocolVersion version,
        Buffer& buffer);

    AMQRequestBody(ProtocolVersion v, RequestId id=0, ResponseId mark=0)
        : AMQMethodBody(v), data(id, mark) {}

    uint8_t type() const { return REQUEST_BODY; }
    void encode(Buffer& buffer) const;

    Data& getData() { return data; }
    RequestId  getRequestId() const { return data.requestId; }
    ResponseId getResponseMark() const { return data.responseMark; }
    void setRequestId(RequestId id) { data.requestId=id; }
    void setResponseMark(ResponseId mark) { data.responseMark=mark; }

    bool isRequest()const { return true; }
    static const uint32_t baseSize() { return AMQMethodBody::baseSize()+20; }
  protected:
    void printPrefix(std::ostream& out) const;
    
  private:
    Data data;
};

}} // namespace qpid::framing



#endif  /*!_framing_AMQRequestBody_h*/
