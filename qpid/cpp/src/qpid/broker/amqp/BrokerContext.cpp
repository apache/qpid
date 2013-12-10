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
#include "BrokerContext.h"

namespace qpid {
namespace broker {
namespace amqp {
BrokerContext::BrokerContext(Broker& b, Interconnects& i, TopicRegistry& t, NodePolicyRegistry& np, const std::string& d) : broker(b), interconnects(i), topics(t), nodePolicies(np), domain(d) {}
BrokerContext::BrokerContext(BrokerContext& c) : broker(c.broker), interconnects(c.interconnects), topics(c.topics), nodePolicies(c.nodePolicies), domain(c.domain) {}
Broker& BrokerContext::getBroker() { return broker; }
Interconnects& BrokerContext::getInterconnects() { return interconnects; }
TopicRegistry& BrokerContext::getTopics() { return topics; }
NodePolicyRegistry& BrokerContext::getNodePolicies() { return nodePolicies; }
std::string BrokerContext::getDomain() { return domain; }
}}} // namespace qpid::broker::amqp
