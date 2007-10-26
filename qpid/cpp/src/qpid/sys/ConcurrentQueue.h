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

#include "qpid/sys/Waitable.h"
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
template <class T> class ConcurrentQueue : public Waitable {
  public:
    struct ShutdownException {};
    
    ConcurrentQueue() : shutdownFlag(false) {}

    /** Waiting threads are notified by ~Waitable */
    ~ConcurrentQueue() { shutdown(); }

    bool shutdown(bool wait=true) {
        ScopedLock l(lock);
        if (!shutdownFlag) {
            shutdownFlag=true;
            lock.notifyAll();
            if (wait) lock.waitAll();
            shutdownFlag=true;
            return true;
        }
        return false;
    }

    /** Push a data item onto the back of the queue */
    void push(const T& data) {
        Mutex::ScopedLock l(lock);
        queue.push_back(data);
        lock.notify();
    }

    /** If the queue is non-empty, pop the front item into data and
     * return true. If the queue is empty, return false
     */
    bool tryPop(T& data) {
        Mutex::ScopedLock l(lock);
        if (shutdownFlag || queue.empty())
            return false;
        data = queue.front();
        queue.pop_front();
        return true;
    }

    /** Wait up to a timeout for a data item to be available.
     *@return true if data was available, false if timed out or shut down.
     *@throws ShutdownException if the queue is destroyed.
     */
    bool waitPop(T& data, Duration timeout=TIME_INFINITE) {
        ScopedLock l(lock);
        AbsTime deadline(now(), timeout);
        {
            ScopedWait(*this);
            while (!shutdownFlag && queue.empty())
                if (!lock.wait(deadline))
                    return false;
        }
        if (queue.empty())
            return false;
        data = queue.front();
        queue.pop_front();
        return true;
    }

    bool isShutdown() { ScopedLock l(lock); return shutdownFlag; }
    
  protected:
    Waitable lock;
  private:
    std::deque<T> queue;
    bool shutdownFlag;
};

}} // namespace qpid::sys


#endif  /*!QPID_SYS_CONCURRENTQUEUE_H*/
