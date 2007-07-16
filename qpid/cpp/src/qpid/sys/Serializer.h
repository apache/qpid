#ifndef SERIALIZER_H
#define SERIALIZER_H


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


#include "qpid/sys/Runnable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"

#include <boost/function.hpp>
#include <boost/noncopyable.hpp>

#include <deque>

namespace qpid {
namespace sys {

/**
 * Execute tasks sequentially, queuing tasks when necessary to
 * ensure only one thread at a time executes a task and tasks
 * are executed in order.
 */
class Serializer : private boost::noncopyable, private Runnable
{
  public:
    typedef boost::function<void()> Task;

    /** Start a serializer.
     *
     * @param notifyDispatch Called when work is pending and there is no
     * active dispatch thread. Must arrange for dispatch() to be called
     * in some thread other than the calling thread and return. 
     * By default the Serializer supplies its own dispatch thread.
     *
     * @param immediate Allow execute() to execute a task immediatly
     * in the current thread.
     */
    Serializer(bool immediate=true, Task notifyDispatch=Task());

    ~Serializer();
    
    /** 
     * Task may be executed immediately in the calling thread if there
     * are no other tasks pending or executing and the "immediate"
     * paramater to the constructor was true. Otherwise task will be
     * enqueued for execution by a dispatch thread.
     */
    void execute(Task task);

    /** Execute pending tasks sequentially in calling thread.
     * Drains the task queue and returns, does not block for more tasks.
     * 
     * @exception ShutdownException if the serializer is being destroyed.
     */
    void dispatch();

  private:
    enum State {
        IDLE, ///< No threads are active.
        EXECUTING, ///< execute() is executing a single task.
        DISPATCHING, ///< dispatch() is draining the queue.
        SHUTDOWN ///< Serializer is being destroyed.
    };

    void dispatch(Task&);
    void notifyWorker();
    void run();

    Monitor lock;

    State state;
    bool immediate;
    std::deque<Task> queue;
    Thread worker;
    Task notifyDispatch;
};

}} // namespace qpid::sys





#endif  /*!SERIALIZER_H*/
