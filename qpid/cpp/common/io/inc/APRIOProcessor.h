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
#ifndef _APRIOProcessor_
#define _APRIOProcessor_

#include "apr_poll.h"
#include <queue>
#include <iostream>
#include "APRMonitor.h"
#include "APRThread.h"
#include "IOSession.h"
#include "Runnable.h"

namespace qpid {
namespace io {

    /**
     * Manages non-blocking io through the APR polling
     * routines. Interacts with the actual io tasks to be performed
     * through the IOSession interface, an implementing instance of
     * which must be set as the client_data of the apr_pollfd_t
     * structures registered.
     */
    class APRIOProcessor : private virtual qpid::concurrent::Runnable
    {
        const int size;
        const apr_interval_time_t timeout;
        apr_pollset_t* pollset;
        int count;
        qpid::concurrent::APRThread thread;
        qpid::concurrent::APRMonitor lock;
        volatile bool stopped;

        void poll();        
        virtual void run();        

    public:
        APRIOProcessor(apr_pool_t* pool, int size, int timeout);
        /**
         * Add the fd to the poll set. Relies on the client_data being
         * an instance implementing IOSession, through which the write
         * and read operations will be performed when readiness is
         * indicated by the poll response.
         */
        void add(apr_pollfd_t* const fd);
        /**
         * Remove the fd from the poll set.
         */
        void remove(apr_pollfd_t* const fd);
        /**
         * Indicates whether the capacity of this processor has been
         * reached (or whether it can still handle further fd's).
         */
        bool full();
        /**
         * Indicates whether there are any fd's registered.
         */
        bool empty();
        /**
         * Stop processing.
         */
        void stop();

	~APRIOProcessor();
    };

}
}


#endif
