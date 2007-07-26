#ifndef QPID_SYS_CONCURRENTQUEUE_H
#define QPID_SYS_CONCURRENTQUEUE_H

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

#include "qpid/sys/Monitor.h"
#include "qpid/sys/ScopedIncrement.h"

#include <boost/bind.hpp>

#include <deque>

namespace qpid {
namespace sys {

/**
 * Thread-safe queue that allows threads to push items onto
 * the queue concurrently with threads popping items off the
 * queue.
 *
 * Also allows consuming threads to wait until an item is available.
 */
template <class T> class ConcurrentQueue {
  public:
    ConcurrentQueue() : waiters(0), shutdown(false) {}

    /** Threads in wait() are woken with ShutdownException before
     * destroying the queue.
     */
    ~ConcurrentQueue() {
        Mutex::ScopedLock l(lock);
        shutdown = true;
        lock.notifyAll();
        while (waiters > 0)
            lock.wait();
    }
    
    /** Push a data item onto the back of the queue */
    void push(const T& data) {
        Mutex::ScopedLock l(lock);
        queue.push_back(data);
    }

    /** If the queue is non-empty, pop the front item into data and
     * return true. If the queue is empty, return false
     */
    bool pop(T& data) {
        Mutex::ScopedLock l(lock);
        return popInternal(data);
    }

    /** Wait up to deadline for a data item to be available.
     *@return true if data was available, false if timed out.
     *@throws ShutdownException if the queue is destroyed.
     */
    bool waitPop(T& data, Duration timeout) {
        Mutex::ScopedLock l(lock);
        ScopedIncrement<size_t> w(
            waiters, boost::bind(&ConcurrentQueue::noWaiters, this));
        AbsTime deadline(now(), timeout);
        while (queue.empty() && lock.wait(deadline))
            ;
        return popInternal(data);
    }

  private:
    
    bool popInternal(T& data) {
        if (shutdown) 
            throw ShutdownException();
        if (queue.empty())
            return false;
        else {
            data = queue.front();
            queue.pop_front();
            return true;
        }
    }
    
    void noWaiters() {
        assert(waiters == 0);
        if (shutdown)
            lock.notify();  // Notify dtor thread.
    }
        
    Monitor lock;
    std::deque<T> queue;
    size_t waiters;
    bool shutdown;
};

}} // namespace qpid::sys


#endif  /*!QPID_SYS_CONCURRENTQUEUE_H*/
