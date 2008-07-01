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
#include "qpid/sys/Time.h"

namespace qpid {
namespace client {

/**
 * A local queue to collect messages retrieved from a remote broker
 * queue. Create a queue and subscribe it using the SubscriptionManager.
 * Messages from the remote queue on the broker will be stored in the
 * local queue until you retrieve them.
 *
 * \ingroup clientapi
 */
class LocalQueue
{
  public:
    /** Create a local queue. Subscribe the local queue to a remote broker
     * queue with a SubscriptionManager.
     *
     * LocalQueue is an alternative to implementing a MessageListener.
     * 
     *@param ackPolicy Policy for acknowledging messages. @see AckPolicy.
     */
    LocalQueue(AckPolicy ackPolicy=AckPolicy());

    ~LocalQueue();

    /** Wait up to timeout for the next message from the local queue.
     *@param result Set to the message from the queue.
     *@param timeout wait up this timeout for a message to appear. 
     *@return true if result was set, false if queue was empty after timeout.
     */
    bool get(Message& result, sys::Duration timeout=0);

    /** Get the next message off the local queue, or wait for a
     * message from the broker queue.
     *@exception ClosedException if subscription has been closed.
     */
    Message get();

    /** Synonym for get(). */
    Message pop();

    /** Return true if local queue is empty. */
    bool empty() const;

    /** Number of messages on the local queue */
    size_t size() const;

    /** Set the message acknowledgement policy. @see AckPolicy. */
    void setAckPolicy(AckPolicy);

    /** Get the message acknowledgement policy. @see AckPolicy. */
    AckPolicy& getAckPolicy();

  private:
    Session session;
    Demux::QueuePtr queue;
    AckPolicy autoAck;

  friend class SubscriptionManager;
};

}} // namespace qpid::client

#endif  /*!QPID_CLIENT_LOCALQUEUE_H*/
