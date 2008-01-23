#ifndef QPID_SYS_BLOCKINGQUEUE_H
#define QPID_SYS_BLOCKINGQUEUE_H

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

#include "Waitable.h"

#include <queue>

namespace qpid {
namespace sys {

/**
 * A simple blocking queue template
 */
template <class T>
class BlockingQueue
{
    mutable sys::Waitable lock;
    std::queue<T> queue;
    bool closed;

public:
    BlockingQueue() : closed(false) {}
    ~BlockingQueue() { close(); }

    /** Block until there is a value to pop */
    T pop()
    {
        Waitable::ScopedLock l(lock);
        if (!queueWait()) throw ClosedException();
        return popInternal();
    }

    /** Non-blocking pop. If there is a value set outValue and return
     * true, else return false;
     */
    bool tryPop(T& outValue) {
        Waitable::ScopedLock l(lock);
        if (queue.empty()) return false;
        outValue = popInternal();
        return true;
    }

    /** Non-blocking pop. If there is a value return it, else return
     * valueIfEmpty.
     */
    T tryPop(const T& valueIfEmpty=T()) {
        T result=valueIfEmpty;
        tryPop(result);
        return result;
    }

    /** Push a value onto the queue */
    void push(const T& t)
    {
        Waitable::ScopedLock l(lock);
        queue.push(t);
        queueNotify(0);
    }

    /**
     * Close the queue. Throws ClosedException in threads waiting in pop().
     * Blocks till all waiting threads have been notified.
     */ 
    void close()
    {
        Waitable::ScopedLock l(lock);
        if (!closed) {
            closed = true;
            lock.notifyAll();
            lock.waitWaiters(); // Ensure no threads are still waiting.
        }
    }

    /** Open a closed queue. */
    void open() {
        Waitable::ScopedLock l(lock);
        closed=false;
    }

    bool isClosed() const { 
        Waitable::ScopedLock l(lock);
        return closed;
    }

    bool isEmpty() const {
        Waitable::ScopedLock l(lock);
        return queue.empty();
    }    

  private:

    void queueNotify(size_t ignore) {
        if (!queue.empty() && lock.hasWaiters()>ignore)
            lock.notify();      // Notify another waiter.
    }

    bool queueWait() {
        Waitable::ScopedWait w(lock);
        while (!closed && queue.empty())
            lock.wait();
        return !queue.empty();
    }

    T popInternal() {
        T t=queue.front();
        queue.pop();
        queueNotify(1);
        return t;
    }
    
};

}}



#endif  /*!QPID_SYS_BLOCKINGQUEUE_H*/
