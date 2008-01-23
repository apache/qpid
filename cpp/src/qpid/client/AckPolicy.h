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

namespace qpid {
namespace client {

/**
 * Policy for automatic acknowledgement of messages.
 *
 * \ingroup clientapi
 */
class AckPolicy
{
    size_t interval;
    size_t count;

  public:
    /**
     *@param n: acknowledge every n messages.
     *n==0 means no automatick acknowledgement.
     */
    AckPolicy(size_t n=1) : interval(n), count(n) {}

    void ack(const Message& msg) {
        if (!interval) return;
        bool send=(--count==0);
        msg.acknowledge(true, send);
        if (send) count = interval;
    }
};

}} // namespace qpid::client



#endif  /*!QPID_CLIENT_ACKPOLICY_H*/
