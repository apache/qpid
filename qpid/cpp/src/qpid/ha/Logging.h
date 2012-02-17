#ifndef QPID_HA_HAOSTREAM_H
#define QPID_HA_HAOSTREAM_H

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
#include <iosfwd>

/**@file ostream helpers used in log messages. */

namespace qpid {

namespace broker {
class Queue;
class QueuedMessage;
}

namespace framing {
class SequenceNumber;
}

namespace ha {

// Other printable helpers

struct QueuePos {
    const broker::Queue* queue;
    const framing::SequenceNumber& position;
    QueuePos(const broker::Queue* q, const framing::SequenceNumber& pos)
        : queue(q), position(pos) {}
    QueuePos(const broker::QueuedMessage& qm);
};

std::ostream& operator<<(std::ostream& o, const QueuePos& h);

}} // namespace qpid::ha

#endif  /*!QPID_HA_HAOSTREAM_H*/
