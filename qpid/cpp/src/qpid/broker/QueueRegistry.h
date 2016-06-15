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
#ifndef _QueueRegistry_
#define _QueueRegistry_

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/QueueFactory.h"
#include "qpid/sys/Mutex.h"
#include "qpid/framing/FieldTable.h"
#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>
#include <algorithm>
#include <map>

namespace qpid {
namespace broker {

class Queue;
class Exchange;
class OwnershipToken;

/**
 * A registry of queues indexed by queue name.
 *
 * Queues are reference counted using shared_ptr to ensure that they
 * are deleted when and only when they are no longer in use.
 *
 */
class QueueRegistry : private QueueFactory {
  public:
    QPID_BROKER_EXTERN QueueRegistry(Broker* b = 0);
    QPID_BROKER_EXTERN ~QueueRegistry();

    /**
     * Declare a queue.
     *
     * @return The queue and a boolean flag which is true if the queue
     * was created by this declare call false if it already existed.
     */
    QPID_BROKER_EXTERN std::pair<boost::shared_ptr<Queue>, bool> declare(
        const std::string& name,
        const QueueSettings& settings,
        boost::shared_ptr<Exchange> alternateExchange = boost::shared_ptr<Exchange>(),
        bool recovering = false,
        const OwnershipToken* owner = 0,
        std::string connectionId=std::string(), std::string userId=std::string());

    /**
     * Destroy the named queue.
     *
     * Note: if the queue is in use it is not actually destroyed until
     * all shared_ptrs to it are destroyed. During that time it is
     * possible that a new queue with the same name may be
     * created. This should not create any problems as the new and
     * old queues exist independently. The registry has
     * forgotten the old queue so there can be no confusion for
     * subsequent calls to find or declare with the same name.
     *
     */
    QPID_BROKER_EXTERN void destroy(
        const std::string& name,
        const std::string& connectionId=std::string(),
        const std::string& userId=std::string());

    QPID_BROKER_EXTERN bool destroyIfUntouched(const std::string& name, long version,
                                               const std::string& connectionId=std::string(),
                                               const std::string& userId=std::string());

    /**
     * Find the named queue. Return 0 if not found.
     */
    QPID_BROKER_EXTERN boost::shared_ptr<Queue> find(const std::string& name);

    /**
     * Get the named queue. Throw exception if not found.
     */
    QPID_BROKER_EXTERN boost::shared_ptr<Queue> get(const std::string& name);

    /**
     * Set the store to use.  May only be called once.
     */
    void setStore (MessageStore*);

    /**
     * Return the message store used.
     */
    MessageStore* getStore() const;

    /**
     * Register the manageable parent for declared queues
     */
    void setParent (management::Manageable*);

    /** Call f for each queue in the registry. */
    template <class F> void eachQueue(F f) const {
        qpid::sys::RWlock::ScopedRlock l(lock);
        for (QueueMap::const_iterator i = queues.begin(); i != queues.end(); ++i)
            f(i->second);
    }

private:
    typedef std::map<std::string, boost::shared_ptr<Queue> > QueueMap;
    QueueMap queues;
    mutable qpid::sys::RWlock lock;

    void eraseLH(QueueMap::iterator, boost::shared_ptr<Queue>, const std::string& name, const std::string& connectionId, const std::string& userId);
};


}} // namespace qpid::broker


#endif
