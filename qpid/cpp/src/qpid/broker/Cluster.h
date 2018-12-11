#ifndef QPID_BROKER_CLUSTER_H
#define QPID_BROKER_CLUSTER_H

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

#include <boost/intrusive_ptr.hpp>

namespace qpid {

namespace framing {
class FieldTable;
}

namespace broker {

class Message;
struct QueuedMessage;
class Queue;
class Exchange;

/**
 * NOTE: this is part of an experimental cluster implementation that is not
 * yet fully functional. The original cluster implementation remains in place.
 * See ../cluster/new-cluster-design.txt
 *
 * Interface for cluster implementations. Functions on this interface are
 * called at relevant points in the Broker's processing.
 */
class Cluster
{
  public:
    virtual ~Cluster() {}

    // Messages

    /** In Exchange::route, before the message is enqueued. */
    virtual void routing(const boost::intrusive_ptr<Message>&) = 0;

    /** A message is delivered to a queue.
     * Called before actually pushing the message to the queue.
     *@return If true the message should be enqueued now, false if it will be enqueued later.
     */
    virtual bool enqueue(Queue& queue, const boost::intrusive_ptr<Message>&) = 0;

    /** In Exchange::route, after all enqueues for the message. */
    virtual void routed(const boost::intrusive_ptr<Message>&) = 0;

    /** A message is acquired by a local consumer, it is unavailable to replicas. */
    virtual void acquire(const QueuedMessage&) = 0;

    /** A locally-acquired message is released by the consumer and re-queued. */
    virtual void requeue(const QueuedMessage&) = 0;

    /** A message is removed from the queue.
     *@return true if the message should be dequeued now, false if it
     * will be dequeued later.
     */
    virtual void dequeue(const QueuedMessage&) = 0;

    // Consumers

    /** A new consumer subscribes to a queue. */
    virtual void consume(Queue&, size_t consumerCount) = 0;
    /** A consumer cancels its subscription to a queue */
    virtual void cancel(Queue&, size_t consumerCount) = 0;

    // Queues

    /** A queue has been stopped */
    virtual void stopped(Queue&) = 0;

    // Wiring

    /** A queue is created */
    virtual void create(Queue&) = 0;
    /** A queue is destroyed */
    virtual void destroy(Queue&) = 0;
    /** An exchange is created */
    virtual void create(Exchange&) = 0;
    /** An exchange is destroyed */
    virtual void destroy(Exchange&) = 0;
    /** A binding is created */
    virtual void bind(Queue&, Exchange&,
                      const std::string& key, const framing::FieldTable& args) = 0;
    /** A binding is removed */
    virtual void unbind(Queue&, Exchange&,
                        const std::string& key, const framing::FieldTable& args) = 0;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CLUSTER_H*/
