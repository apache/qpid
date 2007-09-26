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

#ifndef _BlockingQueue_
#define _BlockingQueue_

#include <queue>
#include "qpid/sys/Monitor.h"

namespace qpid {
namespace client {

struct QueueClosed {};

template <class T>
class BlockingQueue 
{
    sys::Monitor lock;
    std::queue<T> queue;
    bool closed;

public:

    BlockingQueue() : closed(false) {}

    void reset() 
    {
        sys::Monitor::ScopedLock l(lock);
        closed = true; 
    }

    T pop()
    {
        sys::Monitor::ScopedLock l(lock);
        while (!closed && queue.empty()) {
            lock.wait();
        } 
        if (closed) {
            throw QueueClosed();
        } else {
            T t = queue.front();
            queue.pop();
            return t;
        }
    }

    void push(const T& t)
    {
        sys::Monitor::ScopedLock l(lock);
        bool wasEmpty = queue.empty();
        queue.push(t);
        if (wasEmpty) {
            lock.notifyAll();
        }
    }

    void close()
    {
        sys::Monitor::ScopedLock l(lock);
        closed = true;
        lock.notifyAll();
    }
    
    bool empty()
    {
        sys::Monitor::ScopedLock l(lock);
        return queue.empty();
    }
};

}}



#endif
