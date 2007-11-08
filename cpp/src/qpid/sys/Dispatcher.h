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

class Dispatcher;
class DispatchHandle : public PollerHandle {
    friend class Dispatcher;
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
    DispatchHandle(const Socket& s, Callback rCb, Callback wCb, Callback dCb) :
      PollerHandle(s),
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
    void dispatchCallbacks(Poller::EventType dir);
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
