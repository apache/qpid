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
#include "qpid/client/ResponseHandler.h"
#include "qpid/sys/Monitor.h"
#include "qpid/QpidError.h"

qpid::client::ResponseHandler::ResponseHandler() : waiting(false){
    monitor = new qpid::sys::Monitor();
}

qpid::client::ResponseHandler::~ResponseHandler(){
    delete monitor;
}

bool qpid::client::ResponseHandler::validate(const qpid::framing::AMQMethodBody& expected){
    return expected.match(response.get());
}

void qpid::client::ResponseHandler::waitForResponse(){
    monitor->acquire();
    if(waiting){
	monitor->wait();
    }
    monitor->release();
}

void qpid::client::ResponseHandler::signalResponse(qpid::framing::AMQMethodBody::shared_ptr _response){
    response = _response;
    monitor->acquire();
    waiting = false;
    monitor->notify();
    monitor->release();
}

void qpid::client::ResponseHandler::receive(const qpid::framing::AMQMethodBody& expected){
    monitor->acquire();
    if(waiting){
	monitor->wait();
    }
    monitor->release();
    if(!validate(expected)){
	THROW_QPID_ERROR(PROTOCOL_ERROR, "Protocol Error");
    }
}

void qpid::client::ResponseHandler::expect(){
    waiting = true;
}
