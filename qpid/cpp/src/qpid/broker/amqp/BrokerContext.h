#ifndef QPID_BROKER_AMQP_BROKERCONTEXT_H
#define QPID_BROKER_AMQP_BROKERCONTEXT_H

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
#include <string>

namespace qpid {
namespace broker {
class Broker;
namespace amqp {
class Interconnects;
class TopicRegistry;
class NodePolicyRegistry;
/**
 * Context providing access to broker scoped entities.
 */
class BrokerContext
{
  public:
    BrokerContext(Broker&, Interconnects&, TopicRegistry&, NodePolicyRegistry&, const std::string&);
    BrokerContext(BrokerContext&);
    Broker& getBroker();
    Interconnects& getInterconnects();
    TopicRegistry& getTopics();
    NodePolicyRegistry& getNodePolicies();
    std::string getDomain();
  private:
    Broker& broker;
    Interconnects& interconnects;
    TopicRegistry& topics;
    NodePolicyRegistry& nodePolicies;
    std::string domain;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_BROKERCONTEXT_H*/
