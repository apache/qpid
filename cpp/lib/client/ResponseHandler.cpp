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
#include <boost/format.hpp>

#include <ResponseHandler.h>
#include <sys/Monitor.h>
#include <QpidError.h>
#include "amqp_types.h"

using namespace qpid::sys;
using namespace qpid::framing;

namespace qpid {
namespace client {

ResponseHandler::ResponseHandler() : waiting(false){}

ResponseHandler::~ResponseHandler(){}

bool ResponseHandler::validate(ClassId c, MethodId  m) {
    return response != 0 &&
        response->amqpClassId() ==c && response->amqpMethodId() == m;
}

void ResponseHandler::waitForResponse(){
    Monitor::ScopedLock l(monitor);
    while (waiting)
	monitor.wait();
}

void ResponseHandler::signalResponse(
    qpid::framing::AMQMethodBody::shared_ptr _response)
{
    Monitor::ScopedLock l(monitor);
    response = _response;
    waiting = false;
    monitor.notify();
}

void ResponseHandler::receive(ClassId c, MethodId  m) {
    Monitor::ScopedLock l(monitor);
    while (waiting)
	monitor.wait();
    getResponse(); // Check for closed.
    if(!validate(response->amqpClassId(), response->amqpMethodId())) {
	THROW_QPID_ERROR(
            PROTOCOL_ERROR,
            boost::format("Expected class:method %d:%d, got %d:%d")
            % c % m % response->amqpClassId() % response->amqpMethodId());
    }
}

framing::AMQMethodBody::shared_ptr ResponseHandler::getResponse() {
    if (!response) 
        THROW_QPID_ERROR(
            PROTOCOL_ERROR, "Channel closed unexpectedly.");
    return response;
}

RequestId ResponseHandler::getRequestId() {
    assert(response->getRequestId());
    return response->getRequestId();
}
void ResponseHandler::expect(){
    waiting = true;
}

}} // namespace qpid::client
