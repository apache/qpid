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
#include "qpid/broker/amqp/ManagedConnection.h"
#include "qpid/broker/amqp/Exception.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"
#include "qmf/org/apache/qpid/broker/EventClientConnect.h"
#include "qmf/org/apache/qpid/broker/EventClientDisconnect.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {
namespace {
const std::string CLIENT_PROCESS_NAME("qpid.client_process");
const std::string CLIENT_PID("qpid.client_pid");
const std::string CLIENT_PPID("qpid.client_ppid");
template <typename T> T getProperty(const std::string& key, const qpid::types::Variant::Map& props, T defaultValue)
{
    qpid::types::Variant::Map::const_iterator i = props.find(key);
    if (i != props.end()) {
        return i->second;
    } else {
        return defaultValue;
    }
}
}
ManagedConnection::ManagedConnection(Broker& broker, const std::string i, bool brokerInitiated) : id(i), agent(0)
{
    //management integration:
    agent = broker.getManagementAgent();
    if (agent != 0) {
        qpid::management::Manageable* parent = broker.GetVhostObject();
        connection = _qmf::Connection::shared_ptr(new _qmf::Connection(agent, this, parent, id, !brokerInitiated, false, "AMQP 1.0"));
        agent->addObject(connection);
    }
}

ManagedConnection::~ManagedConnection()
{
    if (agent && connection) {
        agent->raiseEvent(_qmf::EventClientDisconnect(id, userid, connection->get_remoteProperties()));
        connection->resourceDestroy();
    }
    QPID_LOG_CAT(debug, model, "Delete connection. user:" << userid << " rhost:" << id);
}

void ManagedConnection::setUserId(const std::string& uid)
{
    userid = uid;
    if (connection) {
        connection->set_authIdentity(userid);
    }
}

void ManagedConnection::opened()
{
    if (agent) {
        agent->raiseEvent(_qmf::EventClientConnect(id, userid, connection->get_remoteProperties()));
    }
    QPID_LOG_CAT(debug, model, "Create connection. user:" << userid << " rhost:" << id );
}

void ManagedConnection::setSaslMechanism(const std::string& mechanism)
{
    if (connection) {
        connection->set_saslMechanism(mechanism);
    }
}

void ManagedConnection::setSaslSsf(int ssf)
{
    if (connection) {
        connection->set_saslSsf(ssf);
    }
}

void ManagedConnection::setPeerProperties(std::map<std::string, types::Variant>& p)
{
    peerProperties = p;
    if (connection) {
        connection->set_remoteProperties(peerProperties);

        std::string procName = getProperty(CLIENT_PROCESS_NAME, peerProperties, std::string());
        uint32_t pid = getProperty(CLIENT_PID, peerProperties, 0);
        uint32_t ppid = getProperty(CLIENT_PPID, peerProperties, 0);

        if (!procName.empty())
            connection->set_remoteProcessName(procName);
        if (pid != 0)
            connection->set_remotePid(pid);
        if (ppid != 0)
            connection->set_remoteParentPid(ppid);

    }
}

void ManagedConnection::setContainerId(const std::string& container)
{
    containerid = container;
    peerProperties["container-id"] = containerid;
    if (connection) {
        connection->set_remoteProperties(peerProperties);
    }
}
const std::string& ManagedConnection::getContainerId() const
{
    return containerid;
}

void ManagedConnection::setInterconnectDomain(const std::string& d)
{
    domain = d;
}
const std::string& ManagedConnection::getInterconnectDomain() const
{
    return domain;
}

qpid::management::ManagementObject::shared_ptr ManagedConnection::GetManagementObject() const
{
    return connection;
}

std::string ManagedConnection::getId() const { return id; }

const management::ObjectId ManagedConnection::getObjectId() const
{
    return GetManagementObject()->getObjectId();
}
const std::string& ManagedConnection::getUserId() const
{
    return userid;
}
const std::string& ManagedConnection::getMgmtId() const
{
    return id;
}
const std::map<std::string, types::Variant>& ManagedConnection::getClientProperties() const
{
    return connection->get_remoteProperties();
}
bool ManagedConnection::isLink() const
{
    return false;
}

bool ManagedConnection::isLocal(const OwnershipToken* t) const
{
    return this == t;
}
void ManagedConnection::outgoingMessageSent()
{
    if (connection) connection->inc_msgsToClient();
}

void ManagedConnection::incomingMessageReceived()
{
    if (connection) connection->inc_msgsFromClient();
}

void ManagedConnection::closedByManagement()
{
    throw Exception(qpid::amqp::error_conditions::NOT_IMPLEMENTED, QPID_MSG(id << "Connection close requested, but not implemented"));
}

qpid::management::Manageable::status_t ManagedConnection::ManagementMethod(uint32_t methodId, qpid::management::Args&, std::string& error)
{
    qpid::management::Manageable::status_t status = qpid::management::Manageable::STATUS_UNKNOWN_METHOD;

    try {
        switch (methodId)
        {
          case _qmf::Connection::METHOD_CLOSE :
            closedByManagement();
            if (connection) connection->set_closing(true);
            status = qpid::management::Manageable::STATUS_OK;
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
