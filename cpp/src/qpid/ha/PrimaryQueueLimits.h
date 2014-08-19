#ifndef QPID_HA_PRIMARYQUEUELIMITS_H
#define QPID_HA_PRIMARYQUEUELIMITS_H

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
#include <qpid/broker/Queue.h>
#include <qpid/broker/QueueRegistry.h>
#include <qpid/framing/amqp_types.h>
#include <boost/shared_ptr.hpp>
#include <boost/bind.hpp>
#include <string>

namespace qpid {
namespace broker {
class Queue;
}

namespace ha {
class LogPrefix;
class RemoteBackup;

/**
 * Track queue limits on the primary, ensure the primary does not attempt to
 * replicate more queues than the backups can handle.
 *
 * THREAD UNSAFE: Protected by Primary::lock
 */
class PrimaryQueueLimits
{
  public:
    // FIXME aconway 2014-01-24: hardcoded maxQueues, use negotiated channel-max
    PrimaryQueueLimits(const LogPrefix& lp,
                       broker::QueueRegistry& qr,
                       const ReplicationTest& rt
    ) :
        logPrefix(lp), maxQueues(framing::CHANNEL_MAX-100), queues(0)
    {
        // Get initial count of replicated queues
        qr.eachQueue(boost::bind(&PrimaryQueueLimits::addQueueIfReplicated, this, _1, rt)); 
    }

    /** Add a replicated queue
     *@exception ResourceLimitExceededException if this would exceed the limit.
     */
    void addQueue(const boost::shared_ptr<broker::Queue>& q) {
        if (queues >= maxQueues) {
            QPID_LOG(error, logPrefix << "Cannot create replicated queue " << q->getName()
                     << " exceeds limit of " << maxQueues
                     << " replicated queues.");
            throw framing::ResourceLimitExceededException(
                Msg() << "Exceeded replicated queue limit " << queues << " >= " << maxQueues);
        }
        else ++queues;
    }

    void addQueueIfReplicated(const boost::shared_ptr<broker::Queue>& q, const ReplicationTest& rt) {
        if(rt.useLevel(*q)) addQueue(q);
    }

    /** Remove a replicated queue.
     * @pre Was previously added with addQueue
     */
    void removeQueue(const boost::shared_ptr<broker::Queue>&) {
        assert(queues != 0);
        --queues;
    }

    // TODO aconway 2014-01-24: Currently replication links always use the
    // hard-coded framing::CHANNEL_MAX. In future (e.g. when we support AMQP1.0
    // on replication links) we may need to check the actual channel max on each
    // link and update maxQueues to the smallest value. addBackup and removeBackup
    // are placeholders for that.

    /** Add a backup */
    void addBackup(const boost::shared_ptr<RemoteBackup>&) {}

    /** Remove a backup */
    void removeBackup(const boost::shared_ptr<RemoteBackup>&) {}

  private:
    const LogPrefix& logPrefix;
    uint64_t maxQueues;
    uint64_t queues;
}; 

}} // namespace qpid::ha

#endif  /*!QPID_HA_PRIMARYQUEUELIMITS_H*/
