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

#ifndef qpid_asyncStore_jrnl2_AtomicCounter_h_
#define qpid_asyncStore_jrnl2_AtomicCounter_h_

#include "ScopedLock.h"

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Template integral atomic counter class which provides a next() operation to increment then return the
 * updated count of integral type T. This operation is performed under a thread lock.
 */
template <class T> class AtomicCounter
{
public:
    /**
     * \brief Constructor with an option to set an inital value for the counter.
     */
    AtomicCounter(T initialValue = T(0)) :
            m_cnt(initialValue)
    {}

    /**
     * \brief Destructor
     */
    virtual ~AtomicCounter()
    {}

    /**
     * \brief Increment, then return new value of the internal counter. This is thread-safe and takes a lock
     * in order to perform the increment.
     *
     * \note This is a non-zero counter. By default, the counter starts with the value 0, and consequently the
     * first call to next() will return 1. Upon overflow, the counter will be incremented twice so as to avoid
     * returning the value 0.
     */
    virtual T next()
    {
        // --- START OF CRITICAL SECTION ---
        ScopedLock l(m_mutex);
        while (!++m_cnt) ; // Cannot return 0x0 if m_cnt should overflow
        return m_cnt;
    }   // --- END OF CRITICAL SECTION ---

protected:
    T m_cnt;                ///< Internal count value
    ScopedMutex m_mutex;    ///< Internal lock used to increment the counter

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_AtomicCounter_h_
