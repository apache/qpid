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

#ifndef QPID_LINEARSTORE_JOURNAL_ATOMICCOUNTER_H_
#define QPID_LINEARSTORE_JOURNAL_ATOMICCOUNTER_H_

#include "qpid/linearstore/journal/slock.h"
#include <string>

namespace qpid {
namespace linearstore {
namespace journal {

template <class T>
class AtomicCounter
{
private:
    std::string id_;
    T count_;
    mutable smutex countMutex;

public:
    AtomicCounter(const std::string& id, const T& initValue) : id_(id), count_(initValue) {}

    virtual ~AtomicCounter() {}

    T get() const {
        slock l(countMutex);
        return count_;
    }

    void set(const T v) {
        slock l(countMutex);
        count_ = v;
    }

    T increment() {
        slock l(countMutex);
        return ++count_;
    }

    T add(const T& a) {
        slock l(countMutex);
        count_ += a;
        return count_;
    }

    T addLimit(const T& a, const T& limit, const uint32_t jerr) {
        slock l(countMutex);
        if (count_ + a > limit) throw jexception(jerr, id_, "AtomicCounter", "addLimit");
        count_ += a;
        return count_;
    }

    T decrement() {
        slock l(countMutex);
        return --count_;
    }

    T decrementLimit(const T& limit = T(0), const uint32_t jerr = jerrno::JERR__UNDERFLOW) {
        slock l(countMutex);
        if (count_ < limit + 1) {
            throw jexception(jerr, id_, "AtomicCounter", "decrementLimit");
        }
        return --count_;
    }

    T subtract(const T& s) {
        slock l(countMutex);
        count_ -= s;
        return count_;
    }

    T subtractLimit(const T& s, const T& limit = T(0), const uint32_t jerr = jerrno::JERR__UNDERFLOW) {
        slock l(countMutex);
        if (count_ < limit + s) throw jexception(jerr, id_, "AtomicCounter", "subtractLimit");
        count_ -= s;
        return count_;
    }

    bool operator==(const T& o) const {
        slock l(countMutex);
        return count_ == o;
    }

    bool operator<(const T& o) const {
        slock l(countMutex);
        return count_ < o;
    }

    bool operator<=(const T& o) const {
        slock l(countMutex);
        return count_ <= o;
    }

    friend T operator-(const T& a, const AtomicCounter& b) {
        slock l(b.countMutex);
        return a - b.count_;
    }

    friend T operator-(const AtomicCounter& a, const T& b) {
        slock l(a.countMutex);
        return a.count_ - b;
    }

    friend T operator-(const AtomicCounter&a, const AtomicCounter& b) {
        slock l1(a.countMutex);
        slock l2(b.countMutex);
        return a.count_ - b.count_;
    }
};

}}} // namespace qpid::qls_jrnl

#endif // QPID_LINEARSTORE_JOURNAL_ATOMICCOUNTER_H_
