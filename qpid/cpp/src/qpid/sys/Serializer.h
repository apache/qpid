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

#include "qpid/Exception.h"
#include "qpid/sys/Runnable.h"
#include "qpid/sys/Monitor.h"
#include "qpid/sys/Thread.h"

#include <boost/function.hpp>
#include <boost/noncopyable.hpp>

#include <deque>

namespace qpid {
namespace sys {

/** Abstract base class for Serializer below. */
class SerializerBase : private boost::noncopyable, private Runnable
{
  public:
    typedef boost::function<void()> VoidFn0;
    struct ShutdownException : public Exception {};
        
    /** @see Serializer::Serializer */
    SerializerBase(bool immediate=true);

    virtual ~SerializerBase() { shutdown(); }

    virtual void dispatch() = 0;
  protected:
    enum State {
        IDLE, ///< No threads are active.
        EXECUTING, ///< execute() is executing a single task.
        DISPATCHING, ///< dispatch() is draining the queue.
        SHUTDOWN ///< SerailizerBase is being destroyed.
    };

    void shutdown();
    void notifyWorker();
    void run();
    virtual bool empty() = 0;
    bool running();
    void wait();

    Monitor lock;
    State state;
    bool immediate;
    Thread worker;
};


/**
 * Execute tasks sequentially, queuing tasks when necessary to
 * ensure only one thread at a time executes a task and tasks
 * are executed in order.
 *
 * Task is a void returning 0-arg functor. It must not throw exceptions.
 * 
 * Note we deliberately do not use boost::function as the task type
 * because copying a boost::functor allocates the target object on the
 * heap.
 */
template <class Task>
class Serializer : public SerializerBase {

    std::deque<Task> queue;

    bool empty() { return queue.empty(); }
    void dispatch(Task& task);
    
  public:
    /** Start a serializer.
     *
     * @param immediate Allow execute() to execute a task immediatly
     * in the current thread.
     */
    Serializer(bool immediate=true)
        : SerializerBase(immediate) {}

    ~Serializer() { shutdown(); }
    /** 
     * Task may be executed immediately in the calling thread if there
     * are no other tasks pending or executing and the "immediate"
     * paramater to the constructor was true. Otherwise task will be
     * enqueued for execution by a dispatch thread.
     */
    void execute(Task& task);


    /** Execute pending tasks sequentially in calling thread.
     * Drains the task queue and returns, does not block for more tasks.
     * 
     * @exception ShutdownException if the serializer is being destroyed.
     */
    void dispatch();
    };


template <class Task>
void Serializer<Task>::execute(Task& task) {
    Mutex::ScopedLock l(lock);
    assert(state != SHUTDOWN);
    if (immediate && state == IDLE) {
        state = EXECUTING;
        dispatch(task);
        if (state != SHUTDOWN) {
            assert(state == EXECUTING);
            state = IDLE;
        }
    }
    else 
        queue.push_back(task);
    if (!queue.empty() && state == IDLE) {
        state = DISPATCHING;
        notifyWorker();
    }
}

template <class Task>
void Serializer<Task>::dispatch() {
    Mutex::ScopedLock l(lock);
    // TODO aconway 2007-07-16: This loop could be unbounded
    // if other threads add work while we're in dispatch(Task&).
    // If we need to bound it we could dispatch just the elements
    // that were enqueued when dispatch() was first called - save
    // begin() iterator and pop only up to that.
    while (!queue.empty() && state != SHUTDOWN) {
        assert(state == DISPATCHING);
        dispatch(queue.front());
        queue.pop_front();
    }
    if (state != SHUTDOWN) {
        assert(state == DISPATCHING);
        state = IDLE;
    }
}

template <class Task>
void Serializer<Task>::dispatch(Task& task) {
    // Preconditions: lock is held, state is EXECUTING or DISPATCHING
    assert(state != IDLE);
    assert(state != SHUTDOWN);
    assert(state == EXECUTING || state == DISPATCHING);
    Mutex::ScopedUnlock u(lock);
    // No exceptions allowed in task.
	notifyWorker();
    try { task(); } catch (...) { assert(0); }
}




}} // namespace qpid::sys





#endif  /*!SERIALIZER_H*/
