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
#include "qpid/sys/Mutex.h"
#include "qpid/management/Manageable.h"
#include "qpid/framing/FieldTable.h"
#include <boost/bind.hpp>
#include <boost/shared_ptr.hpp>
#include <algorithm>
#include <map>

namespace qpid {
namespace broker {

class Queue;
class QueueEvents;
class Exchange;
class OwnershipToken;
class Broker;
class MessageStore;

/**
 * A registry of queues indexed by queue name.
 *
 * Queues are reference counted using shared_ptr to ensure that they
 * are deleted when and only when they are no longer in use.
 *
 */
class QueueRegistry {
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
        bool durable = false,
        bool autodelete = false, 
        const OwnershipToken* owner = 0,
        boost::shared_ptr<Exchange> alternateExchange = boost::shared_ptr<Exchange>(),
        const qpid::framing::FieldTable& args = framing::FieldTable(),
        bool recovering = false);

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
    QPID_BROKER_EXTERN void destroy(const std::string& name);
    template <class Test> bool destroyIf(const std::string& name, Test test)
    {
        qpid::sys::RWlock::ScopedWlock locker(lock);
        if (test()) {
            destroyLH (name);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Find the named queue. Return 0 if not found.
     */
    QPID_BROKER_EXTERN boost::shared_ptr<Queue> find(const std::string& name);

    /**
     * Get the named queue. Throw exception if not found.
     */
    QPID_BROKER_EXTERN boost::shared_ptr<Queue> get(const std::string& name);

    /**
     * Generate unique queue name.
     */
    std::string generateName();

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
    void setParent (management::Manageable* _parent) { parent = _parent; }

    /** Call f for each queue in the registry. */
    template <class F> void eachQueue(F f) const {
        qpid::sys::RWlock::ScopedRlock l(lock);
        for (QueueMap::const_iterator i = queues.begin(); i != queues.end(); ++i)
            f(i->second);
    }
	
	/**
	* Change queue mode when cluster size drops to 1 node, expands again
	* in practice allows flow queue to disk when last name to be exectuted
	*/
	void updateQueueClusterState(bool lastNode);
    
private:
    typedef std::map<std::string, boost::shared_ptr<Queue> > QueueMap;
    QueueMap queues;
    mutable qpid::sys::RWlock lock;
    int counter;
    MessageStore* store;
    QueueEvents* events;
    management::Manageable* parent;
    bool lastNode; //used to set mode on queue declare
    Broker* broker;

    //destroy impl that assumes lock is already held:
    void destroyLH (const std::string& name);
};

    
}} // namespace qpid::broker


#endif
