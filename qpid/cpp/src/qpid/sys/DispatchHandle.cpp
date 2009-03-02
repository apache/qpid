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

#include "DispatchHandle.h"

#include <algorithm>

#include <boost/cast.hpp>

#include <assert.h>

namespace qpid {
namespace sys {

DispatchHandle::~DispatchHandle() {
}

void DispatchHandle::startWatch(Poller::shared_ptr poller0) {
    bool r = readableCallback;
    bool w = writableCallback;

    ScopedLock<Mutex> lock(stateLock);
    assert(state == IDLE || state == DELAYED_IDLE);

    // If no callbacks set then do nothing (that is what we were asked to do!)
    // TODO: Maybe this should be an assert instead
    if (!r && !w) {
        switch (state) {
        case IDLE:
            state = INACTIVE;
            return;
        case DELAYED_IDLE:
            state = DELAYED_INACTIVE;
            return;
        default:
            assert(state == IDLE || state == DELAYED_IDLE);    
        }        
    }

    Poller::Direction d = r ?
        (w ? Poller::INOUT : Poller::INPUT) :
        Poller::OUTPUT;

    poller = poller0;
    poller->addFd(*this, d);
    
    switch (state) {
    case IDLE:
        state = r ?
            (w ? ACTIVE_RW : ACTIVE_R) :
            ACTIVE_W;
        return;
    case DELAYED_IDLE:
        state = r ?
            (w ? DELAYED_RW : DELAYED_R) :
            DELAYED_W;
        return;
        default:
            assert(state == IDLE || state == DELAYED_IDLE);            
    }   
}

void DispatchHandle::rewatch() {
    bool r = readableCallback;
    bool w = writableCallback;

    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_R:
    case DELAYED_W:
    case DELAYED_INACTIVE:
        state = r ?
            (w ? DELAYED_RW : DELAYED_R) :
            DELAYED_W;
        break;
    case DELAYED_DELETE:
        break;
    case INACTIVE:
    case ACTIVE_R:
    case ACTIVE_W: {
        assert(poller);
        Poller::Direction d = r ?
            (w ? Poller::INOUT : Poller::INPUT) :
            Poller::OUTPUT;
        poller->modFd(*this, d);
        state = r ?
            (w ? ACTIVE_RW : ACTIVE_R) :
            ACTIVE_W;
        break;
        }
    case DELAYED_RW:
    case ACTIVE_RW:
        // Don't need to do anything already waiting for readable/writable
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
    }
}

void DispatchHandle::rewatchRead() {
    if (!readableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_R:
    case DELAYED_RW:
    case DELAYED_DELETE:
        break;
    case DELAYED_W:
        state = DELAYED_RW;
        break;
    case DELAYED_INACTIVE:
        state = DELAYED_R;
        break;
    case ACTIVE_R:
    case ACTIVE_RW:
        // Nothing to do: already waiting for readable
        break;
    case INACTIVE:
        assert(poller);
        poller->modFd(*this, Poller::INPUT);
        state = ACTIVE_R;
        break;
    case ACTIVE_W:
        assert(poller);
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
    }
}

void DispatchHandle::rewatchWrite() {
    if (!writableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_W:
    case DELAYED_RW:
    case DELAYED_DELETE:
        break;
    case DELAYED_R:
        state = DELAYED_RW;
        break;
    case DELAYED_INACTIVE:
        state = DELAYED_W;
        break;
    case INACTIVE:
        assert(poller);
        poller->modFd(*this, Poller::OUTPUT);
        state = ACTIVE_W;
        break;
    case ACTIVE_R:
        assert(poller);
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        break;
    case ACTIVE_W:
    case ACTIVE_RW:
        // Nothing to do: already waiting for writable
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
   }
}

void DispatchHandle::unwatchRead() {
    if (!readableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_R:
        state = DELAYED_INACTIVE;
        break;
    case DELAYED_RW:
        state = DELAYED_W;    
        break;
    case DELAYED_W:
    case DELAYED_INACTIVE:
    case DELAYED_DELETE:
        break;
    case ACTIVE_R:
        assert(poller);
        poller->modFd(*this, Poller::NONE);
        state = INACTIVE;
        break;
    case ACTIVE_RW:
        assert(poller);
        poller->modFd(*this, Poller::OUTPUT);
        state = ACTIVE_W;
        break;
    case ACTIVE_W:
    case INACTIVE:
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
    }
}

void DispatchHandle::unwatchWrite() {
    if (!writableCallback) {
        return;
    }
    
    ScopedLock<Mutex> lock(stateLock);
    switch(state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_W:
        state = DELAYED_INACTIVE;
        break;
    case DELAYED_RW:
        state = DELAYED_R;
        break;
    case DELAYED_R:
    case DELAYED_INACTIVE:
    case DELAYED_DELETE:
        break;
    case ACTIVE_W:
        assert(poller);
        poller->modFd(*this, Poller::NONE);
        state = INACTIVE;
        break;
    case ACTIVE_RW:
        assert(poller);
        poller->modFd(*this, Poller::INPUT);
        state = ACTIVE_R;
        break;
    case ACTIVE_R:
    case INACTIVE:
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
   }
}

void DispatchHandle::unwatch() {
    ScopedLock<Mutex> lock(stateLock);
    switch (state) {
    case IDLE:
    case DELAYED_IDLE:
        break;
    case DELAYED_R:
    case DELAYED_W:
    case DELAYED_RW:
    case DELAYED_INACTIVE:
        state = DELAYED_INACTIVE;
        break;
    case DELAYED_DELETE:
        break;
    default:
        assert(poller);
        poller->modFd(*this, Poller::NONE);
        state = INACTIVE;
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
    }            
}

void DispatchHandle::stopWatch() {
    ScopedLock<Mutex> lock(stateLock);
    switch (state) {
    case IDLE:
    case DELAYED_IDLE:
    case DELAYED_DELETE:
        return;
    case DELAYED_R:
    case DELAYED_W:
    case DELAYED_RW:
    case DELAYED_INACTIVE:
        state = DELAYED_IDLE;
        break;
    default:
        state = IDLE;
        break;
    case ACTIVE_DELETE:
        assert(state != ACTIVE_DELETE);
    }
    assert(poller);
    poller->delFd(*this);
    poller.reset();
}

// If we are already in the IDLE state we can't do the callback as we might
// race to delete and callback at the same time
// TODO: might be able to fix this by adding a new state, but would make
// the state machine even more complex
void DispatchHandle::call(Callback iCb) {
    assert(iCb);
    ScopedLock<Mutex> lock(stateLock);
    switch (state) {
    case IDLE:
    case ACTIVE_DELETE:
        assert(false);
        return;
    default:
        interruptedCallbacks.push(iCb);
        assert(poller);
        (void) poller->interrupt(*this);
    }
}

// The slightly strange switch structure
// is to ensure that the lock is released before
// we do the delete
void DispatchHandle::doDelete() {
    {
    ScopedLock<Mutex> lock(stateLock);
    // Ensure that we're no longer watching anything
    switch (state) {
    case DELAYED_R:
    case DELAYED_W:
    case DELAYED_RW:
    case DELAYED_INACTIVE:
        assert(poller);
        poller->delFd(*this);
        poller.reset();
        // Fallthrough
    case DELAYED_IDLE:
        state = DELAYED_DELETE;
        // Fallthrough
    case DELAYED_DELETE:
    case ACTIVE_DELETE:
        return;
    case IDLE:
        break;
    default:
        state = ACTIVE_DELETE;
        assert(poller);
        (void) poller->interrupt(*this);
        poller->delFd(*this);
        return;
    }
    }
    // If we're IDLE we can do this right away
    delete this;
}

void DispatchHandle::processEvent(Poller::EventType type) {
    CallbackQueue callbacks;

    // Note that we are now doing the callbacks
    {
    ScopedLock<Mutex> lock(stateLock);
    
    // Set up to wait for same events next time unless reset
    switch(state) {
    case ACTIVE_R:
        state = DELAYED_R;
        break;
    case ACTIVE_W:
        state = DELAYED_W;
        break;
    case ACTIVE_RW:
        state = DELAYED_RW;
        break;
    case ACTIVE_DELETE:
        // Need to make sure we clean up any pending callbacks in this case
        std::swap(callbacks, interruptedCallbacks);
        goto saybyebye;
    // Can get here in idle if we are stopped in a different thread
    // just after we return with this handle in Poller::wait
    case IDLE:
    // Can get here in INACTIVE if a non connection thread unwatches
    // whilst we were stuck in the above lock 
    case INACTIVE:
    // Can only get here in a DELAYED_* state in the rare case
    // that we're already here for reading and we get activated for
    // writing and we can write (it might be possible the other way
    // round too). In this case we're already processing the handle
    // in a different thread in this function so return right away
    case DELAYED_R:
    case DELAYED_W:
    case DELAYED_RW:
    case DELAYED_INACTIVE:
    case DELAYED_IDLE:
    case DELAYED_DELETE:
        return;
    }
    
    std::swap(callbacks, interruptedCallbacks);
    }

    // Do callbacks - whilst we are doing the callbacks we are prevented from processing
    // the same handle until we re-enable it. To avoid rentering the callbacks for a single
    // handle re-enabling in the callbacks is actually deferred until they are complete.
    switch (type) {
    case Poller::READABLE:
        readableCallback(*this);
        break;
    case Poller::WRITABLE:
        writableCallback(*this);
        break;
    case Poller::READ_WRITABLE:
        readableCallback(*this);
        writableCallback(*this);
        break;
    case Poller::DISCONNECTED:
        {
        ScopedLock<Mutex> lock(stateLock);
        state = DELAYED_INACTIVE;
        }
        if (disconnectedCallback) {
            disconnectedCallback(*this);
        }
        break;
    case Poller::INTERRUPTED:
        {
        // We could only be interrupted if we also had a callback to do
        assert(callbacks.size() > 0);
        // We'll actually do the interrupt below
        }
        break;
    default:
        assert(false);
    }

    // If we have any callbacks do them now -
    // (because we use a copy from before the previous callbacks we won't
    //  do anything yet that was just added) 
    while (callbacks.size() > 0) {
        Callback cb = callbacks.front();
        assert(cb);
        cb(*this);
        callbacks.pop();
    }

    {
    ScopedLock<Mutex> lock(stateLock);
    // If any of the callbacks re-enabled reading/writing then actually
    // do it now
    switch (state) {
    case DELAYED_R:
        poller->modFd(*this, Poller::INPUT);
        state = ACTIVE_R;
        return;
    case DELAYED_W:
        poller->modFd(*this, Poller::OUTPUT);
        state = ACTIVE_W;
        return;
    case DELAYED_RW:
        poller->modFd(*this, Poller::INOUT);
        state = ACTIVE_RW;
        return;
    case DELAYED_INACTIVE:
        state = INACTIVE;
        return;
    case DELAYED_IDLE:
    	state = IDLE;
    	return;
    default:
        // This should be impossible
        assert(false);
        return;
    case DELAYED_DELETE:
        break;
    }
    }

saybyebye:
    delete this;
}

}}
