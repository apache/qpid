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

#ifndef QPID_LINEARSTORE_JOURNAL_SMUTEX_H
#define QPID_LINEARSTORE_JOURNAL_SMUTEX_H

#include "qpid/linearstore/journal/jexception.h"
#include <pthread.h>

namespace qpid {
namespace linearstore {
namespace journal {

    // Ultra-simple scoped mutex class that allows a posix mutex to be initialized and destroyed with error checks
    class smutex
    {
    protected:
        mutable pthread_mutex_t _m;
    public:
        inline smutex()
        {
            PTHREAD_CHK(::pthread_mutex_init(&_m, 0), "::pthread_mutex_init", "smutex", "smutex");
        }
        inline virtual ~smutex()
        {
            PTHREAD_CHK(::pthread_mutex_destroy(&_m), "::pthread_mutex_destroy", "smutex", "~smutex");
        }
        inline pthread_mutex_t* get() const { return &_m; }
    };

}}}

#endif // ifndef QPID_LINEARSTORE_JOURNAL_SMUTEX_H
