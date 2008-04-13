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
#include <framing/amqp_framing.h>
#include <sys/Monitor.h>

#ifndef _ResponseHandler_
#define _ResponseHandler_

namespace qpid {
    namespace client {

        class ResponseHandler{
            bool waiting;
            qpid::framing::AMQMethodBody::shared_ptr response;
            qpid::sys::Monitor monitor;

        public:
            ResponseHandler();
            ~ResponseHandler();
            inline bool isWaiting(){ return waiting; }
            inline qpid::framing::AMQMethodBody::shared_ptr getResponse(){ return response; }
            bool validate(const qpid::framing::AMQMethodBody& expected);
            void waitForResponse();
            void signalResponse(qpid::framing::AMQMethodBody::shared_ptr response);
            void receive(const qpid::framing::AMQMethodBody& expected);
            void expect();//must be called before calling receive
        };

    }
}


#endif
