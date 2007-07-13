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

#include "qpid/sys/Mutex.h"

#include <deque>

namespace qpid {
namespace sys {

/**
 * Thread-safe queue that allows threads to push items onto
 * the queue concurrently with threads popping items off the
 * queue.
 */
template <class T> class ConcurrentQueue {
  public:
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
        if (queue.empty())
            return false;
        else {
            data = queue.front();
            queue.pop_front();
            return true;
        }
    }
    
  private:
    Mutex lock;
    std::deque<T> queue;
};

}} // namespace qpid::sys


#endif  /*!QPID_SYS_CONCURRENTQUEUE_H*/
