#ifndef _posix_EventChannelThreads_h
#define _sys_EventChannelThreads_h

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
#include <vector>

#include <Exception.h>
#include <sys/Time.h>
#include <sys/Monitor.h>
#include <sys/Thread.h>
#include <sys/AtomicCount.h>
#include "EventChannel.h"

namespace qpid {
namespace sys {

/**
   Dynamic thread pool serving an EventChannel.

   Threads run a loop { e = getEvent(); e->dispatch(); }
   The size of the thread pool is automatically adjusted to optimal size.
*/
class EventChannelThreads :
        public qpid::SharedObject<EventChannelThreads>,
        public sys::Monitor, private sys::Runnable
{
  public:
    /** Create the thread pool and start initial threads. */
    static EventChannelThreads::shared_ptr create(
        EventChannel::shared_ptr channel
    );

    ~EventChannelThreads();

    /** Post event to the underlying channel */
    void postEvent(Event& event) { channel->postEvent(event); }

    /** Post event to the underlying channel Must not be 0. */
    void postEvent(Event* event) { channel->postEvent(event); }

    /**
     * Terminate all threads.
     *
     * Returns immediately, use join() to wait till all threads are
     * shut down. 
     */
    void shutdown();
    
    /** Wait for all threads to terminate. */
    void join();

  private:
    typedef std::vector<sys::Thread> Threads;
    typedef enum {
        RUNNING, TERMINATE_SENT, JOINING, SHUTDOWN
    } State;

    EventChannelThreads(EventChannel::shared_ptr underlyingChannel);
    void addThread();

    void run();
    bool keepRunning();
    void adjustThreads();

    EventChannel::shared_ptr channel;
    Threads workers;
    sys::AtomicCount nWaiting;
    State state;
    Event terminate;
};


}}


#endif  /*!_sys_EventChannelThreads_h*/
