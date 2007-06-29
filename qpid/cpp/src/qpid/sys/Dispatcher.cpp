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
        DispatchHandle* h = static_cast<DispatchHandle*>(event.handle);

        // If can read/write then dispatch appropriate callbacks        
        if (h) {
            h->dispatchCallbacks(event.dir);
        } else {
            // Handle shutdown
            switch (event.dir) {
            case Poller::SHUTDOWN:
                goto dispatcher_shutdown;
            default:
                // This should be impossible
                assert(false);
            }
        }
    } while (true);
    
dispatcher_shutdown:
    ;
}

void DispatchHandle::watch(Poller::shared_ptr poller0) {
    bool r = readableCallback;
    bool w = writableCallback;

    ScopedLock<Mutex> lock(stateLock);
    assert(state == IDLE);

    // If no callbacks set then do nothing (that is what we were asked to do!)
    // TODO: Maybe this should be an assert instead
    if (!r && !w) {
        state = INACTIVE;
        return;
    }

    Poller::Direction d = r ?
        (w ? Poller::INOUT : Poller::IN) :
        Poller::OUT;

    poller = poller0;
    poller->addFd(*this, d);
    
    state = r ?
        (w ? ACTIVE_RW : ACTIVE_R) :
        ACTIVE_W;
}

void DispatchHandle::rewatch() {
    assert(poller);
    bool r = readableCallback;
    bool w = writableCallback;

    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case DispatchHandle::IDLE:
        assert(false);
        break;
    case DispatchHandle::DELAYED_R:
    case DispatchHandle::DELAYED_W:
    case DispatchHandle::CALLBACK:
        state = r ?
            (w ? DELAYED_RW : DELAYED_R) :
            DELAYED_W;
        break;
    case DispatchHandle::INACTIVE:
    case DispatchHandle::ACTIVE_R:
    case DispatchHandle::ACTIVE_W: {
        Poller::Direction d = r ?
            (w ? Poller::INOUT : Poller::IN) :
            Poller::OUT;
        poller->modFd(*this, d);
        state = r ?
            (w ? ACTIVE_RW : ACTIVE_R) :
            ACTIVE_W;
        break;
        }
    case DispatchHandle::DELAYED_RW:
    case DispatchHandle::ACTIVE_RW:
        // Don't need to do anything already waiting for readable/writable
        break;
    }
}

void DispatchHandle::rewatchRead() {
    assert(poller);
    if (!readableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case DispatchHandle::IDLE:
        assert(false);
        break;
    case DispatchHandle::DELAYED_R:
    case DispatchHandle::DELAYED_RW:
        break;
    case DispatchHandle::DELAYED_W:
        state = DELAYED_RW;
        break;
    case DispatchHandle::CALLBACK:
        state = DELAYED_R;
        break;
    case DispatchHandle::ACTIVE_R:
    case DispatchHandle::ACTIVE_RW:
        // Nothing to do: already wating for readable
        break;
    case DispatchHandle::INACTIVE:
        poller->modFd(*this, Poller::IN);
        state = ACTIVE_R;
        break;
    case DispatchHandle::ACTIVE_W:
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        break;
    }
}

void DispatchHandle::rewatchWrite() {
    assert(poller);
    if (!writableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case DispatchHandle::IDLE:
        assert(false);
        break;
    case DispatchHandle::DELAYED_W:
    case DispatchHandle::DELAYED_RW:
        break;
    case DispatchHandle::DELAYED_R:
        state = DELAYED_RW;
        break;
    case DispatchHandle::CALLBACK:
        state = DELAYED_W;
        break;
    case DispatchHandle::INACTIVE:
        poller->modFd(*this, Poller::OUT);
        state = ACTIVE_W;
        break;
    case DispatchHandle::ACTIVE_R:
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        break;
    case DispatchHandle::ACTIVE_W:
    case DispatchHandle::ACTIVE_RW:
        // Nothing to do: already waiting for writable
        break;
   }
}

void DispatchHandle::unwatch() {
    assert(poller);
    ScopedLock<Mutex> lock(stateLock);
    poller->delFd(*this);
    poller.reset();
    state = IDLE;
}

void DispatchHandle::dispatchCallbacks(Poller::Direction dir) {
    // Note that we are now doing the callbacks
    {
    ScopedLock<Mutex> lock(stateLock);
    assert(
        state == ACTIVE_R ||
        state == ACTIVE_W ||
        state == ACTIVE_RW);

    state = CALLBACK;
    }

    // Do callbacks - whilst we are doing the callbacks we are prevented from processing
    // the same handle until we re-enable it. To avoid rentering the callbacks for a single
    // handle re-enabling in the callbacks is actually deferred until they are complete.
    switch (dir) {
    case Poller::IN:
        readableCallback(*this);
        break;
    case Poller::OUT:
        writableCallback(*this);
        break;
    case Poller::INOUT:
        readableCallback(*this);
        writableCallback(*this);
        break;
    default:
        assert(false);
    }

    // If any of the callbacks re-enabled reading/writing then actually
    // do it now
    ScopedLock<Mutex> lock(stateLock);
    switch (state) {
    case DELAYED_R:
        poller->modFd(*this, Poller::IN);
        state = ACTIVE_R;
        break;
    case DELAYED_W:
        poller->modFd(*this, Poller::OUT);
        state = ACTIVE_W;
        break;
    case DELAYED_RW:
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        break;
    case CALLBACK:
        state = INACTIVE;
        break;
    default:
        // This should be impossible
        assert(false);
    }            
}

}}
