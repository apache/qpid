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
#include "SessionHandle.h"
#include "ConnectionContext.h"
#include "ConnectionHandle.h"
#include "ReceiverContext.h"
#include "ReceiverHandle.h"
#include "SenderContext.h"
#include "SenderHandle.h"
#include "SessionContext.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace messaging {
namespace amqp {

SessionHandle::SessionHandle(boost::shared_ptr<ConnectionContext> c, boost::shared_ptr<SessionContext> s) : connection(c), session(s) {}

void SessionHandle::commit()
{
    connection->commit(session);
}

void SessionHandle::rollback()
{
    connection->rollback(session);
}

void SessionHandle::acknowledge(bool /*sync*/)
{
    connection->acknowledge(session, 0, false);
}

void SessionHandle::acknowledge(qpid::messaging::Message& msg, bool cumulative)
{
    connection->acknowledge(session, &msg, cumulative);
}

void SessionHandle::reject(qpid::messaging::Message& msg)
{
    connection->nack(session, msg, true);
}

void SessionHandle::release(qpid::messaging::Message& msg)
{
    connection->nack(session, msg, false);
}

void SessionHandle::close()
{
    connection->endSession(session);
}

void SessionHandle::sync(bool block)
{
    if (block) {
        connection->sync(session);
    }
}

qpid::messaging::Sender SessionHandle::createSender(const qpid::messaging::Address& address)
{
    boost::shared_ptr<SenderContext> sender = connection->createSender(session, address);
    return qpid::messaging::Sender(new SenderHandle(connection, session, sender));
}

qpid::messaging::Receiver SessionHandle::createReceiver(const qpid::messaging::Address& address)
{
    boost::shared_ptr<ReceiverContext> receiver = connection->createReceiver(session, address);
    return qpid::messaging::Receiver(new ReceiverHandle(connection, session, receiver));
}

bool SessionHandle::nextReceiver(Receiver& receiver, Duration timeout)
{
    boost::shared_ptr<ReceiverContext> r = connection->nextReceiver(session, timeout);
    if (r) {
        //TODO: cache handles in this case to avoid frequent allocation
        receiver = qpid::messaging::Receiver(new ReceiverHandle(connection, session, r));
        return true;
    } else {
        return false;
    }
}

qpid::messaging::Receiver SessionHandle::nextReceiver(Duration timeout)
{
    qpid::messaging::Receiver r;
    if (nextReceiver(r, timeout)) return r;
    else throw qpid::messaging::NoMessageAvailable();
}

uint32_t SessionHandle::getReceivable()
{
    return session->getReceivable();
}

uint32_t SessionHandle::getUnsettledAcks()
{
    return session->getUnsettledAcks();
}

Sender SessionHandle::getSender(const std::string& name) const
{
    return qpid::messaging::Sender(new SenderHandle(connection, session, connection->getSender(session, name)));
}

Receiver SessionHandle::getReceiver(const std::string& name) const
{
    return qpid::messaging::Receiver(new ReceiverHandle(connection, session, connection->getReceiver(session, name)));
}

Connection SessionHandle::getConnection() const
{
    return qpid::messaging::Connection(new ConnectionHandle(connection));
}

void SessionHandle::checkError()
{

}


}}} // namespace qpid::messaging::amqp
