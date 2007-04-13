#ifndef _sys_ThreadSafeQueue_h
#define _sys_ThreadSafeQueue_h

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

#include <deque>
#include "ProducerConsumer.h"
#include "qpid/Exception.h"

namespace qpid {
namespace sys {

/**
 * A thread safe queue template.
 */
template <class T, class ContainerType=std::deque<T> >
class ThreadSafeQueue
{
  public:

    ThreadSafeQueue() {}

    /** Push a value onto the back of the queue */
    void push(const T& value) {
        ProducerConsumer::ProducerLock producer(pc);
        if (producer.isOk()) {
            producer.confirm();
            container.push_back(value);
        }
    }

    /** Pop a value from the front of the queue. Waits till value is available.
     *@throw ShutdownException if queue is shutdown while waiting.
     */
    T pop() {
        ProducerConsumer::ConsumerLock consumer(pc);
        if (consumer.isOk()) {
            consumer.confirm();
            T value(container.front());
            container.pop_front();
            return value;
        }
        throw ShutdownException();
    }

    /**
     * If a value becomes available within the timeout, set outValue
     * and return true. Otherwise return false;
     */
    bool pop(T& outValue, const Time& timeout) {
        ProducerConsumer::ConsumerLock consumer(pc, timeout);
        if (consumer.isOk()) {
            consumer.confirm();
            outValue = container.front();
            container.pop_front();
            return true;
        }
        return false;
    }

    /** Interrupt threads waiting in pop() */
    void shutdown() { pc.shutdown(); }

    /** True if queue is shutdown */
    bool isShutdown() { return pc.isShutdown(); }

    /** Size of the queue */
    size_t size() { ProducerConsumer::Lock l(pc); return container.size(); }

    /** True if queue is empty */
    bool empty() { ProducerConsumer::Lock l(pc); return container.empty(); }

  private:
    ProducerConsumer pc;
    ContainerType container;
};

}} // namespace qpid::sys



#endif  /*!_sys_ThreadSafeQueue_h*/
