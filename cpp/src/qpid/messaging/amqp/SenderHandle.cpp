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
#include "SenderHandle.h"
#include "ConnectionContext.h"
#include "SessionContext.h"
#include "SessionHandle.h"
#include "SenderContext.h"
#include "qpid/messaging/Duration.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/Session.h"

namespace qpid {
namespace messaging {
namespace amqp {

SenderHandle::SenderHandle(boost::shared_ptr<ConnectionContext> c,
             boost::shared_ptr<SessionContext> s,
             boost::shared_ptr<SenderContext> sndr
) : connection(c), session(s), sender(sndr) {}

void SenderHandle::send(const Message& message, bool sync)
{
    SenderContext::Delivery* d = 0;
    connection->send(session, sender, message, sync, &d);
}

void SenderHandle::close()
{
    connection->detach(session, sender);
}

void SenderHandle::setCapacity(uint32_t capacity)
{
    connection->setCapacity(sender, capacity);
}

uint32_t SenderHandle::getCapacity()
{
    return connection->getCapacity(sender);
}

uint32_t SenderHandle::getUnsettled()
{
    return connection->getUnsettled(sender);
}

const std::string& SenderHandle::getName() const
{
    return sender->getName();
}

qpid::messaging::Session SenderHandle::getSession() const
{
    return qpid::messaging::Session(new SessionHandle(connection, session));
}

Address SenderHandle::getAddress() const
{
    return sender->getAddress();
}

}}} // namespace qpid::messaging::amqp
