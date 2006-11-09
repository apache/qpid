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
#include <boost/shared_ptr.hpp>
#include <qpid/framing/amqp_types.h>
#include <qpid/framing/Buffer.h>

#ifndef _AMQBody_
#define _AMQBody_

namespace qpid {
    namespace framing {

        class AMQBody
        {
          public:
            typedef boost::shared_ptr<AMQBody> shared_ptr;

            virtual ~AMQBody();
            virtual u_int32_t size() const = 0;
            virtual u_int8_t type() const = 0;
            virtual void encode(Buffer& buffer) const = 0;
            virtual void decode(Buffer& buffer, u_int32_t size) = 0;
            virtual void print(std::ostream& out) const;
        };

        std::ostream& operator<<(std::ostream& out, const AMQBody& body) ;

        enum body_types {METHOD_BODY = 1, HEADER_BODY = 2, CONTENT_BODY = 3, HEARTBEAT_BODY = 8};
    }
}


#endif
