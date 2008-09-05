#ifndef QPID_CLUSTER_POLLABLEQUEUE_H
#define QPID_CLUSTER_POLLABLEQUEUE_H

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

#include "qpid/cluster/PollableCondition.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Mutex.h"
#include <boost/function.hpp>
#include <boost/bind.hpp>
#include <deque>

namespace qpid {

namespace sys { class Poller; }

namespace cluster {

// FIXME aconway 2008-08-11: this could be of more general interest,
// move to common lib.

/**
 * A queue that can be polled by sys::Poller.  Any thread can push to
 * the queue, on wakeup the poller thread processes all items on the
 * queue by passing them to a callback in a batch.
 */
template <class T>
class PollableQueue {
    typedef std::deque<T> Queue;

  public:
    typedef typename Queue::iterator iterator;
    
    /** Callback to process a range of items. */
    typedef boost::function<void (const iterator&, const iterator&)> Callback;

    /** When the queue is selected by the poller, values are passed to callback cb. */
    explicit PollableQueue(const Callback& cb);

    /** Push a value onto the queue. Thread safe */
    void push(const T& t) { ScopedLock l(lock); queue.push_back(t); condition.set(); }

    /** Start polling. */ 
    void start(const boost::shared_ptr<sys::Poller>& poller) { handle.startWatch(poller); }

    /** Stop polling. */
    void stop() { handle.stopWatch(); }
    
  private:
    typedef sys::Mutex::ScopedLock ScopedLock;
    typedef sys::Mutex::ScopedUnlock ScopedUnlock;

    void dispatch(sys::DispatchHandle&);
    
    sys::Mutex lock;
    Callback callback;
    PollableCondition condition;
    sys::DispatchHandle handle;
    Queue queue;
    Queue batch;
};

template <class T> PollableQueue<T>::PollableQueue(const Callback& cb) // FIXME aconway 2008-08-12: 
    : callback(cb),
      handle(condition, boost::bind(&PollableQueue<T>::dispatch, this, _1), 0, 0)
{}

template <class T> void PollableQueue<T>::dispatch(sys::DispatchHandle& h) {
    ScopedLock l(lock);         // Lock for concurrent push() 
    batch.clear();
    batch.swap(queue);
    condition.clear();
    {
        // Process outside the lock to allow concurrent push.
        ScopedUnlock u(lock);
        callback(batch.begin(), batch.end()); 
        h.rewatch();
    }
    batch.clear();
}

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_POLLABLEQUEUE_H*/
