#ifndef _posix_EventChannelThreads_h
#define _sys_EventChannelThreads_h

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
#include "EventChannel.h"

#include "qpid/Exception.h"
#include "qpid/sys/AtomicCount.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/sys/Runnable.h"

#include <vector>

namespace qpid {
namespace sys {

/**
   Dynamic thread pool serving an EventChannel.

   Threads run a loop { e = wait(); e->dispatch(); }
   The size of the thread pool is automatically adjusted to optimal size.
*/
class EventChannelThreads :
        public qpid::SharedObject<EventChannelThreads>,
        private sys::Runnable
{
  public:
    /** Constant to represent an unlimited number of threads */ 
    static const size_t unlimited;
    
    /**
     * Create the thread pool and start initial threads.
     * @param minThreads Pool will initialy contain minThreads threads and
     * will never shrink to less until shutdown.
     * @param maxThreads Pool will never grow to more than maxThreads. 
     */
    static EventChannelThreads::shared_ptr create(
        EventChannel::shared_ptr channel = EventChannel::create(),
        size_t minThreads = 1,
        size_t maxThreads = unlimited
    );

    ~EventChannelThreads();

    /** Post event to the underlying channel */
    void post(Event& event) { channel->post(event); }

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
        RUNNING, TERMINATING, JOINING, SHUTDOWN
    } State;

    EventChannelThreads(
        EventChannel::shared_ptr channel, size_t min, size_t max);
    
    void addThread();

    void run();
    bool keepRunning();
    void adjustThreads();

    Monitor monitor;
    size_t minThreads;
    size_t maxThreads;
    EventChannel::shared_ptr channel;
    Threads workers;
    sys::AtomicCount nWaiting;
    State state;
};


}}


#endif  /*!_sys_EventChannelThreads_h*/
