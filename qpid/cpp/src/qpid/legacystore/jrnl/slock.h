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
 * \file slock.h
 *
 * Qpid asynchronous store plugin library
 *
 * Messaging journal scoped lock class mrg::journal::slock and scoped try-lock
 * class mrg::journal::stlock.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_SLOCK_H
#define QPID_LEGACYSTORE_JRNL_SLOCK_H

#include "qpid/legacystore/jrnl/jexception.h"
#include "qpid/legacystore/jrnl/smutex.h"
#include <pthread.h>

namespace mrg
{
namespace journal
{

    // Ultra-simple scoped lock class, auto-releases mutex when it goes out-of-scope
    class slock
    {
    protected:
        const smutex& _sm;
    public:
        inline slock(const smutex& sm) : _sm(sm)
        {
            PTHREAD_CHK(::pthread_mutex_lock(_sm.get()), "::pthread_mutex_lock", "slock", "slock");
        }
        inline ~slock()
        {
            PTHREAD_CHK(::pthread_mutex_unlock(_sm.get()), "::pthread_mutex_unlock", "slock", "~slock");
        }
    };

    // Ultra-simple scoped try-lock class, auto-releases mutex when it goes out-of-scope
    class stlock
    {
    protected:
        const smutex& _sm;
        bool _locked;
    public:
        inline stlock(const smutex& sm) : _sm(sm), _locked(false)
        {
            int ret = ::pthread_mutex_trylock(_sm.get());
            _locked = (ret == 0); // check if lock obtained
            if (!_locked && ret != EBUSY) PTHREAD_CHK(ret, "::pthread_mutex_trylock", "stlock", "stlock");
        }
        inline ~stlock()
        {
            if (_locked)
                PTHREAD_CHK(::pthread_mutex_unlock(_sm.get()), "::pthread_mutex_unlock", "stlock", "~stlock");
        }
        inline bool locked() const { return _locked; }
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_SLOCK_H
