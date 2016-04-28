#ifndef QPID_BROKER_OBSERVERS_H
#define QPID_BROKER_OBSERVERS_H

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
#include <boost/shared_ptr.hpp>
#include <set>
#include <algorithm>

namespace qpid {
namespace broker {

/**
 * Base class for collections of observers with thread-safe add/remove and traversal.
 */
template <class Observer>
class Observers
{
  public:
    typedef boost::shared_ptr<Observer> ObserverPtr;

    void add(const ObserverPtr& observer) {
        sys::Mutex::ScopedLock l(lock);
        observers.insert(observer);
    }

    void remove(const ObserverPtr& observer) {
        sys::Mutex::ScopedLock l(lock);
        observers.erase(observer) ;
    }

    /** Iterate over the observers. */
    template <class F> void each(F f) {
        Set copy;               // Make a copy and iterate outside the lock.
        {
            sys::Mutex::ScopedLock l(lock);
            copy = observers;
        }
        std::for_each(copy.begin(), copy.end(), f);
    }

    template <class T> boost::shared_ptr<T> findType() const {
        sys::Mutex::ScopedLock l(lock);
        typename Set::const_iterator i =
            std::find_if(observers.begin(), observers.end(), &isA<T>);
        return i == observers.end() ?
            boost::shared_ptr<T>() : boost::dynamic_pointer_cast<T>(*i);
    }
    virtual ~Observers() {}

  protected:
    typedef std::set<ObserverPtr> Set;
    Observers() : lock(myLock) {}

    /** Specify a lock for the Observers to use */
    Observers(sys::Mutex& l) : lock(l) {}

    /** Iterate over the observers without taking the lock, caller must hold the lock */
    template <class F> void each(F f, const sys::Mutex::ScopedLock&) {
        std::for_each(observers.begin(), observers.end(), f);
    }

    template <class T> static bool isA(const ObserverPtr&o) {
        return !!boost::dynamic_pointer_cast<T>(o);
    }

    mutable sys::Mutex myLock;
    sys::Mutex& lock;
    Set observers;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_OBSERVERS_H*/
