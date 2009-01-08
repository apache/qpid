#ifndef _sys_DispatchHandle_h
#define _sys_DispatchHandle_h

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
#include "Mutex.h"

#include <boost/function.hpp>


namespace qpid {
namespace sys {

class DispatchHandleRef;
/**
 * In order to have your own handle (file descriptor on Unix) watched by the poller
 * you need to:
 * 
 * - Subclass IOHandle, in the constructor supply an appropriate
 *    IOHandlerPrivate object for the platform.
 *    
 * - Construct a DispatchHandle passing it your IOHandle and 
 *   callback functions for read, write and disconnect events.
 *
 * - Ensure the DispatchHandle is not deleted until the poller is no longer using it.
 *   TODO: astitcher document DispatchHandleRef to simplify this.
 *   
 * When an event occurs on the handle, the poller calls the relevant callback and
 * stops watching that handle. Your callback can call rewatch() or related functions
 * to re-enable polling.
 */
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
    /**
     * Provide a handle to poll and a set of callbacks.  Note
     * callbacks can be 0, meaning you are not interested in that
     * event.
     * 
     *@param h: the handle to watch. The IOHandle encapsulates a
     * platfrom-specific handle to an IO object (e.g. a file descriptor
     * on Unix.)
     *@param rCb Callback called when the handle is readable.
     *@param wCb Callback called when the handle is writable.
     *@param dCb Callback called when the handle is disconnected.
     */
    DispatchHandle(const IOHandle& h, Callback rCb, Callback wCb, Callback dCb) :
      PollerHandle(h),
      readableCallback(rCb),
      writableCallback(wCb),
      disconnectedCallback(dCb),
      state(IDLE)
    {}

    ~DispatchHandle();

    /** Add this DispatchHandle to the poller to be watched. */
    void startWatch(Poller::shared_ptr poller);

    /** Resume watchingn for all non-0 callbacks. */
    void rewatch();
    /** Resume watchingn for read only. */
    void rewatchRead();

    /** Resume watchingn for write only. */
    void rewatchWrite();

    /** Stop watching temporarily. The DispatchHandle remains
        associated with the poller and can be re-activated using
        rewatch. */
    void unwatch();
    /** Stop watching for read */
    void unwatchRead();
    /** Stop watching for write */
    void unwatchWrite();

    /** Stop watching permanently. Disassociates from the poller. */
    void stopWatch();
    
protected:
    /** Override to get extra processing done when the DispatchHandle is deleted. */
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

}}

#endif // _sys_DispatchHandle_h
