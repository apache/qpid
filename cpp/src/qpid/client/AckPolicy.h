#ifndef QPID_CLIENT_ACKPOLICY_H
#define QPID_CLIENT_ACKPOLICY_H

/*
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

#include "qpid/framing/SequenceSet.h"
#include "qpid/client/AsyncSession.h"

namespace qpid {
namespace client {

/**
 * Policy for automatic acknowledgement of messages.
 *
 * \ingroup clientapi
 */
class AckPolicy
{
    framing::SequenceSet accepted;
    size_t interval;
    size_t count;

  public:
    /**
     *@param n: acknowledge every n messages.
     *n==0 means no automatic acknowledgement.
     */
    AckPolicy(size_t n=1) : interval(n), count(n) {}

    void ack(const Message& msg, AsyncSession session) {
        accepted.add(msg.getId());
        if (!interval) return;
        if (--count==0) {
            session.markCompleted(msg.getId(), false, true);        
            session.messageAccept(accepted);
            accepted.clear();
            count = interval;
        } else {
            session.markCompleted(msg.getId(), false, false);        
        }
    }

    void ackOutstanding(AsyncSession session) {
        if (!accepted.empty()) {
            session.messageAccept(accepted);
            accepted.clear();
            session.sendCompletion();
        }
    }
};

}} // namespace qpid::client



#endif  /*!QPID_CLIENT_ACKPOLICY_H*/
