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
 * \file ScopedLock.cpp
 */

#include "ScopedLock.h"

#include "JournalError.h"

#include <cerrno> // EBUSY
#include <cstring> // std::strerror
#include <sstream> // std::ostringstream

namespace qpid {
namespace asyncStore {
namespace jrnl2 {

// --- ScopedMutex ---

ScopedMutex::ScopedMutex()
{
    PTHREAD_CHK(::pthread_mutex_init(&m_mutex, 0), "::pthread_mutex_init", "ScopedMutex", "ScopedMutex");
}

ScopedMutex::~ScopedMutex() {
    PTHREAD_CHK(::pthread_mutex_destroy(&m_mutex), "::pthread_mutex_destroy", "ScopedMutex", "~ScopedMutex");
}

::pthread_mutex_t*
ScopedMutex::get() const {
    return &m_mutex;
}


// --- ScopedMutexContainer ---

ScopedMutexContainer::ScopedMutexContainer(const ScopedMutex& sm) :
        m_scopedMutexRef(sm)
{}

::pthread_mutex_t* ScopedMutexContainer::get() const {
    return m_scopedMutexRef.get();
}


// --- ScopedLock ---

ScopedLock::ScopedLock(const ScopedMutex& sm) :
        ScopedMutexContainer(sm)
{
    PTHREAD_CHK(::pthread_mutex_lock(m_scopedMutexRef.get()), "::pthread_mutex_lock", "ScopedLock", "ScopedLock");
}

ScopedLock::~ScopedLock() {
    PTHREAD_CHK(::pthread_mutex_unlock(m_scopedMutexRef.get()), "::pthread_mutex_unlock", "ScopedLock", "~ScopedLock");
}


// --- ScopedTryLock ---

ScopedTryLock::ScopedTryLock(const ScopedMutex& sm) :
        ScopedMutexContainer(sm),
        m_lockedFlag(false)
{
    int ret = ::pthread_mutex_trylock(m_scopedMutexRef.get());
    m_lockedFlag = (ret == 0); // check if lock obtained
    if (!m_lockedFlag && ret != EBUSY) {
        PTHREAD_CHK(ret, "::pthread_mutex_trylock", "ScopedTryLock", "ScopedTryLock");
    }
}

ScopedTryLock::~ScopedTryLock() {
    if (m_lockedFlag)
        PTHREAD_CHK(::pthread_mutex_unlock(m_scopedMutexRef.get()), "::pthread_mutex_unlock", "ScopedTryLock",
                    "~ScopedTryLock");
}

bool
ScopedTryLock::isLocked() const {
    return m_lockedFlag;
}


// --- ScopedConditionVariable ---
/*

ScopedConditionVariable::ScopedConditionVariable()
{
    PTHREAD_CHK(::pthread_cond_init(&m_cv, 0), "pthread_cond_init", "ScopedConditionVariable",
                    "ScopedConditionVariable");
}

ScopedConditionVariable::~ScopedConditionVariable()
{
    PTHREAD_CHK(::pthread_cond_destroy(&m_cv), "pthread_cond_destroy", "ScopedConditionVariable",
                    "~ScopedConditionVariable");
}

void
ScopedConditionVariable::wait(ScopedLock& sl)
{
    PTHREAD_CHK(::pthread_cond_wait(&m_cv, sl.get()), "pthread_cond_wait", "ScopedConditionVariable", "wait");
}

void
ScopedConditionVariable::notify_one()
{
    PTHREAD_CHK(::pthread_cond_signal(&m_cv), "pthread_cond_signal", "ScopedConditionVariable", "notify_one");
}

void
ScopedConditionVariable::notify_all()
{
    PTHREAD_CHK(::pthread_cond_broadcast(&m_cv), "pthread_cond_broadcast", "ScopedConditionVariable",
                    "notify_all");
}
*/

}}} // namespace qpid::asyncStore::jrnl2
