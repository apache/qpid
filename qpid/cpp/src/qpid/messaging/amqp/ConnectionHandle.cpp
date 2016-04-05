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
#include "ConnectionHandle.h"
#include "ConnectionContext.h"
#include "SessionHandle.h"
#include "DriverImpl.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/ProtocolRegistry.h"

namespace qpid {
namespace messaging {
namespace amqp {
// Static constructor which registers this implementation in the ProtocolRegistry
namespace {
ConnectionImpl* create(const std::string& u, const qpid::types::Variant::Map& o)
{
    try {
        return new ConnectionHandle(u, o);
    } catch (const types::Exception& ) {
        throw;
    } catch (const qpid::Exception& e) {
        throw messaging::ConnectionError( e.what() );
    }
}

void shutdown() {
    DriverImpl::getDefault()->stop();
}

struct StaticInit
{
    StaticInit()
    {
        ProtocolRegistry::add("amqp1.0", &create, &shutdown);
    };
} init;
}

ConnectionHandle::ConnectionHandle(const std::string& url, const qpid::types::Variant::Map& options) : connection(new ConnectionContext(url, options)) {}
ConnectionHandle::ConnectionHandle(boost::shared_ptr<ConnectionContext> c) : connection(c) {}

void ConnectionHandle::open()
{
    connection->open();
}

bool ConnectionHandle::isOpen() const
{
    return connection->isOpen();
}

void ConnectionHandle::close()
{
    connection->close();
}

Session ConnectionHandle::newSession(bool transactional, const std::string& name)
{
    return qpid::messaging::Session(new SessionHandle(connection, connection->newSession(transactional, name)));
}

Session ConnectionHandle::getSession(const std::string& name) const
{
    return qpid::messaging::Session(new SessionHandle(connection, connection->getSession(name)));
}

void ConnectionHandle::setOption(const std::string& name, const qpid::types::Variant& value)
{
    connection->setOption(name, value);
}

std::string ConnectionHandle::getAuthenticatedUsername()
{
    return connection->getAuthenticatedUsername();
}

void ConnectionHandle::reconnect(const std::string& url)
{
    connection->reconnect(url);
}
void ConnectionHandle::reconnect()
{
    connection->reconnect();
}
std::string ConnectionHandle::getUrl() const
{
    return connection->getUrl();
}

}}} // namespace qpid::messaging::amqp
