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
#include "qpid/broker/SecureConnectionFactory.h"

#include "qpid/amqp_0_10/Connection.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/SecureConnection.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/SecuritySettings.h"

namespace qpid {
namespace broker {

using framing::ProtocolVersion;
using qpid::sys::SecuritySettings;
typedef std::auto_ptr<qpid::amqp_0_10::Connection> CodecPtr;
typedef std::auto_ptr<SecureConnection> SecureConnectionPtr;
typedef std::auto_ptr<qpid::broker::amqp_0_10::Connection> ConnectionPtr;
typedef std::auto_ptr<sys::ConnectionInputHandler> InputPtr;

SecureConnectionFactory::SecureConnectionFactory(Broker& b) : broker(b) {}

sys::ConnectionCodec*
SecureConnectionFactory::create(ProtocolVersion v, sys::OutputControl& out, const std::string& id,
                                const SecuritySettings& external) {
    if (v == ProtocolVersion(0, 10)) {
        return create_0_10(out, id, external, false);
    } else {
        return broker.getProtocolRegistry().create(v, out, id, external);
    }
    return 0;
}

sys::ConnectionCodec*
SecureConnectionFactory::create(sys::OutputControl& out, const std::string& id,
                                const SecuritySettings& external) {
    // used to create connections from one broker to another
    return create_0_10(out, id, external, true);
}

sys::ConnectionCodec*
SecureConnectionFactory::create_0_10(sys::OutputControl& out, const std::string& id,
                                const SecuritySettings& external, bool brokerActsAsClient)
{
    SecureConnectionPtr sc(new SecureConnection());
    CodecPtr c(new qpid::amqp_0_10::Connection(out, id, brokerActsAsClient));
    ConnectionPtr i(new broker::amqp_0_10::Connection(c.get(), broker, id, external, brokerActsAsClient));
    i->setSecureConnection(sc.get());
    c->setInputHandler(InputPtr(i.release()));
    sc->setCodec(std::auto_ptr<sys::ConnectionCodec>(c));
    return sc.release();
}


}} // namespace qpid::broker
