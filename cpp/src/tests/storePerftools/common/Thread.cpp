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
 * \file Thread.cpp
 */

#include "Thread.h"

#include "PerftoolError.h"

namespace tests {
namespace storePerftools {
namespace common {

Thread::Thread(startFn_t sf,
               void* p) :
        m_running(true)
{
    PTHREAD_CHK(::pthread_create(&m_thread, NULL, sf, p), "::pthread_create", "Thread", "Thread");
}

Thread::Thread(Thread::startFn_t sf,
               void* p,
               const std::string& id) :
        m_id(id),
        m_running(true)
{
    PTHREAD_CHK(::pthread_create(&m_thread, NULL, sf, p), "::pthread_create", "Thread", "Thread");
}

Thread::~Thread() {
    if (m_running) {
        PTHREAD_CHK(::pthread_detach(m_thread), "pthread_detach", "~Thread", "Thread");
    }
}

const std::string&
Thread::getId() const {
    return m_id;
}

void
Thread::join() {
    PTHREAD_CHK(::pthread_join(m_thread, NULL), "pthread_join", "join", "Thread");
    m_running = false;
}

}}} // namespace tests::storePerftools::common
