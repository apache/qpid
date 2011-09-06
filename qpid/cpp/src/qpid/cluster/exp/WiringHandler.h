#ifndef QPID_CLUSTER_WIRINGHANDLER_H
#define QPID_CLUSTER_WIRINGHANDLER_H

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
#include "qpid/broker/RecoveryManagerImpl.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include <boost/intrusive_ptr.hpp>
#include <map>

namespace qpid {

namespace framing {
class FieldTable;
}

namespace broker {
class Broker;
}

namespace cluster {
class EventHandler;
class QueueHandler;

/**
 * Handler for wiring disposition events.
 */
class WiringHandler : public framing::AMQP_AllOperations::ClusterWiringHandler,
                      public HandlerBase
{
  public:
    WiringHandler(EventHandler&, const boost::intrusive_ptr<QueueHandler>& qh);

    bool invoke(const framing::AMQBody& body);

    void createQueue(const std::string& data);
    void destroyQueue(const std::string& name);
    void createExchange(const std::string& data);
    void destroyExchange(const std::string& name);
    void bind(const std::string& queue, const std::string& exchange,
              const std::string& routingKey, const framing::FieldTable& arguments);
    void unbind(const std::string& queue, const std::string& exchange,
                const std::string& routingKey, const framing::FieldTable& arguments);


  private:

    broker::Broker& broker;
    broker::RecoveryManagerImpl recovery;
    boost::intrusive_ptr<QueueHandler> queueHandler;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_WIRINGHANDLER_H*/
