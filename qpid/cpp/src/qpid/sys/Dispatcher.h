#ifndef _sys_Dispatcher_h
#define _sys_Dispatcher_h

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

#include "Poller.h"
#include "Runnable.h"
#include "Mutex.h"

#include <memory>
#include <queue>
#include <boost/function.hpp>

#include <assert.h>


namespace qpid {
namespace sys {

class DispatchHandleRef;
class DispatchHandle : public PollerHandle {
    friend class DispatchHandleRef;
public:
    typedef boost::function1<void, DispatchHandle&> Callback;

private:
    Callback readableCallback;
    Callback writableCallback;
    Callback disconnectedCallback;
    Poller::shared_ptr poller;
    Mutex stateLock;
    enum {
        IDLE, INACTIVE, ACTIVE_R, ACTIVE_W, ACTIVE_RW,
        DELAYED_IDLE, DELAYED_INACTIVE, DELAYED_R, DELAYED_W, DELAYED_RW,
        DELAYED_DELETE
    } state;

public:
    DispatchHandle(const IOHandle& h, Callback rCb, Callback wCb, Callback dCb) :
      PollerHandle(h),
      readableCallback(rCb),
      writableCallback(wCb),
      disconnectedCallback(dCb),
      state(IDLE)
    {}

    ~DispatchHandle();

    void startWatch(Poller::shared_ptr poller);
    void rewatch();
    void rewatchRead();
    void rewatchWrite();
    void unwatch();
    void unwatchRead();
    void unwatchWrite();
    void stopWatch();
    
protected:
    void doDelete();

private:
    void processEvent(Poller::EventType dir);
};

class DispatchHandleRef {
    DispatchHandle* ref;

public:
    typedef boost::function1<void, DispatchHandle&> Callback;
    DispatchHandleRef(const IOHandle& h, Callback rCb, Callback wCb, Callback dCb) :
      ref(new DispatchHandle(h, rCb, wCb, dCb))
    {}

    ~DispatchHandleRef() { ref->doDelete(); }

    void startWatch(Poller::shared_ptr poller) { ref->startWatch(poller); }
    void rewatch() { ref->rewatch(); }
    void rewatchRead() { ref->rewatchRead(); }
    void rewatchWrite() { ref->rewatchWrite(); }
    void unwatch() { ref->unwatch(); }
    void unwatchRead() { ref->unwatchRead(); }
    void unwatchWrite() { ref->unwatchWrite(); }
    void stopWatch() { ref->stopWatch(); }
};


class Dispatcher : public Runnable {
    const Poller::shared_ptr poller;

public:
    Dispatcher(Poller::shared_ptr poller);
    ~Dispatcher();
    
    void run();
};

}}

#endif // _sys_Dispatcher_h
