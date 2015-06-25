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

/**
 * \file cvar.h
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains a posix condition variable class.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_CVAR_H
#define QPID_LEGACYSTORE_JRNL_CVAR_H

#include <cstring>
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/jexception.h"
#include "qpid/legacystore/jrnl/smutex.h"
#include "qpid/legacystore/jrnl/time_ns.h"
#include <pthread.h>
#include <sstream>

namespace mrg
{
namespace journal
{

    // Ultra-simple thread condition variable class
    class cvar
    {
    private:
        const smutex& _sm;
        pthread_cond_t _c;
    public:
        inline cvar(const smutex& sm) : _sm(sm) { ::pthread_cond_init(&_c, 0); }
        inline ~cvar() { ::pthread_cond_destroy(&_c); }
        inline void wait()
        {
            PTHREAD_CHK(::pthread_cond_wait(&_c, _sm.get()), "::pthread_cond_wait", "cvar", "wait");
        }
        inline void timedwait(timespec& ts)
        {
            PTHREAD_CHK(::pthread_cond_timedwait(&_c, _sm.get(), &ts), "::pthread_cond_timedwait", "cvar", "timedwait");
        }
        inline bool waitintvl(const long intvl_ns)
        {
            time_ns t; t.now(); t+=intvl_ns;
            int ret = ::pthread_cond_timedwait(&_c, _sm.get(), &t);
            if (ret == ETIMEDOUT)
                return true;
            PTHREAD_CHK(ret, "::pthread_cond_timedwait", "cvar", "waitintvl");
            return false;
        }
        inline void signal()
        {
            PTHREAD_CHK(::pthread_cond_signal(&_c), "::pthread_cond_signal", "cvar", "notify");
        }
        inline void broadcast()
        {
            PTHREAD_CHK(::pthread_cond_broadcast(&_c), "::pthread_cond_broadcast", "cvar", "broadcast");
        }
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_CVAR_H
