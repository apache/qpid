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

#ifndef _AMQHeartbeatBody_
#define _AMQHeartbeatBody_

namespace qpid {
namespace framing {

class AMQHeartbeatBody : virtual public AMQBody
{
public:
    typedef std::tr1::shared_ptr<AMQHeartbeatBody> shared_ptr;

    virtual ~AMQHeartbeatBody();
    inline u_int32_t size() const { return 0; }
    inline u_int8_t type() const { return HEARTBEAT_BODY; }
    inline void encode(Buffer& ) const {}
    inline void decode(Buffer& , u_int32_t /*size*/) {}
    virtual void print(std::ostream& out) const;
};

}
}

#endif
