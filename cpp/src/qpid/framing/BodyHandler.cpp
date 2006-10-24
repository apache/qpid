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
#include "qpid/framing/BodyHandler.h"

using namespace qpid::framing;
using namespace boost;

BodyHandler::~BodyHandler() {}

void BodyHandler::handleBody(AMQBody::shared_ptr& body){

    switch(body->type())
    {

    case METHOD_BODY:
	handleMethod(dynamic_pointer_cast<AMQMethodBody, AMQBody>(body));
	break;
 
   case HEADER_BODY:
	handleHeader(dynamic_pointer_cast<AMQHeaderBody, AMQBody>(body));
	break;

    case CONTENT_BODY:
	handleContent(dynamic_pointer_cast<AMQContentBody, AMQBody>(body));
	break;

    case HEARTBEAT_BODY:
	handleHeartbeat(dynamic_pointer_cast<AMQHeartbeatBody, AMQBody>(body));
	break;

    default:
	throw UnknownBodyType(body->type());
    }

}
