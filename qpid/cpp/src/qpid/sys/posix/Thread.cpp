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

#include "qpid/sys/Thread.h"

#include "qpid/sys/Runnable.h"
#include "qpid/sys/posix/check.h"

#include <pthread.h>

namespace qpid {
namespace sys {

namespace {
void* runRunnable(void* p)
{
    static_cast<Runnable*>(p)->run();
    return 0;
}
}

class ThreadPrivate {
public:
    pthread_t thread;

    ThreadPrivate(Runnable* runnable) {
        QPID_POSIX_ASSERT_THROW_IF(::pthread_create(&thread, NULL, runRunnable, runnable));
    }

    ThreadPrivate() : thread(::pthread_self()) {}
};

Thread::Thread() {}

Thread::Thread(Runnable* runnable) : impl(new ThreadPrivate(runnable)) {}

Thread::Thread(Runnable& runnable) : impl(new ThreadPrivate(&runnable)) {}

Thread::operator bool() {
    return !!impl;
}

bool Thread::operator==(const Thread& t) const {
    return pthread_equal(impl->thread, t.impl->thread) != 0;
}

bool Thread::operator!=(const Thread& t) const {
    return !(*this==t);
}

void Thread::join(){
    if (impl) {
        QPID_POSIX_ASSERT_THROW_IF(::pthread_join(impl->thread, 0));
    }
}

unsigned long Thread::logId() {
    // This does need to be the C cast operator as
    // pthread_t could be either a pointer or an integer
    // and so we can't know static_cast<> or reinterpret_cast<>
    return (unsigned long) ::pthread_self();
}

Thread Thread::current() {
    Thread t;
    t.impl.reset(new ThreadPrivate());
    return t;
}

}}
