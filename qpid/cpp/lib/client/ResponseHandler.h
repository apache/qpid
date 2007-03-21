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

#include <framing/amqp_framing.h> // FIXME aconway 2007-02-01: #include cleanup.
#include <sys/Monitor.h>

#ifndef _ResponseHandler_
#define _ResponseHandler_

namespace qpid {
namespace client {

/**
 * Holds a response from the broker peer for the client.
 */ 
class ResponseHandler{
    bool waiting;
    qpid::framing::AMQMethodBody::shared_ptr response;
    qpid::sys::Monitor monitor;

  public:
    ResponseHandler();
    ~ResponseHandler();
    
    bool isWaiting(){ return waiting; }
    framing::AMQMethodBody::shared_ptr getResponse();
    void waitForResponse();
    
    void signalResponse(framing::AMQMethodBody::shared_ptr response);

    void expect();//must be called before calling receive
    bool validate(framing::ClassId, framing::MethodId);
    void receive(framing::ClassId, framing::MethodId);

    framing::RequestId getRequestId();

    template <class BodyType> bool validate() {
        return validate(BodyType::CLASS_ID, BodyType::METHOD_ID);
    }
    template <class BodyType> void receive() {
        receive(BodyType::CLASS_ID, BodyType::METHOD_ID);
    }
};

}
}


#endif
