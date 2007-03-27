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
#include <QpidError.h>
#include <boost/format.hpp>
#include "ResponseHandler.h"
#include "AMQMethodBody.h"

using namespace qpid::sys;
using namespace qpid::framing;

namespace qpid {
namespace client {

ResponseHandler::ResponseHandler() : waiting(false), shutdownFlag(false) {}

ResponseHandler::~ResponseHandler(){}

bool ResponseHandler::isWaiting()  {
    Monitor::ScopedLock l(monitor);
    return waiting;
}

void ResponseHandler::expect(){
    Monitor::ScopedLock l(monitor);
    waiting = true;
}

void ResponseHandler::signalResponse(MethodPtr _response)
{
    Monitor::ScopedLock l(monitor);
    response = _response;
    if (!response)
        shutdownFlag=true;
    waiting = false;
    monitor.notify();
}

ResponseHandler::MethodPtr ResponseHandler::receive() {
    Monitor::ScopedLock l(monitor);
    while (!response && !shutdownFlag)
	monitor.wait();
    if (shutdownFlag) 
        THROW_QPID_ERROR(
            PROTOCOL_ERROR, "Channel closed unexpectedly.");
    MethodPtr result = response;
    response.reset();
    return result;
}

ResponseHandler::MethodPtr ResponseHandler::receive(ClassId c, MethodId  m) {
    MethodPtr response = receive();
    if(c != response->amqpClassId() || m != response->amqpMethodId()) {
	THROW_QPID_ERROR(
            PROTOCOL_ERROR,
            boost::format("Expected class:method %d:%d, got %d:%d")
            % c % m % response->amqpClassId() % response->amqpMethodId());
    }
    return response;
}

}} // namespace qpid::client
