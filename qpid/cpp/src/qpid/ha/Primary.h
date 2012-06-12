#ifndef QPID_HA_PRIMARY_H
#define QPID_HA_PRIMARY_H

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

#include "UnreadyQueueSet.h"
#include "types.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <map>
#include <string>

namespace qpid {

namespace broker {
class Queue;
}

namespace ha {
class HaBroker;
class ReplicatingSubscription;

/**
 * State associated with a primary broker. Tracks replicating
 * subscriptions to determine when primary is active.
 *
 * THREAD SAFE: readyReplica is called in arbitray threads.
 */
class Primary
{
  public:
    static Primary* get() { return instance; }

    Primary(HaBroker& hb, const IdSet& expectedBackups);

    void readyReplica(const ReplicatingSubscription&);
    void removeReplica(const std::string& q);

    UnreadyQueueSet& getUnreadyQueueSet() { return queues; }
    bool isActive() { return activated; }

  private:
    sys::Mutex lock;
    HaBroker& haBroker;
    std::string logPrefix;
    size_t expected, unready;
    bool activated;
    UnreadyQueueSet queues;

    static Primary* instance;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARY_H*/
