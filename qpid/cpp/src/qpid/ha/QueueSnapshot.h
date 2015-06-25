#ifndef QPID_HA_IDSETOBSERVER_H
#define QPID_HA_IDSETOBSERVER_H

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

#include "qpid/broker/Message.h"
#include "qpid/broker/QueueObserver.h"
#include "qpid/sys/Mutex.h"

namespace qpid {
namespace ha {

/**
 * A QueueObserver that maintains a ReplicationIdSet of the ReplicationIds of
 * the messages on the queue.
 *
 * THREAD SAFE: Note that QueueObserver methods are called under the Queues messageLock.
 *
 */
class  QueueSnapshot : public broker::QueueObserver
{
  public:
    void enqueued(const broker::Message& m) {
        sys::Mutex::ScopedLock l(lock);
        set += m.getReplicationId();
    }

    void dequeued(const broker::Message& m) {
        sys::Mutex::ScopedLock l(lock);
        set -= m.getReplicationId();
    }

    void acquired(const broker::Message&) {}

    void requeued(const broker::Message&) {}

    ReplicationIdSet getSnapshot() {
        sys::Mutex::ScopedLock l(lock);
        return set;
    }

  private:
    sys::Mutex lock;
    ReplicationIdSet set;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_IDSETOBSERVER_H*/
