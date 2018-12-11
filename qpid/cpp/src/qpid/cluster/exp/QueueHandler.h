#ifndef QPID_CLUSTER_QUEUEHANDLER_H
#define QPID_CLUSTER_QUEUEHANDLER_H

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

#include "HandlerBase.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "boost/shared_ptr.hpp"
#include "boost/intrusive_ptr.hpp"
#include <map>

namespace qpid {

namespace broker {
class Queue;
class QueuedMessage;
}

namespace cluster {

class EventHandler;
class QueueReplica;
class Multicaster;
class Group;
class Settings;

/**
 * Handler for queue subscription events.
 *
 * THREAD UNSAFE: only accessed in cluster deliver thread, on delivery
 * of queue controls and also from WiringHandler on delivery of queue
 * create.
 */
class QueueHandler : public framing::AMQP_AllOperations::ClusterQueueHandler,
                     public HandlerBase
{
  public:
    QueueHandler(Group&, Settings&);

    bool handle(const framing::AMQFrame& body);

    // Events
    void subscribe(const std::string& queue);

    void unsubscribe(const std::string& queue, bool resubscribe);

    void consumed(const std::string& queue,
                  const framing::SequenceSet& acquired,
                  const framing::SequenceSet& dequeued);

    void left(const MemberId&);

    void add(broker::Queue&);
    void remove(broker::Queue&);

  private:
    typedef std::map<std::string, boost::intrusive_ptr<QueueReplica> > QueueMap;

    boost::intrusive_ptr<QueueReplica> find(const std::string& queue);

    QueueMap queues;
    Group& group;
    size_t consumeTicks;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_QUEUEHANDLER_H*/
