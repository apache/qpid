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

#include "qpid/framing/AMQP_AllOperations.h"
#include "MessageId.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {

namespace broker {
class Message;
class Broker;
}

namespace cluster {
class EventHandler;
class BrokerHandler;

/**
 * Handler for message disposition events.
 */
class MessageHandler : public framing::AMQP_AllOperations::ClusterMessageHandler
{
  public:
    MessageHandler(EventHandler&);
    ~MessageHandler();

    void routing(uint64_t sequence, const std::string& message);
    void enqueue(uint64_t sequence, const std::string& queue);
    void routed(uint64_t sequence);

  private:
    typedef std::map<MessageId, boost::intrusive_ptr<broker::Message> > RoutingMap;

    MemberId sender();
    MemberId self();

    broker::Broker& broker;
    EventHandler& eventHandler;
    BrokerHandler& brokerHandler;
    RoutingMap routingMap;

};
}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_MESSAGEHANDLER_H*/
