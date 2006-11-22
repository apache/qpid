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
/*#include <qpid/framing/amqp_methods.h>*/
#include <qpid/framing/amqp_types.h>
#include <qpid/framing/AMQBody.h>
#include <qpid/framing/AMQDataBlock.h>
#include <qpid/framing/AMQMethodBody.h>
#include <qpid/framing/AMQHeaderBody.h>
#include <qpid/framing/AMQContentBody.h>
#include <qpid/framing/AMQHeartbeatBody.h>
#include <qpid/framing/AMQP_MethodVersionMap.h>
#include <qpid/framing/Buffer.h>

#ifndef _AMQFrame_
#define _AMQFrame_

namespace qpid {
    namespace framing {

        class AMQFrame : virtual public AMQDataBlock
        {
            u_int16_t channel;
            u_int8_t type;//used if the body is decoded separately from the 'head'
            AMQBody::shared_ptr body;
            AMQP_MethodVersionMap versionMap;
			AMQBody::shared_ptr createMethodBody(Buffer& buffer);
            
        public:
            AMQFrame();
            AMQFrame(u_int16_t channel, AMQBody* body);
            AMQFrame(u_int16_t channel, AMQBody::shared_ptr& body);
            virtual ~AMQFrame();
            virtual void encode(Buffer& buffer); 
            virtual bool decode(Buffer& buffer); 
            virtual u_int32_t size() const;
            u_int16_t getChannel();
            AMQBody::shared_ptr& getBody();

            u_int32_t decodeHead(Buffer& buffer); 
            void decodeBody(Buffer& buffer, uint32_t size); 

            friend std::ostream& operator<<(std::ostream& out, const AMQFrame& body);
        };

    }
}


#endif
