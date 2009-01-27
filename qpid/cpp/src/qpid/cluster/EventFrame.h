#ifndef QPID_CLUSTER_EVENTFRAME_H
#define QPID_CLUSTER_EVENTFRAME_H

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
#include "Event.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/sys/LatencyMetric.h"
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace cluster {

class Connection;

/**
 * A frame decoded from an Event.
 */
struct EventFrame
{
    EventFrame() : sequence(0) {}
    // Connection event frame
    EventFrame(const boost::intrusive_ptr<Connection>& c, const Event& e, const framing::AMQFrame& f, int rc=0)
        : connection(c), member(e.getMemberId()), frame(f), sequence(e.getSequence()), readCredit(rc)
    {
        QPID_LATENCY_INIT(frame);
    }

    bool isCluster() const { return !connection; }
    bool isConnection() const { return connection; }
    bool isLastInEvent() const { return readCredit; }

    // True if this frame follows immediately after frame e. 
    bool follows(const EventFrame& e) const {
        return sequence == e.sequence || (sequence == e.sequence+1 && e.readCredit);
    }

    bool operator<(const EventFrame& e) const { return sequence < e.sequence; }
    
    boost::intrusive_ptr<Connection> connection;
    MemberId member;
    framing::AMQFrame frame;   
    uint64_t sequence;
    int readCredit;             // last frame in an event, give credit when processed.
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_EVENTFRAME_H*/
