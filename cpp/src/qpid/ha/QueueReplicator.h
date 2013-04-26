#ifndef QPID_HA_QUEUEREPLICATOR_H
#define QPID_HA_QUEUEREPLICATOR_H

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

#include "BrokerInfo.h"
#include "qpid/broker/Exchange.h"
#include "qpid/framing/SequenceSet.h"
#include <boost/enable_shared_from_this.hpp>
#include <iosfwd>

namespace qpid {

namespace broker {
class Bridge;
class Link;
class Queue;
class QueueRegistry;
class SessionHandler;
class Deliverable;
}

namespace ha {
class HaBroker;
class Settings;

/**
 * Exchange created on a backup broker to replicate a queue on the primary.
 *
 * Puts replicated messages on the local queue, handles dequeue events.
 * Creates a ReplicatingSubscription on the primary by passing special
 * arguments to the consume command.
 *
 * THREAD SAFE: Called in different connection threads.
 */
class QueueReplicator : public broker::Exchange,
                        public boost::enable_shared_from_this<QueueReplicator>
{
  public:
    static const std::string DEQUEUE_EVENT_KEY;
    static const std::string POSITION_EVENT_KEY;
    static const std::string QPID_SYNC_FREQUENCY;

    static std::string replicatorName(const std::string& queueName);
    static bool isReplicatorName(const std::string&);

    /** Test if a string is an event key */
    static bool isEventKey(const std::string key);

    QueueReplicator(HaBroker&,
                    boost::shared_ptr<broker::Queue> q,
                    boost::shared_ptr<broker::Link> l);

    ~QueueReplicator();

    void activate();            // Must be called immediately after constructor.

    std::string getType() const;
    bool bind(boost::shared_ptr<broker::Queue
              >, const std::string&, const framing::FieldTable*);
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    void route(broker::Deliverable&);
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const);

    // Set if the queue has ever been subscribed to, used for auto-delete cleanup.
    void setSubscribed() { subscribed = true; }
    bool isSubscribed() { return subscribed; }

    boost::shared_ptr<broker::Queue> getQueue() const { return queue; }

  private:
    class ErrorListener;
    class QueueObserver;

    void initializeBridge(broker::Bridge& bridge, broker::SessionHandler& sessionHandler);
    void destroy();             // Called when the queue is destroyed.
    void dequeue(framing::SequenceNumber, sys::Mutex::ScopedLock&);

    HaBroker& haBroker;
    std::string logPrefix;
    std::string bridgeName;
    sys::Mutex lock;
    boost::shared_ptr<broker::Queue> queue;
    boost::shared_ptr<broker::Link> link;
    boost::shared_ptr<broker::Bridge> bridge;
    BrokerInfo brokerInfo;
    bool subscribed;
    const Settings& settings;
    bool destroyed;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUEREPLICATOR_H*/
