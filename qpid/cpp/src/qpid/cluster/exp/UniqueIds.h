#ifndef QPID_CLUSTER_EXP_UNIQUEIDS_H
#define QPID_CLUSTER_EXP_UNIQUEIDS_H

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
#include <set>

namespace qpid {
namespace cluster {

/**
 * Assign ID numbers, ensuring that all the assigned IDs are unique
 * T is the numeric type - actually any type with >, == and ++ will do.
 */
template <class T> class UniqueIds
{
  public:
    /** Get an ID that is different from all other active IDs.
     *@return the ID, which is now considered active.
     */
    T get() {
        sys::Mutex::ScopedLock l(lock);
        T old = mark;
        while (active.find(++mark) != active.end() && mark != old)
            ;
        assert(mark != old);      // check wrap-around
        active.insert(mark);
        return mark;
    }
    /** Release an ID, so it is inactive and available for re-use */
    void release(T id) {
        sys::Mutex::ScopedLock l(lock);
        active.erase(id);
    }
    /** Allocate an ID, release automatically at end of scope */
    struct Scope {
        UniqueIds& ids;
        T id;
        Scope(UniqueIds& ids_) : ids(ids_), id(ids.get()) {}
        ~Scope() { ids.release(id); }
    };

  private:
    sys::Mutex lock;
    std::set<T> active;
    T mark;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EXP_UNIQUEIDS_H*/
