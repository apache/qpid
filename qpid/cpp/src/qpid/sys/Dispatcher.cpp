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

#include "Dispatcher.h"

#include <assert.h>

namespace qpid {
namespace sys {

Dispatcher::Dispatcher(Poller::shared_ptr poller0) :
  poller(poller0) {
}

Dispatcher::~Dispatcher() {
}
    
void Dispatcher::run() {
    do {
        Poller::Event event = poller->wait();
        // Poller::wait guarantees to return an event
        DispatchHandle* h = static_cast<DispatchHandle*>(event.handle);
        switch (event.dir) {
        case Poller::IN:
            h->readableCallback(*h);
            break;
        case Poller::OUT:
            h->writableCallback(*h);
            break;
        case Poller::INOUT:
            h->readableCallback(*h);
            h->writableCallback(*h);
            break;
        case Poller::SHUTDOWN:
            goto dispatcher_shutdown;
        default:
            ;
        }
    } while (true);
    
dispatcher_shutdown:
    ;
}

void DispatchHandle::watch(Poller::shared_ptr poller0) {
    bool r = readableCallback;
    bool w = writableCallback;
    
    // If no callbacks set then do nothing (that is what we were asked to do!)
    // TODO: Maybe this should be an assert instead
    if (!r && !w)
        return;

    Poller::Direction d = r ?
        (w ? Poller::INOUT : Poller::IN) :
        Poller::OUT;

    poller = poller0;
    poller->addFd(*this, d);
}

void DispatchHandle::rewatch() {
    assert(poller);
    poller->rearmFd(*this);
}

void DispatchHandle::unwatch() {
    poller->delFd(*this);
    poller.reset();
}

}}
