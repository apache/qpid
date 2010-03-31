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
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/messaging/ConnectionImpl.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/SessionImpl.h"
#include "qpid/messaging/PrivateImplRef.h"
#include "qpid/client/amqp0_10/ConnectionImpl.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace messaging {

using namespace qpid::types;

typedef PrivateImplRef<qpid::messaging::Connection> PI;

Connection::Connection(ConnectionImpl* impl) { PI::ctor(*this, impl); }
Connection::Connection(const Connection& c) : Handle<ConnectionImpl>() { PI::copy(*this, c); }
Connection& Connection::operator=(const Connection& c) { return PI::assign(*this, c); }
Connection::~Connection() { PI::dtor(*this); }

Connection::Connection(const std::string& o)
{ 
    Variant::Map options;
    AddressParser parser(o);
    if (o.empty() || parser.parseMap(options)) {
        PI::ctor(*this, new qpid::client::amqp0_10::ConnectionImpl(options));
    } else {
        throw InvalidOptionString(o);
    }
}
Connection::Connection(const Variant::Map& options)
{
    PI::ctor(*this, new qpid::client::amqp0_10::ConnectionImpl(options));
}

void Connection::open(const std::string& url) { impl->open(url); }
void Connection::close() { impl->close(); }
Session Connection::createSession(const std::string& name) { return impl->newSession(false, name); }
Session Connection::createTransactionalSession(const std::string& name)
{ 
    return impl->newSession(true, name);
}
Session Connection::getSession(const std::string& name) const { return impl->getSession(name); }
void Connection::setOption(const std::string& name, const Variant& value)
{ 
    impl->setOption(name, value);
}

InvalidOptionString::InvalidOptionString(const std::string& msg) : Exception(msg) {}

}} // namespace qpid::messaging
