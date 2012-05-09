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
 * \file ScopedLock.h
 */

#ifndef qpid_asyncStore_jrnl2_ScopedLock_h_
#define qpid_asyncStore_jrnl2_ScopedLock_h_

#include <pthread.h>

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

class ScopedMutex
{
public:
    ScopedMutex();
    virtual ~ScopedMutex();
    ::pthread_mutex_t* get() const;
protected:
    mutable ::pthread_mutex_t m_mutex;
};



class ScopedMutexContainer
{
public:
    ScopedMutexContainer(const ScopedMutex& sm);
    ::pthread_mutex_t* get() const;
protected:
    const ScopedMutex& m_scopedMutexRef;    ///< Reference to ScopedMutex instance being locked.
};



class ScopedLock : public ScopedMutexContainer
{
public:
    ScopedLock(const ScopedMutex& sm);
    virtual ~ScopedLock();
};



class ScopedTryLock : public ScopedMutexContainer
{
public:
    ScopedTryLock(const ScopedMutex& sm);
    virtual ~ScopedTryLock();
    bool isLocked() const;
protected:
    bool m_lockedFlag;
};



/*
class ScopedConditionVariable
{
public:
    ScopedConditionVariable();
    ~ScopedConditionVariable();
    void wait(ScopedLock& sl);
    void notify_one();
    void notify_all();
protected:
    mutable ::pthread_cond_t m_cv;
};
*/

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_ScopedLock_h_
