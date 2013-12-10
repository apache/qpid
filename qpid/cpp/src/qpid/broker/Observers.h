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
#include <vector>
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
    void add(boost::shared_ptr<Observer> observer) {
        sys::Mutex::ScopedLock l(lock);
        observers.push_back(observer);
    }

    void remove(boost::shared_ptr<Observer> observer) {
        sys::Mutex::ScopedLock l(lock);
        typename List::iterator i = std::find(observers.begin(), observers.end(), observer);
        observers.erase(i);
    }

    template <class F> void each(F f) {
        List copy;
        {
            sys::Mutex::ScopedLock l(lock);
            copy = observers;
        }
        std::for_each(copy.begin(), copy.end(), f);
    }

  protected:
    typedef std::vector<boost::shared_ptr<Observer> > List;

    sys::Mutex lock;
    List observers;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_OBSERVERS_H*/
