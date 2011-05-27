#ifndef QPID_CLUSTER_MULTICASTER_H
#define QPID_CLUSTER_MULTICASTER_H

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

#include "qpid/cluster/types.h"
#include "qpid/cluster/Event.h"
#include "qpid/sys/PollableQueue.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>
#include <deque>

namespace qpid {

namespace sys {
class Poller;
}

namespace cluster {

class Cpg;

/**
 * Multicast to the cluster. Shared, thread safe object.
 * 
 * holding mode: Hold connection events for later multicast. Cluster
 * events are never held.  Used during PRE_INIT/INIT state when we
 * want to hold any connection traffic till we are read in the
 * cluster.
 *
 * bypass mode: Multicast cluster events directly in the calling
 * thread. This mode is used by cluster in PRE_INIT state the poller
 * is not yet be active.
 *
 * Multicaster is created in bypass+holding mode, they are disabled by
 * start and setReady respectively.
 */
class Multicaster
{
  public:
    /** Starts in initializing mode. */
    Multicaster(Cpg& cpg_,
                const boost::shared_ptr<sys::Poller>&,
                boost::function<void()> onError
    );
    void mcastControl(const framing::AMQBody& controlBody, const ConnectionId&);
    void mcastControl(const framing::AMQFrame& controlFrame, const ConnectionId&);
    void mcastBuffer(const char*, size_t, const ConnectionId&);
    void mcast(const Event& e);

    /** Start the pollable queue, turn off bypass mode. */
    void start();
    /** Switch to ready mode, release held messages. */
    void setReady();

  private:
    typedef sys::PollableQueue<Event> PollableEventQueue;
    typedef std::deque<Event> PlainEventQueue;

    PollableEventQueue::Batch::const_iterator sendMcast(const PollableEventQueue::Batch& );

    sys::Mutex lock;
    boost::function<void()> onError;
    Cpg& cpg;
    PollableEventQueue queue;
    bool ready;
    PlainEventQueue holdingQueue;
    std::vector<struct ::iovec> ioVector;
    bool bypass;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_MULTICASTER_H*/
