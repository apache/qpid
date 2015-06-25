#ifndef QPID_BROKER_QUEUEOBSERVERS_H
#define QPID_BROKER_QUEUEOBSERVERS_H

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

#include "Observers.h"
#include "QueueObserver.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

/**
 * A collection of queue observers.
 */
class QueueObservers : public Observers<QueueObserver> {
  public:
    typedef Observers<QueueObserver> Base;

    // The only public functions are inherited from Observers<QueueObserver>
    using Base::each;           // Avoid function hiding.

  friend class Queue;

    typedef const sys::Mutex::ScopedLock& Lock;

    QueueObservers(const std::string& q, sys::Mutex& lock) : Base(lock), qname(q) {}

    template <class T> void each(void (QueueObserver::*f)(const T&), const T& arg, const char* fname, Lock l) {
        Base::each(boost::bind(&QueueObservers::wrap<T>, this, f, boost::cref(arg), fname, _1), l);
    }

    template <class T> void wrap(void (QueueObserver::*f)(const T&), const T& arg, const char* fname, const ObserverPtr& o) {
        try { (o.get()->*f)(arg); }
        catch (const std::exception& e) {
            QPID_LOG(warning, "Exception on " << fname << " for queue " << qname << ": " << e.what());
        }
    }


    // Calls are locked externally by caller.
    void enqueued(const Message& m, Lock l) { each(&QueueObserver::enqueued, m, "enqueue", l); }
    void dequeued(const Message& m, Lock l) { each(&QueueObserver::dequeued, m, "dequeue", l); }
    void acquired(const Message& m, Lock l) { each(&QueueObserver::acquired, m, "acquire", l); }
    void requeued(const Message& m, Lock l) { each(&QueueObserver::requeued, m, "requeue", l); }
    void consumerAdded(const Consumer& c, Lock l) { each(&QueueObserver::consumerAdded, c, "consumer added", l); }
    void consumerRemoved(const Consumer& c, Lock l) { each(&QueueObserver::consumerRemoved, c, "consumer removed", l); }
    void destroy(Lock l) {
        Base::each(boost::bind(&QueueObserver::destroy, _1), l);
        observers.clear();
    }

    std::string qname;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEOBSERVERS_H*/
