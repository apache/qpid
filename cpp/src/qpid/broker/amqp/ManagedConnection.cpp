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

ManagedConnection::ManagedConnection(Broker& broker, const std::string i) : id(i), agent(0)
{
    //management integration:
    agent = broker.getManagementAgent();
    if (agent != 0) {
        qpid::management::Manageable* parent = broker.GetVhostObject();
        // TODO set last bool true if system connection
        connection = _qmf::Connection::shared_ptr(new _qmf::Connection(agent, this, parent, id, true, false, "AMQP 1.0"));
        connection->set_shadow(false);
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

void ManagedConnection::setUserid(const std::string& uid)
{
    userid = uid;
    if (agent && connection) {
        connection->set_authIdentity(userid);
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

void ManagedConnection::setContainerId(const std::string& container)
{
    containerid = container;
    if (connection) {
        qpid::types::Variant::Map props;
        props["container-id"] = containerid;
        connection->set_remoteProperties(props);
    }
}
const std::string& ManagedConnection::getContainerId() const
{
    return containerid;
}

qpid::management::ManagementObject::shared_ptr ManagedConnection::GetManagementObject() const
{
    return connection;
}

std::string ManagedConnection::getId() const { return id; }
std::string ManagedConnection::getUserid() const { return userid; }

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

}}} // namespace qpid::broker::amqp
