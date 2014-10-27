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
#include "qpid/broker/amqp/Exception.h"
#include "qpid/amqp/descriptors.h"
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
        std::string name(id);
        std::string fullName(name);
        if (name.length() >= std::numeric_limits<uint8_t>::max())
            name.resize(std::numeric_limits<uint8_t>::max()-1);
        session = _qmf::Session::shared_ptr(new _qmf::Session(agent, this, broker.GetVhostObject(), name));
        session->set_fullName(fullName);
        session->set_attached(true);
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

bool ManagedSession::isLocal(const OwnershipToken* t) const
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
void ManagedSession::txStarted()
{
    if (session) {
        session->inc_TxnStarts();
    }
}

void ManagedSession::txCommitted()
{
    if (session) {
        session->inc_TxnCommits();
        session->inc_TxnCount();
    }
}

void ManagedSession::txAborted()
{
    if (session) {
        session->inc_TxnRejects();
        session->inc_TxnCount();
    }
}

ManagedConnection& ManagedSession::getParent()
{
    return parent;
}

void ManagedSession::detachedByManagement()
{
    throw Exception(qpid::amqp::error_conditions::NOT_IMPLEMENTED, QPID_MSG(id << "Session detach requested, but not implemented"));
}

qpid::management::Manageable::status_t ManagedSession::ManagementMethod (uint32_t methodId,
                                                                         qpid::management::Args& /*args*/,
                                                                         std::string&  error)
{
    qpid::management::Manageable::status_t status = qpid::management::Manageable::STATUS_UNKNOWN_METHOD;

    try {
        switch (methodId)
        {
          case _qmf::Session::METHOD_DETACH :
            detachedByManagement();
            status = qpid::management::Manageable::STATUS_OK;
            break;

          case _qmf::Session::METHOD_CLOSE :
          case _qmf::Session::METHOD_SOLICITACK :
          case _qmf::Session::METHOD_RESETLIFESPAN :
            status = Manageable::STATUS_NOT_IMPLEMENTED;
            break;
        }
    } catch (const Exception& e) {
        if (e.symbol() == qpid::amqp::error_conditions::NOT_IMPLEMENTED) {
            status = qpid::management::Manageable::STATUS_NOT_IMPLEMENTED;
        } else {
            error = e.what();
            status = qpid::management::Manageable::STATUS_EXCEPTION;
        }
    }

    return status;
}

}}} // namespace qpid::broker::amqp
