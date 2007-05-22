#ifndef _sys_ProducerConsumer_h
#define _sys_ProducerConsumer_h

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
#include <boost/noncopyable.hpp>
#include "qpid/Exception.h"
#include "Monitor.h"

namespace qpid {
namespace sys {

/**
 * Producer-consumer synchronisation.
 * 
 * Producers increase the number of available items, consumers reduce it.
 * Consumers wait till an item is available. Waiting threads can be
 * woken for shutdown using shutdown().
 *
 * Note: Currently implements unbounded producer-consumer, i.e. no limit
 * to available items, producers never block. Can be extended to support
 * bounded PC if required.
 *
 // TODO aconway 2007-02-13: example, from tests.
*/
class ProducerConsumer
{
  public:
    ProducerConsumer(size_t init_items=0);

    ~ProducerConsumer() { shutdown(); }

    /**
     * Wake any threads waiting for ProducerLock or ConsumerLock.
     *@post No threads are waiting in Producer or Consumer locks.
     */
    void shutdown();

    /** True if queue is shutdown */
    bool isShutdown() { return shutdownFlag; }

    /** Number of items available for consumers */
    size_t available() const;

    /** Number of consumers waiting for items */
    size_t consumers() const;

    /** True if available == 0 */
    bool empty() const { return available() == 0; }

    /**
     * Base class for producer and consumer locks.
     */
    class Lock : private boost::noncopyable {
      public:

        /**
         * You must call isOk() after creating a lock to verify its state.
         * 
         *@return true means the lock succeeded. You MUST call either
         *confirm() or cancel() before the lock goes out of scope.
         *
         * false means the lock failed - timed out or the
         * ProducerConsumer is shutdown. You should not do anything in
         * the scope of the lock.
         */
        bool isOk() const;

        /**
         * Confirm that an item was produced/consumed.
         *@pre isOk()
         */
        void confirm();

        /**
         * Cancel the lock to indicate nothing was produced/consumed.
         * Note that locks are not actually released until destroyed.
         *
         *@pre isOk()
         */
        void cancel();

        /** True if this lock experienced a timeout */
        bool isTimedOut() const { return status == TIMEOUT; }

        /** True if we have been shutdown */
        bool isShutdown() const { return pc.isShutdown(); }
        
        ProducerConsumer& pc;

      protected:
        /** Lock status */
        enum Status { INCOMPLETE, CONFIRMED, CANCELLED, TIMEOUT };

        Lock(ProducerConsumer& p);
        ~Lock();
        void checkOk() const;
        Mutex::ScopedLock lock;
        Status status;
    };
    
    /** Lock for code that produces items. */
    struct ProducerLock : public Lock {
        /**
         * Acquire locks to produce an item.
         *@post If isOk() the calling thread has exclusive access
         * to produce an item.
         */
        ProducerLock(ProducerConsumer& p);

        /** Release locks, signal waiting consumers if confirm() was called. */
        ~ProducerLock();
    };

    /** Lock for code that consumes items */
    struct ConsumerLock : public Lock {
        /**
         * Wait for an item to consume and acquire locks.
         * 
         *@post If isOk() there is at least one item available and the
         *calling thread has exclusive access to consume it.
         */
        ConsumerLock(ProducerConsumer& p);

        /**
         * Wait up to timeout to acquire lock.
         *@post If isOk() caller has a producer lock.
         * If isTimedOut() there was a timeout.
         * If neither then we were shutdown.
         */
        ConsumerLock(ProducerConsumer& p, const Duration& timeout);

        /** Release locks */
        ~ConsumerLock();
    };

  private:
    mutable Monitor monitor;
    size_t items;
    size_t waiters;
    bool shutdownFlag;

  friend class Lock;
  friend class ProducerLock;
  friend class ConsumerLock;
};

}} // namespace qpid::sys

#endif  /*!_sys_ProducerConsumer_h*/
