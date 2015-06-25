#ifndef QPID_BROKER_QUEUEOBSERVER_H
#define QPID_BROKER_QUEUEOBSERVER_H

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
namespace qpid {
namespace broker {

class Consumer;
class Message;

/**
 * Interface for notifying classes who want to act as 'observers' of a queue of particular
 * events.
 *
 * The events that are monitored reflect the relationship between a particular message and
 * the queue it has been delivered to.  A message can be considered in one of three states
 * with respect to the queue:
 *
 * 1) "Available" - available for transfer to consumers (i.e. for browse or acquire),
 *
 * 2) "Acquired" - owned by a particular consumer, no longer available to other consumers
 * (by either browse or acquire), but still considered on the queue.
 *
 * 3) "Dequeued" - removed from the queue and no longer available to any consumer.
 *
 * The queue events that are observable are:
 *
 * "Enqueued" - the message is "Available" - on the queue for transfer to any consumer
 * (e.g. browse or acquire)
 *
 * "Acquired" - - a consumer has claimed exclusive access to it. It is no longer available
 * for other consumers to browse or acquire, but it is not yet considered dequeued as it
 * may be requeued by the consumer.
 *
 * "Requeued" - a previously-acquired message is released by its owner: it is put back on
 * the queue at its original position and returns to the "Available" state.
 *
 * "Dequeued" - a message is no longer queued.  At this point, the queue no longer tracks
 * the message, and the broker considers the consumer's transaction complete.
 */
class QueueObserver
{
  public:
    virtual ~QueueObserver() {}

    // note: the Queue will hold the messageLock while calling these methods!
    virtual void enqueued(const Message&) = 0;
    virtual void dequeued(const Message&) = 0;
    virtual void acquired(const Message&) = 0;
    virtual void requeued(const Message&) = 0;
    virtual void consumerAdded( const Consumer& ) {};
    virtual void consumerRemoved( const Consumer& ) {};
    virtual void destroy() {};
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEOBSERVER_H*/
