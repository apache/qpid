#ifndef QPID_HA_QUEUERANGE_H
#define QPID_HA_QUEUERANGE_H

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

#include "ReplicatingSubscription.h"
#include "qpid/broker/Queue.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/SequenceNumber.h"
#include <iostream>

namespace qpid {
namespace ha {

/**
 * Get the front/back range of a queue or from a ReplicatingSubscription arguments table.
 */
struct QueueRange {
  public:
    framing::SequenceNumber front, back;

    QueueRange() { }

    QueueRange(broker::Queue& q) {
        back = q.getPosition();
        front = back+1;         // assume empty
        ReplicatingSubscription::getFront(q, front);
        assert(front <= back + 1);
    }

    QueueRange(const framing::FieldTable& args) {
        back = args.getAsInt(ReplicatingSubscription::QPID_BACK);
        front = back+1;
        if (args.isSet(ReplicatingSubscription::QPID_FRONT))
            front = args.getAsInt(ReplicatingSubscription::QPID_FRONT);
        if (back+1 < front)
            throw Exception(QPID_MSG("Invalid range [" << front << "," << back <<"]"));
    }

    bool empty() const { return front == back+1; }
};


inline std::ostream& operator<<(std::ostream& o, const QueueRange& qr) {
    if (qr.front > qr.back) return o << "[-" << qr.back << "]";
    else return o << "[" << qr.front << "," << qr.back << "]";
}


}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUERANGE_H*/
