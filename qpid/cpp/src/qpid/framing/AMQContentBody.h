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
#include "amqp_types.h"
#include "AMQBody.h"
#include "Buffer.h"

#ifndef _AMQContentBody_
#define _AMQContentBody_

namespace qpid {
namespace framing {

class AMQContentBody :  public AMQBody
{
    string data;

public:
    AMQContentBody();
    AMQContentBody(const string& data);
    inline virtual ~AMQContentBody(){}
    inline uint8_t type() const { return CONTENT_BODY; };
    inline const string& getData() const { return data; }
    inline string& getData() { return data; }
    uint32_t encodedSize() const;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer, uint32_t size);
    void print(std::ostream& out) const;
    void accept(AMQBodyConstVisitor& v) const { v.visit(*this); }
    boost::intrusive_ptr<AMQBody> clone() const { return BodyFactory::copy(*this); }
};

}
}


#endif
