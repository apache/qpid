#ifndef QPID_CLUSTER_MESSAGEHANDLER_H
#define QPID_CLUSTER_MESSAGEHANDLER_H

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

// TODO aconway 2010-10-19: experimental cluster code.

#include "HandlerBase.h"
#include "MessageBuilders.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {

namespace broker {
class Message;
class Broker;
class Queue;
}

namespace cluster {
class EventHandler;
class BrokerContext;
class Core;

// FIXME aconway 2011-06-28: doesn't follow the same Handler/Replica/Context pattern as for queue.
// Make this consistent.

/**
 * Handler for message disposition events.
 */
class MessageHandler : public framing::AMQP_AllOperations::ClusterMessageHandler,
                       public HandlerBase
{
  public:
    MessageHandler(EventHandler&, Core&);

    bool handle(const framing::AMQFrame&);

    void enqueue(const std::string& queue, uint16_t channel);
    void acquire(const std::string& queue, uint32_t position);
    void dequeue(const std::string& queue, uint32_t position);
    void requeue(const std::string& queue, uint32_t position, bool redelivered);

  private:
    boost::shared_ptr<broker::Queue> findQueue(const std::string& q, const char* msg);

    broker::Broker& broker;
    Core& core;
    MessageBuilders messageBuilders;
};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_MESSAGEHANDLER_H*/
