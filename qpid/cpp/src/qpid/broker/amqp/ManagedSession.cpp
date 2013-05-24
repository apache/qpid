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
#include "qpid/broker/amqp/ManagedSession.h"
#include "qpid/broker/amqp/ManagedConnection.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/log/Statement.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {

ManagedSession::ManagedSession(Broker& broker, ManagedConnection& p, const std::string i) : parent(p), id(i), unacked(0)
{
    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent != 0) {
        session = _qmf::Session::shared_ptr(new _qmf::Session(agent, this, broker.GetVhostObject(), id));
        session->set_attached(true);
        session->set_detachedLifespan(0);
        session->clr_expireTime();
        session->set_connectionRef(parent.GetManagementObject()->getObjectId());
        agent->addObject(session);
    }
}

ManagedSession::~ManagedSession()
{
    if (session) session->resourceDestroy();
}

qpid::management::ManagementObject::shared_ptr ManagedSession::GetManagementObject() const
{
    return session;
}

bool ManagedSession::isLocal(const ConnectionToken* t) const
{
    return &parent == t;
}

void ManagedSession::outgoingMessageSent()
{
    if (session) session->set_unackedMessages(++unacked);
    parent.outgoingMessageSent();
}
void ManagedSession::outgoingMessageAccepted()
{
    if (session) session->set_unackedMessages(--unacked);
}
void ManagedSession::outgoingMessageRejected()
{
    if (session) session->set_unackedMessages(--unacked);
}

void ManagedSession::incomingMessageReceived()
{
    parent.incomingMessageReceived();
}
void ManagedSession::incomingMessageAccepted()
{

}
void ManagedSession::incomingMessageRejected()
{

}
ManagedConnection& ManagedSession::getParent()
{
    return parent;
}

}}} // namespace qpid::broker::amqp
