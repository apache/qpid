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
#include "ManagedOutgoingLink.h"
#include "qpid/broker/amqp/ManagedSession.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/types/Variant.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/log/Statement.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {

ManagedOutgoingLink::ManagedOutgoingLink(Broker& broker, Queue& q, ManagedSession& p, const std::string i, bool topic)
    : parent(p), id(i)
{
    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent) {
        subscription = _qmf::Subscription::shared_ptr(new _qmf::Subscription(agent, this, &p, q.GetManagementObject()->getObjectId(), id,
                                                           false/*FIXME*/, true/*FIXME*/, topic, qpid::types::Variant::Map()));
        agent->addObject(subscription);
        subscription->set_creditMode("n/a");
    }
}
ManagedOutgoingLink::~ManagedOutgoingLink()
{
    if (subscription != 0) subscription->resourceDestroy();
}

qpid::management::ManagementObject::shared_ptr ManagedOutgoingLink::GetManagementObject() const
{
    return subscription;
}

void ManagedOutgoingLink::outgoingMessageSent()
{
    if (subscription) { subscription->inc_delivered(); }
    parent.outgoingMessageSent();
}
void ManagedOutgoingLink::outgoingMessageAccepted()
{
    parent.outgoingMessageAccepted();
}
void ManagedOutgoingLink::outgoingMessageRejected()
{
    parent.outgoingMessageRejected();
}

}}} // namespace qpid::broker::amqp
