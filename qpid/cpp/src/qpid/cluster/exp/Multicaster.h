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

#include "BufferFactory.h"
#include "qpid/sys/PollableQueue.h"
#include <sys/uio.h>            // For struct iovec
#include <vector>

namespace qpid {

namespace framing {
class AMQDataBlock;
class AMQBody;
}

namespace sys {
class Poller;
}

namespace cluster {

class Cpg;

/**
 * Multicast to a CPG group in poller threads. Shared, thread safe object.
 */
class Multicaster
{
  public:
    Multicaster(Cpg& cpg_,
                const boost::shared_ptr<sys::Poller>&,
                boost::function<void()> onError
    );

    /** Multicast an event */
    void mcast(const framing::AMQDataBlock&);
    void mcast(const framing::AMQBody&);

  private:
    typedef sys::PollableQueue<BufferRef> PollableEventQueue;

    PollableEventQueue::Batch::const_iterator sendMcast(const PollableEventQueue::Batch& );

    boost::function<void()> onError;
    Cpg& cpg;
    PollableEventQueue queue;
    std::vector<struct ::iovec> ioVector;
    BufferFactory buffers;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_MULTICASTER_H*/
