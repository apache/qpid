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
#include <IncomingMessage.h>
#include "framing/AMQHeaderBody.h"
#include "framing/AMQContentBody.h"
#include "BasicGetOkBody.h"
#include "BasicReturnBody.h"
#include "BasicDeliverBody.h"
#include <QpidError.h>
#include <iostream>

namespace qpid {
namespace client {

using namespace sys;
using namespace framing;

struct IncomingMessage::Guard: public Mutex::ScopedLock {
    Guard(IncomingMessage* im) : Mutex::ScopedLock(im->lock) {
        im->shutdownError.throwIf();
    }
};

IncomingMessage::IncomingMessage() { reset(); }

void IncomingMessage::reset() {
    state = &IncomingMessage::expectRequest;
    endFn= &IncomingMessage::endRequest;
    buildMessage = Message();
}
    
void IncomingMessage::startGet() {
    Guard g(this);
    if (state != &IncomingMessage::expectRequest) {
        endGet(new QPID_ERROR(CLIENT_ERROR, "Message already in progress."));
    }
    else {
        state = &IncomingMessage::expectGetOk;
        endFn = &IncomingMessage::endGet;
        getError.reset();
        getState = GETTING;
    }
}

bool IncomingMessage::waitGet(Message& msg) {
    Guard g(this);
    while (getState == GETTING && !shutdownError && !getError)
        getReady.wait(lock);
    shutdownError.throwIf();
    getError.throwIf();
    msg = getMessage;
    return getState==GOT;
}

Message IncomingMessage::waitDispatch() {
    Guard g(this);
    while(dispatchQueue.empty() && !shutdownError)
        dispatchReady.wait(lock);
    shutdownError.throwIf();

    Message msg(dispatchQueue.front());
    dispatchQueue.pop();
    return msg;
}

void IncomingMessage::add(BodyPtr body) {
    Guard g(this);
    shutdownError.throwIf();
    // Call the current state function.
    (this->*state)(body);
}

void IncomingMessage::shutdown() {
    Mutex::ScopedLock l(lock);
    shutdownError.reset(new ShutdownException());
    getReady.notify();
    dispatchReady.notify();
}

bool IncomingMessage::isShutdown() const {
    Mutex::ScopedLock l(lock);
    return shutdownError;
}

// Common check for all the expect functions. Called in network thread.
template<class T>
boost::shared_ptr<T> IncomingMessage::expectCheck(BodyPtr body) {
    boost::shared_ptr<T> ptr = boost::dynamic_pointer_cast<T>(body);
    if (!ptr) 
        throw QPID_ERROR(PROTOCOL_ERROR+504, "Unexpected frame type");
    return ptr;
}

void IncomingMessage::expectGetOk(BodyPtr body) {
    if (dynamic_cast<BasicGetOkBody*>(body.get()))
        state = &IncomingMessage::expectHeader;
    else if (dynamic_cast<BasicGetEmptyBody*>(body.get())) {
        getState = EMPTY;
        endGet();
    }
    else
        throw QPID_ERROR(PROTOCOL_ERROR+504, "Unexpected frame type");
}

void IncomingMessage::expectHeader(BodyPtr body) {
    AMQHeaderBody::shared_ptr header = expectCheck<AMQHeaderBody>(body);
    buildMessage.header = header;
    state = &IncomingMessage::expectContent;
    checkComplete();
}

void IncomingMessage::expectContent(BodyPtr body) {
    AMQContentBody::shared_ptr content = expectCheck<AMQContentBody>(body);
    buildMessage.setData(buildMessage.getData() + content->getData());
    checkComplete();
}

void IncomingMessage::checkComplete() {
    size_t declaredSize = buildMessage.header->getContentSize();
    size_t currentSize = buildMessage.getData().size();
    if (declaredSize == currentSize)
        (this->*endFn)(0);
    else if (declaredSize < currentSize)
        (this->*endFn)(new QPID_ERROR(
                  PROTOCOL_ERROR, "Message content exceeds declared size."));
}

void IncomingMessage::expectRequest(BodyPtr body) {
    AMQMethodBody::shared_ptr method = expectCheck<AMQMethodBody>(body);
    buildMessage.setMethod(method);
    state = &IncomingMessage::expectHeader;
}
    
void IncomingMessage::endGet(Exception* ex) {
    getError.reset(ex);
    if (getState == GETTING) {
        getMessage = buildMessage;
        getState = GOT;
    }
    reset();
    getReady.notify();
}

void IncomingMessage::endRequest(Exception* ex) {
    ExceptionHolder eh(ex);
    if (!eh) {
        dispatchQueue.push(buildMessage);
        reset();
        dispatchReady.notify();
    }
    eh.throwIf();
}

}} // namespace qpid::client
