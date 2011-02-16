#ifndef _AsyncCompletion_
#define _AsyncCompletion_

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

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/sys/AtomicValue.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Monitor.h"

namespace qpid {
namespace broker {

/**
 * Class to implement asynchronous notification of completion.
 *
 * Use-case: An "initiator" needs to wait for a set of "completers" to
 * finish a unit of work before an action can occur.  This object
 * tracks the progress of the set of completers, and allows the action
 * to occur once all completers have signalled that they are done.
 *
 * The initiator and completers may be running in separate threads.
 *
 * The initiating thread is the thread that initiates the action,
 * i.e. the connection read thread.
 *
 * A completing thread is any thread that contributes to completion,
 * e.g. a store thread that does an async write.
 * There may be zero or more completers.
 *
 * When the work is complete, a callback is invoked.  The callback
 * may be invoked in the Initiator thread, or one of the Completer
 * threads. The callback is passed a flag indicating whether or not
 * the callback is running under the context of the Initiator thread.
 *
 * Use model:
 * 1) Initiator thread invokes begin()
 * 2) After begin() has been invoked, zero or more Completers invoke
 * startCompleter().  Completers may be running in the same or
 * different thread as the Initiator, as long as they guarantee that
 * startCompleter() is invoked at least once before the Initiator invokes end().
 * 3) Completers may invoke finishCompleter() at any time, even after the
 * initiator has invoked end().  finishCompleter() may be called from any
 * thread.
 * 4) startCompleter()/finishCompleter() calls "nest": for each call to
 * startCompleter(), a corresponding call to finishCompleter() must be made.
 * Once the last finishCompleter() is called, the Completer must no longer
 * reference the completion object.
 * 5) The Initiator invokes end() at the point where it has finished
 * dispatching work to the Completers, and is prepared for the callback
 * handler to be invoked. Note: if there are no outstanding Completers
 * pending when the Initiator invokes end(), the callback will be invoked
 * directly, and the sync parameter will be set true. This indicates to the
 * Initiator that the callback is executing in the context of the end() call,
 * and the Initiator is free to optimize the handling of the completion,
 * assuming no need for synchronization with Completer threads.
 */

class AsyncCompletion
{
 private:
    mutable qpid::sys::AtomicValue<uint32_t> completionsNeeded;
    mutable qpid::sys::Monitor callbackLock;
    bool inCallback, active;

    void invokeCallback(bool sync) {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        if (active) {
            inCallback = true;
            {
                qpid::sys::Mutex::ScopedUnlock ul(callbackLock);
                completed(sync);
            }
            inCallback = false;
            active = false;
            callbackLock.notifyAll();
        }
    }

 protected:
    /** Invoked when all completers have signalled that they have completed
     * (via calls to finishCompleter()). bool == true if called via end()
     */
    virtual void completed(bool) = 0;

 public:
    AsyncCompletion() : completionsNeeded(0), inCallback(false), active(true) {};
    virtual ~AsyncCompletion() { cancel(); }

    /** True when all outstanding operations have compeleted
     */
    bool isDone()
    {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        return !active;
    }

    /** Called to signal the start of an asynchronous operation.  The operation
     * is considered pending until finishCompleter() is called.
     * E.g. called when initiating an async store operation.
     */
    void startCompleter() { ++completionsNeeded; }

    /** Called by completer to signal that it has finished the operation started
     * when startCompleter() was invoked.
     * e.g. called when async write complete.
     */
    void finishCompleter()
    {
        if (--completionsNeeded == 0) {
            invokeCallback(false);
        }
    }

    /** called by initiator before any calls to startCompleter can be done.
     */
    void begin()
    {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        ++completionsNeeded;
    }

    /** called by initiator after all potential completers have called
     * startCompleter().
     */
    void end()
    {
        assert(completionsNeeded.get() > 0);    // ensure begin() has been called!
        if (--completionsNeeded == 0) {
            invokeCallback(true);
        }
    }

    /** may be called by Initiator to cancel the callback.  Will wait for
     * callback to complete if in progress.
     */
    virtual void cancel() {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        while (inCallback) callbackLock.wait();
        active = false;
    }

    /** may be called by Initiator after all completers have been added but
     * prior to calling end().  Allows initiator to determine if it _really_
     * needs to wait for pending Completers (e.g. count > 1).
     */
    //uint32_t getPendingCompleters() { return completionsNeeded.get(); }
};

}}  // qpid::broker::
#endif  /*!_AsyncCompletion_*/
