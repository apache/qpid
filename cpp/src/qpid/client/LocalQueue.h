#ifndef QPID_CLIENT_LOCALQUEUE_H
#define QPID_CLIENT_LOCALQUEUE_H

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

#include "qpid/client/Message.h"
#include "qpid/client/Demux.h"
#include "qpid/client/AckPolicy.h"

namespace qpid {
namespace client {

/**
 * Local representation of a remote queue.
 *
 * \ingroup clientapi
 */
class LocalQueue
{
  public:
    LocalQueue(AckPolicy=AckPolicy());
    ~LocalQueue();

    /** Pop the next message off the queue.
     *@exception ClosedException if subscription has been closed.
     */
    Message pop();
    bool empty() const;
    size_t size() const;
    void setAckPolicy(AckPolicy);

  private:
  friend class SubscriptionManager;
    Session_0_10 session;
    Demux::QueuePtr queue;
    AckPolicy autoAck;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_LOCALQUEUE_H*/
