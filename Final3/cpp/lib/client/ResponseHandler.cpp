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
#include <ResponseHandler.h>
#include <sys/Monitor.h>
#include <QpidError.h>

using namespace qpid::sys;

qpid::client::ResponseHandler::ResponseHandler() : waiting(false){}

qpid::client::ResponseHandler::~ResponseHandler(){}

bool qpid::client::ResponseHandler::validate(const qpid::framing::AMQMethodBody& expected){
    return expected.match(response.get());
}

void qpid::client::ResponseHandler::waitForResponse(){
    Monitor::ScopedLock l(monitor);
    if(waiting){
	monitor.wait();
    }
}

void qpid::client::ResponseHandler::signalResponse(qpid::framing::AMQMethodBody::shared_ptr _response){
    response = _response;
    Monitor::ScopedLock l(monitor);
    waiting = false;
    monitor.notify();
}

void qpid::client::ResponseHandler::receive(const qpid::framing::AMQMethodBody& expected){
    Monitor::ScopedLock l(monitor);
    if(waiting){
	monitor.wait();
    }
    if(!validate(expected)){
	THROW_QPID_ERROR(PROTOCOL_ERROR, "Protocol Error");
    }
}

void qpid::client::ResponseHandler::expect(){
    waiting = true;
}
