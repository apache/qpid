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

#include "qpid/RefCounted.h"
#include <boost/intrusive_ptr.hpp>

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

class AsyncCompletion : public virtual RefCounted
{
 public:

    /** Supplied by the Initiator to the end() method, allows for a callback
     * when all outstanding completers are done.  If the callback cannot be
     * made during the end() call, the clone() method must supply a copy of
     * this callback object that persists after end() returns.  The cloned
     * callback object will be used by the last completer thread, and
     * released when the callback returns.
     */
    class Callback : public RefCounted
    {
      public:
        // Normally RefCounted objects cannot be copied.
        // Allow Callback objects to be copied (by subclasses implementing clone())
        // The copy has an initial refcount of 0
        Callback(const Callback&) : RefCounted() {}
        Callback() {}

        virtual void completed(bool) = 0;
        virtual boost::intrusive_ptr<Callback> clone() = 0;
    };

 private:
    mutable qpid::sys::AtomicValue<uint32_t> completionsNeeded;
    mutable qpid::sys::Monitor callbackLock;
    bool inCallback, active;

    void invokeCallback(bool sync) {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        if (active) {
            if (callback.get()) {
                boost::intrusive_ptr<Callback> save = callback;
                callback = boost::intrusive_ptr<Callback>(); // Nobody else can run callback.
                inCallback = true;
                {
                    qpid::sys::Mutex::ScopedUnlock ul(callbackLock);
                    save->completed(sync);
                }
                inCallback = false;
                callbackLock.notifyAll();
            }
            active = false;
        }
    }

 protected:
    /** Invoked when all completers have signalled that they have completed
     * (via calls to finishCompleter()). bool == true if called via end()
     */
    boost::intrusive_ptr<Callback> callback;

 public:
    AsyncCompletion() : completionsNeeded(0), inCallback(false), active(true) {};
    virtual ~AsyncCompletion() { cancel(); }


    /** True when all outstanding operations have compeleted
     */
    bool isDone()
    {
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
        ++completionsNeeded;
    }

    /** called by initiator after all potential completers have called
     * startCompleter().
     */
    void end(Callback& cb)
    {
        assert(completionsNeeded.get() > 0);    // ensure begin() has been called!
        // the following only "decrements" the count if it is 1.  This means
        // there are no more outstanding completers and we are done.
        if (completionsNeeded.boolCompareAndSwap(1, 0)) {
            // done!  Complete immediately
            cb.completed(true);
            return;
        }

        // the compare-and-swap did not succeed.  This means there are
        // outstanding completers pending (count > 1).  Get a persistent
        // Callback object to use when the last completer is done.
        // Decrement after setting up the callback ensures that pending
        // completers cannot touch the callback until it is ready.
        callback = cb.clone();
        if (--completionsNeeded == 0) {
            // note that a completer may have completed during the
            // callback setup or decrement:
            invokeCallback(true);
        }
    }

    /** may be called by Initiator to cancel the callback.  Will wait for
     * callback to complete if in progress.
     */
    virtual void cancel() {
        qpid::sys::Mutex::ScopedLock l(callbackLock);
        while (inCallback) callbackLock.wait();
        callback = boost::intrusive_ptr<Callback>();
        active = false;
    }
};

}}  // qpid::broker::
#endif  /*!_AsyncCompletion_*/
