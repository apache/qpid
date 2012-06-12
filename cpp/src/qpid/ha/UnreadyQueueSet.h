#ifndef QPID_HA_BROKERGUARD_H
#define QPID_HA_BROKERGUARD_H

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

#include "ReplicationTest.h"
#include "types.h"
#include "qpid/broker/ConfigurationObserver.h"
#include "qpid/sys/Mutex.h"
#include <set>
#include <map>

namespace qpid {

namespace broker {
class Broker;
}

namespace ha {
class QueueGuard;

/**
 * ConfigurationObserver that sets a QueueGuard on all existing/new queues
 * so they are safe until their ReplicatingSubscriptions catch up.
 *
 * THREAD SAFE: Called concurrently as a ConfigurationObserver and via ready()
 */
class UnreadyQueueSet : public qpid::broker::ConfigurationObserver
{
  public:
    /** Caller should call broker.getConfigurationObservers().add(shared_ptr(this)) */
    UnreadyQueueSet(broker::Broker&, ReplicationTest rt, const IdSet& expected);

    void setExpectedBackups(const IdSet&);

    /** Backup id is ready on queue.
     *@return true if all initial queuse are now ready.
     */
    bool ready(const boost::shared_ptr<broker::Queue>&, const types::Uuid& id);

    // ConfigurationObserver overrides
    void queueCreate(const boost::shared_ptr<broker::Queue>&);
    void queueDestroy(const boost::shared_ptr<broker::Queue>&);

    // FIXME aconway 2012-05-31: handle IdSet changes.
  private:
    typedef boost::shared_ptr<QueueGuard> GuardPtr;
    struct Entry {
        bool initial;
        //        FIXME aconway 2012-06-05: GuardPtr guard;
        // Entry(bool i=false, GuardPtr g=GuardPtr()) : initial(i), guard(g) {}
        Entry(bool i=false) : initial(i) {}
    };
    typedef std::map<boost::shared_ptr<broker::Queue>, Entry> QueueMap;

    void remove(QueueMap::iterator i, sys::Mutex::ScopedLock&);

    sys::Mutex lock;
    std::string logPrefix;
    ReplicationTest replicationTest;
    IdSet expected;
    QueueMap queues;
    bool initializing;
    size_t initialQueues;       // Count of initial queues still unready.
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_BROKERGUARD_H*/
