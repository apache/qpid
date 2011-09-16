#ifndef QPID_CLUSTER_QUEUEMODEL_H
#define QPID_CLUSTER_QUEUEMODEL_H

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

#include "qpid/RefCounted.h"
#include "qpid/cluster/types.h"
#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>
#include <deque>

namespace qpid {

namespace broker {
class Queue;
}

namespace cluster {
class QueueContext;
struct PrintSubscribers;

/**
 * Queue state that is replicated among all cluster members.
 *
 * Handles queue subscription controls by starting/stopping the queue.
 *
 * THREAD UNSAFE: only used in cluster deliver thread, on delivery
 * of queue controls and also from WiringHandler on delivery of queue
 * create.
 */
class QueueReplica : public RefCounted
{
  public:
    QueueReplica(boost::shared_ptr<broker::Queue> , const MemberId& );
    void subscribe(const MemberId&);
    void unsubscribe(const MemberId&);
    void resubscribe(const MemberId&);

    MemberId getSelf() const { return self; }
    
  private:
    typedef std::deque<MemberId> MemberQueue;

    boost::shared_ptr<broker::Queue> queue;
    MemberQueue subscribers;
    MemberId self;
    boost::intrusive_ptr<QueueContext> context;

    QueueOwnership getState() const;
    bool isOwner() const;
    bool isSubscriber(const MemberId&) const;
    void update(QueueOwnership before);

  friend struct PrintSubscribers;
  friend std::ostream& operator<<(std::ostream&, QueueOwnership);
  friend std::ostream& operator<<(std::ostream&, const QueueReplica&);
  friend std::ostream& operator<<(std::ostream& o, const PrintSubscribers& ps);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_QUEUEMODEL_H*/
