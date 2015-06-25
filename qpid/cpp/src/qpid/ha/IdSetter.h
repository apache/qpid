#ifndef QPID_HA_IDSETTER_H
#define QPID_HA_IDSETTER_H

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

#include "types.h"

#include "qpid/broker/Message.h"
#include "qpid/broker/MessageInterceptor.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/AtomicValue.h"


namespace qpid {
namespace ha {
class LogPrefix;

/**
 * A MessageInterceptor that sets the ReplicationId on each message as it is
 * enqueued on a primary queue.
 *
 * THREAD UNSAFE: Called sequentially under the queue lock.
 */
class IdSetter : public broker::MessageInterceptor
{
  public:
    IdSetter(const LogPrefix& lp, const std::string& q, ReplicationId firstId=1) :
        logPrefix(lp), queue(q), nextId(firstId)
    {}

    void record(broker::Message& m) {
        // Record is called when a message is first delivered to a queue, before it has
        // been enqueued or saved in a transaction buffer. This is when we normally want
        // to assign a replication-id.
        m.setReplicationId(nextId++);
    }

    void publish(broker::Message& m) {
        // Publish is called when a message is assigned a position on the queue,
        // after any transaction has comitted. Normally this is too late to
        // assign a replication-id but during broker start-up and recovery from
        // store record() is not called, so set the ID now if not already set.
        if (!m.hasReplicationId()) {
            m.setReplicationId(nextId++);
        }
    }

  private:
    const LogPrefix& logPrefix;
    std::string queue;
    sys::AtomicValue<uint32_t> nextId;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_IDSETTER_H*/
