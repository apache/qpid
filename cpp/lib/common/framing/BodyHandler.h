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
#include <string>

#ifndef _BodyHandler_
#define _BodyHandler_

#include <AMQMethodBody.h>
#include <AMQHeaderBody.h>
#include <AMQContentBody.h>
#include <AMQHeartbeatBody.h>

namespace qpid {
namespace framing {

    class BodyHandler{
    public:
        virtual ~BodyHandler();
	virtual void handleMethod(AMQMethodBody::shared_ptr body) = 0;
	virtual void handleHeader(AMQHeaderBody::shared_ptr body) = 0;
	virtual void handleContent(AMQContentBody::shared_ptr body) = 0;
	virtual void handleHeartbeat(AMQHeartbeatBody::shared_ptr body) = 0;

        void handleBody(AMQBody::shared_ptr& body);
    };

    class UnknownBodyType{
    public:
	const u_int16_t type;
	inline UnknownBodyType(u_int16_t _type) : type(_type){}
    };
}
}


#endif
