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
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQBody.h"
#include "qpid/framing/Buffer.h"

#ifndef _AMQContentBody_
#define _AMQContentBody_

namespace qpid {
namespace framing {

class AMQContentBody : virtual public AMQBody
{
    string data;

public:
    typedef std::tr1::shared_ptr<AMQContentBody> shared_ptr;

    AMQContentBody();
    AMQContentBody(const string& data);
    inline virtual ~AMQContentBody(){}
    inline u_int8_t type() const { return CONTENT_BODY; };
    inline string& getData(){ return data; }
    u_int32_t size() const;
    void encode(Buffer& buffer) const;
    void decode(Buffer& buffer, u_int32_t size);
    void print(std::ostream& out) const;
};

}
}


#endif
