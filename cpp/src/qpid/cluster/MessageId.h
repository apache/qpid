#ifndef QPID_CLUSTER_MESSAGEID_H
#define QPID_CLUSTER_MESSAGEID_H

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
#include <iosfwd>

namespace qpid {
namespace cluster {

// TODO aconway 2010-10-20: experimental new cluster code.

/** Sequence number used in message identifiers */
typedef uint64_t SequenceNumber;

/**
 * Message identifier
 */
struct MessageId {
    MemberId member;            /// Member that created the message
    SequenceNumber sequence;    /// Sequence number assiged by member.
    MessageId(MemberId m=MemberId(), SequenceNumber s=0) : member(m), sequence(s) {}
};

bool operator<(const MessageId&, const MessageId&);

std::ostream& operator<<(std::ostream&, const MessageId&);


}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_MESSAGEID_H*/
