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

#ifndef QPID_LINEARSTORE_ATOMICCOUNTER_H_
#define QPID_LINEARSTORE_ATOMICCOUNTER_H_

#include "qpid/linearstore/jrnl/slock.h"

namespace qpid {
namespace qls_jrnl {

template <class T>
class AtomicCounter
{
private:
    T count;
    mutable smutex countMutex;

public:
    AtomicCounter(const T& initValue = T(0)) : count(initValue) {}

    virtual ~AtomicCounter() {}

    T get() const {
        slock l(countMutex);
        return count;
    }

    T increment() {
        slock l(countMutex);
        return ++count;
    }

    T add(const T& a) {
        slock l(countMutex);
        count += a;
        return count;
    }

    T addLimit(const T& a, const T& limit, const uint32_t jerr) {
        slock l(countMutex);
        if (count + a > limit) throw jexception(jerr, "AtomicCounter", "addLimit");
        count += a;
        return count;
    }

    T decrement() {
        slock l(countMutex);
        return --count;
    }

    T decrementLimit(const T& limit = T(0), const uint32_t jerr = jerrno::JERR__UNDERFLOW) {
        slock l(countMutex);
        if (count < limit + 1) {
            throw jexception(jerr, "AtomicCounter", "decrementLimit");
        }
        return --count;
    }

    T subtract(const T& s) {
        slock l(countMutex);
        count -= s;
        return count;
    }

    T subtractLimit(const T& s, const T& limit = T(0), const uint32_t jerr = jerrno::JERR__UNDERFLOW) {
        slock l(countMutex);
        if (count < limit + s) throw jexception(jerr, "AtomicCounter", "subtractLimit");
        count -= s;
        return count;
    }

    bool operator==(const T& o) const {
        slock l(countMutex);
        return count == o;
    }

    bool operator<(const T& o) const {
        slock l(countMutex);
        return count < o;
    }

    bool operator<=(const T& o) const {
        slock l(countMutex);
        return count <= o;
    }

    friend T operator-(const T& a, const AtomicCounter& b) {
        slock l(b.countMutex);
        return a - b.count;
    }

    friend T operator-(const AtomicCounter& a, const T& b) {
        slock l(a.countMutex);
        return a.count - b;
    }

    friend T operator-(const AtomicCounter&a, const AtomicCounter& b) {
        slock l1(a.countMutex);
        slock l2(b.countMutex);
        return a.count - b.count;
    }

/*
    friend std::ostream& operator<<(std::ostream& out, const AtomicCounter& a) {
        T temp; // Use temp so lock is not held while streaming to out.
        {
            slock l(a.countMutex);
            temp = a.count;
        }
        out << temp;
        return out;
    }
*/
};

}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_ATOMICCOUNTER_H_
