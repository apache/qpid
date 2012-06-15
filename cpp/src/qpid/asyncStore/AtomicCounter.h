/*
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
 */

/**
 * \file AtomicCounter.h
 */

#ifndef qpid_asyncStore_AtomicCounter_h_
#define qpid_asyncStore_AtomicCounter_h_

#include "qpid/sys/Condition.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Time.h"

namespace qpid {
namespace asyncStore {

template <class T>
class AtomicCounter
{
public:
    AtomicCounter(const T& initValue = T(0)) :
        m_cnt(initValue),
        m_cntMutex(),
        m_cntCondition()
    {}

    virtual ~AtomicCounter()
    {}

    T&
    get() const
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_cntMutex);
        return m_cnt;
    }

    AtomicCounter&
    operator++()
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_cntMutex);
        ++m_cnt;
        return *this;
    }

    AtomicCounter&
    operator--()
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_cntMutex);
        if (--m_cnt == 0) {
            m_cntCondition.notify();
        }
        return *this;
    }

    bool
    operator==(const AtomicCounter& rhs)
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l1(m_cntMutex);
        qpid::sys::ScopedLock<qpid::sys::Mutex> l2(rhs.m_cntMutex);
        return m_cnt == rhs.m_cnt;
    }

    bool
    operator==(const T rhs)
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_cntMutex);
        return m_cnt == rhs;
    }

    void
    waitForZero(const qpid::sys::Duration& d)
    {
        qpid::sys::ScopedLock<qpid::sys::Mutex> l(m_cntMutex);
        while (m_cnt != 0) {
            m_cntCondition.wait(m_cntMutex, qpid::sys::AbsTime(qpid::sys::AbsTime(), d));
        }
    }

private:
    T m_cnt;
    mutable qpid::sys::Mutex m_cntMutex;
    qpid::sys::Condition m_cntCondition;
};

typedef AtomicCounter<uint32_t> AsyncOpCounter;

}} // namespace qpid::asyncStore

#endif // qpid_asyncStore_AtomicCounter_h_
