#ifndef _sys_posix_Mutex_h
#define _sys_posix_Mutex_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "check.h"

#include <pthread.h>
#include <boost/noncopyable.hpp>

namespace qpid {
namespace sys {

class Condition;

/**
 * Mutex lock.
 */
class Mutex : private boost::noncopyable {
    friend class Condition;

public:
    typedef ScopedLock<Mutex> ScopedLock;
    typedef ScopedUnlock<Mutex> ScopedUnlock;
    
    inline Mutex();
    inline ~Mutex();
    inline void lock();
    inline void unlock();
    inline void trylock();

protected:
    pthread_mutex_t mutex;
};

/**
 * Initialise a recursive mutex attr for use in creating mutexes later
 * (we use pthread_once to make sure it is initialised exactly once)
 */
namespace {
	pthread_once_t  onceControl = PTHREAD_ONCE_INIT;
	pthread_mutexattr_t mutexattr;
	
	void initMutexattr()  {
		pthread_mutexattr_init(&mutexattr);
		pthread_mutexattr_settype(&mutexattr, PTHREAD_MUTEX_RECURSIVE);
	}
	
	struct RecursiveMutexattr {
		RecursiveMutexattr() {
			pthread_once(&onceControl, initMutexattr);
		}
		
		operator const pthread_mutexattr_t*() const {
			return &mutexattr;
		}
	};
	
	RecursiveMutexattr recursiveMutexattr;
}

/**
 * PODMutex is a POD, can be static-initialized with
 * PODMutex m = QPID_PODMUTEX_INITIALIZER
 */
struct PODMutex 
{
    typedef ScopedLock<PODMutex> ScopedLock;

    inline void lock();
    inline void unlock();
    inline void trylock();

    // Must be public to be a POD:
    pthread_mutex_t mutex;
};

#define QPID_MUTEX_INITIALIZER { PTHREAD_MUTEX_INITIALIZER }

void PODMutex::lock() {
    QPID_POSIX_THROW_IF(pthread_mutex_lock(&mutex));
}
void PODMutex::unlock() {
    QPID_POSIX_THROW_IF(pthread_mutex_unlock(&mutex));
}

void PODMutex::trylock() {
    QPID_POSIX_THROW_IF(pthread_mutex_trylock(&mutex));
}

Mutex::Mutex() {
    QPID_POSIX_THROW_IF(pthread_mutex_init(&mutex, recursiveMutexattr));
}

Mutex::~Mutex(){
    QPID_POSIX_THROW_IF(pthread_mutex_destroy(&mutex));
}

void Mutex::lock() {
    QPID_POSIX_THROW_IF(pthread_mutex_lock(&mutex));
}
void Mutex::unlock() {
    QPID_POSIX_THROW_IF(pthread_mutex_unlock(&mutex));
}

void Mutex::trylock() {
    QPID_POSIX_THROW_IF(pthread_mutex_trylock(&mutex));
}

}}
#endif  /*!_sys_posix_Mutex_h*/
