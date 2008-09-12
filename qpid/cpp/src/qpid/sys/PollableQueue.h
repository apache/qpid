#ifndef QPID_SYS_POLLABLEQUEUE_H
#define QPID_SYS_POLLABLEQUEUE_H

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

#include "qpid/sys/PollableCondition.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Monitor.h"
#include <boost/function.hpp>
#include <boost/bind.hpp>
#include <algorithm>
#include <deque>

namespace qpid {
namespace sys {

class Poller;

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

    /** @see forEach() */
    template <class F> struct ForEach {
        F handleOne;
        ForEach(const F& f) : handleOne(f) {}
        void operator()(const iterator& i, const iterator& j) const { std::for_each(i, j, handleOne); }
    };

    /** Create a range callback from a functor that processes a single item. */
    template <class F> static ForEach<F> forEach(const F& f) { return ForEach<F>(f); }
    
    /** When the queue is selected by the poller, values are passed to callback cb. */
    explicit PollableQueue(const Callback& cb);

    /** Push a value onto the queue. Thread safe */
    void push(const T& t);

    /** Start polling. */ 
    void start(const boost::shared_ptr<sys::Poller>& poller);

    /** Stop polling and wait for the current callback, if any, to complete. */
    void stop();
    
  private:
    typedef sys::Monitor::ScopedLock ScopedLock;
    typedef sys::Monitor::ScopedUnlock ScopedUnlock;

    void dispatch(sys::DispatchHandle&);
    
    sys::Monitor lock;
    Callback callback;
    PollableCondition condition;
    sys::DispatchHandle handle;
    Queue queue;
    Queue batch;
    bool dispatching, stopped;
};

template <class T> PollableQueue<T>::PollableQueue(const Callback& cb) // FIXME aconway 2008-08-12: 
    : callback(cb),
      handle(condition, boost::bind(&PollableQueue<T>::dispatch, this, _1), 0, 0),
      dispatching(false), stopped(true)
{}

template <class T> void PollableQueue<T>::start(const boost::shared_ptr<sys::Poller>& poller) {
    ScopedLock l(lock);
    stopped = false;
    handle.startWatch(poller);
}

template <class T> void PollableQueue<T>::push(const T& t) {
    ScopedLock l(lock);
    queue.push_back(t);
    condition.set();
}

template <class T> void PollableQueue<T>::dispatch(sys::DispatchHandle& h) {
    ScopedLock l(lock);
    if (stopped) return;
    dispatching = true;
    condition.clear();
    batch.clear();
    batch.swap(queue);          // Snapshot of current queue contents.
    {
        // Process outside the lock to allow concurrent push.
        ScopedUnlock u(lock);
        callback(batch.begin(), batch.end()); 
    }
    batch.clear();
    dispatching = false;
    if (stopped) lock.notifyAll();
    else h.rewatch();
}

template <class T> void PollableQueue<T>::stop() {
    ScopedLock l(lock);
    handle.stopWatch();
    stopped = true;
    while (dispatching) lock.wait();
}

}} // namespace qpid::sys

#endif  /*!QPID_SYS_POLLABLEQUEUE_H*/
