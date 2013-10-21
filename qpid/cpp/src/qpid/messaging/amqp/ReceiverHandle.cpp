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
#include "ReceiverHandle.h"
#include "ConnectionContext.h"
#include "SessionContext.h"
#include "SessionHandle.h"
#include "ReceiverContext.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Session.h"

namespace qpid {
namespace messaging {
namespace amqp {

ReceiverHandle::ReceiverHandle(boost::shared_ptr<ConnectionContext> c,
             boost::shared_ptr<SessionContext> s,
             boost::shared_ptr<ReceiverContext> r
) : connection(c), session(s), receiver(r) {}


bool ReceiverHandle::get(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    return connection->get(session, receiver, message, timeout);
}

qpid::messaging::Message ReceiverHandle::get(qpid::messaging::Duration timeout)
{
    qpid::messaging::Message result;
    if (!get(result, timeout)) throw qpid::messaging::NoMessageAvailable();
    return result;
}

bool ReceiverHandle::fetch(qpid::messaging::Message& message, qpid::messaging::Duration timeout)
{
    return connection->fetch(session, receiver, message, timeout);
}

qpid::messaging::Message ReceiverHandle::fetch(qpid::messaging::Duration timeout)
{
    qpid::messaging::Message result;
    if (!fetch(result, timeout)) throw qpid::messaging::NoMessageAvailable();
    return result;
}

void ReceiverHandle::setCapacity(uint32_t capacity)
{
    connection->setCapacity(receiver, capacity);
}

uint32_t ReceiverHandle::getCapacity()
{
    return connection->getCapacity(receiver);
}

uint32_t ReceiverHandle::getAvailable()
{
    return connection->getAvailable(receiver);
}

uint32_t ReceiverHandle::getUnsettled()
{
    return connection->getUnsettled(receiver);
}

void ReceiverHandle::close()
{
    connection->detach(session, receiver);
}

const std::string& ReceiverHandle::getName() const
{
    return receiver->getName();
}

qpid::messaging::Session ReceiverHandle::getSession() const
{
    //create new SessionHandle instance; i.e. create new handle that shares the same context
    return qpid::messaging::Session(new SessionHandle(connection, session));
}

bool ReceiverHandle::isClosed() const
{
    return connection->isClosed(session, receiver);
}

Address ReceiverHandle::getAddress() const
{
    return receiver->getAddress();
}

}}} // namespace qpid::messaging::amqp
