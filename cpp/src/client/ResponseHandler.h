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
#include "../shared_ptr.h"
#include "../sys/Monitor.h"

#ifndef _ResponseHandler_
#define _ResponseHandler_

namespace qpid {

namespace framing {
class AMQMethodBody;
}

namespace client {

/**
 * Holds a response from the broker peer for the client.
 */ 
class ResponseHandler{
    typedef shared_ptr<framing::AMQMethodBody> MethodPtr;
    bool waiting;
    bool shutdownFlag;
    MethodPtr response;
    sys::Monitor monitor;

  public:
    ResponseHandler();
    ~ResponseHandler();

    /** Is a response expected? */
    bool isWaiting();

    /** Provide a response to the waiting thread */
    void signalResponse(MethodPtr response);

    /** Indicate a message is expected. */
    void expect();

    /** Wait for a response. */
    MethodPtr receive();

    /** Wait for a specific response. */
    MethodPtr receive(framing::ClassId, framing::MethodId);

    /** Template version of receive returns typed pointer. */
    template <class BodyType>
    shared_ptr<BodyType> receive() {
        return shared_polymorphic_downcast<BodyType>(
            receive(BodyType::CLASS_ID, BodyType::METHOD_ID));
    }
};

}}


#endif
