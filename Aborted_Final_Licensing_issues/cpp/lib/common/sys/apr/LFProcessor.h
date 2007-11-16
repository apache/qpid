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
#ifndef _LFProcessor_
#define _LFProcessor_

#include <apr_poll.h>
#include <iostream>
#include <vector>
#include <sys/Monitor.h>
#include <sys/Runnable.h>
#include <sys/Thread.h>

namespace qpid {
namespace sys {

    class LFSessionContext;

    /**
     * This class processes a poll set using the leaders-followers
     * pattern for thread synchronization: the leader will poll and on
     * the poll returning, it will remove a session, promote a
     * follower to leadership, then process the session.
     */
    class LFProcessor : private virtual qpid::sys::Runnable
    {
        typedef std::vector<LFSessionContext*>::iterator iterator;
        
        const int size;
        const apr_interval_time_t timeout;
        apr_pollset_t* pollset;
        int signalledCount;
        int current;
        const apr_pollfd_t* signalledFDs;
        int count;
        const int workerCount;
        bool hasLeader;
        qpid::sys::Thread* workers;
        qpid::sys::Monitor leadLock;
        qpid::sys::Mutex countLock;
        std::vector<LFSessionContext*> sessions;
        volatile bool stopped;
        apr_pool_t* pool;

        const apr_pollfd_t* getNextEvent();
        void waitToLead();
        void relinquishLead();
        void poll();        
        virtual void run();        

    public:
        LFProcessor(int workers, int size, int timeout);
        /**
         * Add the fd to the poll set. Relies on the client_data being
         * an instance of LFSessionContext.
         */
        void add(const apr_pollfd_t* const fd);
        /**
         * Remove the fd from the poll set.
         */
        void remove(const apr_pollfd_t* const fd);
        /**
         * Signal that the fd passed in, already part of the pollset,
         * has had its flags altered.
         */
        void update(const apr_pollfd_t* const fd);
        /**
         * Add an fd back to the poll set after deactivation.
         */
        void reactivate(const apr_pollfd_t* const fd);
        /**
         * Temporarily remove the fd from the poll set. Called when processing
         * is about to begin.
         */
        void deactivate(const apr_pollfd_t* const fd);
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
        /**
         * Start processing.
         */
        void start();
        /**
         * Is processing stopped?
         */
        bool isStopped();
        
	~LFProcessor();
    };

}
}


#endif
