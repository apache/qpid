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
#include "ManagedIncomingLink.h"
#include "qpid/broker/amqp/ManagedConnection.h"
#include "qpid/broker/amqp/ManagedSession.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/log/Statement.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {

ManagedIncomingLink::ManagedIncomingLink(Broker& broker, ManagedSession& p, const std::string& source, const std::string& target, const std::string& _name)
    : parent(p), name(_name)
{
    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent) {
        incoming = _qmf::Incoming::shared_ptr(new _qmf::Incoming(agent, this, &parent, parent.getParent().getContainerId(), _name, source, target,
                                                                 parent.getParent().getInterconnectDomain()));
        agent->addObject(incoming);
    }
}
ManagedIncomingLink::~ManagedIncomingLink()
{
    if (incoming != 0) incoming->resourceDestroy();
}

qpid::management::ManagementObject::shared_ptr ManagedIncomingLink::GetManagementObject() const
{
    return incoming;
}

void ManagedIncomingLink::incomingMessageReceived()
{
    if (incoming) { incoming->inc_transfers(); }
}
}}} // namespace qpid::broker::amqp
