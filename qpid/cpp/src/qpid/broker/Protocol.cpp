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
#include "Protocol.h"
#include "qpid/broker/RecoverableMessageImpl.h"
#include "qpid/amqp_0_10/Connection.h"
#include "qpid/broker/amqp_0_10/Connection.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/broker/SecureConnection.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace {
const std::string AMQP_0_10("amqp0-10");
}
ProtocolRegistry::ProtocolRegistry(const std::set<std::string>& e, Broker* b) : enabled(e), broker(b) {}

qpid::sys::ConnectionCodec* ProtocolRegistry::create(const qpid::framing::ProtocolVersion& v, qpid::sys::OutputControl& o, const std::string& id, const qpid::sys::SecuritySettings& s)
{
    if (v == qpid::framing::ProtocolVersion(0, 10)) {
        if (isEnabled(AMQP_0_10)) {
            return create_0_10(o, id, s, false);
        }
    }
    qpid::sys::ConnectionCodec* codec = 0;
    for (Protocols::const_iterator i = protocols.begin(); !codec && i != protocols.end(); ++i) {
        if (isEnabled(i->first)) {
            codec = i->second->create(v, o, id, s);
        }
    }
    return codec;
}
qpid::sys::ConnectionCodec* ProtocolRegistry::create(qpid::sys::OutputControl& o, const std::string& id, const qpid::sys::SecuritySettings& s)
{
    return create_0_10(o, id, s, true);
}

qpid::framing::ProtocolVersion ProtocolRegistry::supportedVersion() const
{
    if (isEnabled(AMQP_0_10)) {
        return qpid::framing::ProtocolVersion(0,10);
    } else {
        for (Protocols::const_iterator i = protocols.begin(); i != protocols.end(); ++i) {
            if (isEnabled(i->first)) {
                return i->second->supportedVersion();
            }
        }
    }
    QPID_LOG(error, "No enabled protocols!");
    return qpid::framing::ProtocolVersion(0,0);
}

bool ProtocolRegistry::isEnabled(const std::string& name) const
{
    return enabled.empty()/*if nothing is explicitly enabled, assume everything is*/ || enabled.find(name) != enabled.end();
}


typedef std::auto_ptr<qpid::amqp_0_10::Connection> CodecPtr;
typedef std::auto_ptr<SecureConnection> SecureConnectionPtr;
typedef std::auto_ptr<qpid::broker::amqp_0_10::Connection> ConnectionPtr;
typedef std::auto_ptr<sys::ConnectionInputHandler> InputPtr;

sys::ConnectionCodec*
ProtocolRegistry::create_0_10(qpid::sys::OutputControl& out, const std::string& id,
                              const qpid::sys::SecuritySettings& external, bool brokerActsAsClient)
{
    assert(broker);
    SecureConnectionPtr sc(new SecureConnection());
    CodecPtr c(new qpid::amqp_0_10::Connection(out, id, brokerActsAsClient));
    ConnectionPtr i(new broker::amqp_0_10::Connection(c.get(), *broker, id, external, brokerActsAsClient));
    i->setSecureConnection(sc.get());
    c->setInputHandler(InputPtr(i.release()));
    sc->setCodec(std::auto_ptr<sys::ConnectionCodec>(c));
    return sc.release();
}

boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> ProtocolRegistry::translate(const Message& m)
{
    boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> transfer;
    const qpid::broker::amqp_0_10::MessageTransfer* ptr = dynamic_cast<const qpid::broker::amqp_0_10::MessageTransfer*>(&m.getEncoding());
    if (ptr) transfer = boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer>(ptr);
    for (Protocols::const_iterator i = protocols.begin(); !transfer && i != protocols.end(); ++i) {
        transfer = i->second->translate(m);
    }
    if (!transfer) throw new Exception("Could not convert message into 0-10");
    return transfer;
}
boost::shared_ptr<RecoverableMessage> ProtocolRegistry::recover(qpid::framing::Buffer& b)
{
    uint32_t position = b.getPosition();
    for (Protocols::const_iterator i = protocols.begin(); i != protocols.end(); ++i) {
        boost::shared_ptr<RecoverableMessage> msg = i->second->recover(b);
        if (msg) return msg;
        else b.setPosition(position);
    }
    boost::intrusive_ptr<qpid::broker::amqp_0_10::MessageTransfer> transfer(new qpid::broker::amqp_0_10::MessageTransfer());
    transfer->decodeHeader(b);
    boost::shared_ptr<RecoverableMessage> msg(new RecoverableMessageImpl(Message(transfer, transfer)));
    return msg;
}

Message ProtocolRegistry::decode(qpid::framing::Buffer& buffer)
{
    boost::shared_ptr<RecoverableMessage> r = recover(buffer);
    r->decodeContent(buffer);
    return r->getMessage();
}

ProtocolRegistry::~ProtocolRegistry()
{
    for (Protocols::const_iterator i = protocols.begin(); i != protocols.end(); ++i) {
        delete i->second;
    }
    protocols.clear();
}
void ProtocolRegistry::add(const std::string& key, Protocol* protocol)
{
    protocols[key] = protocol;
    QPID_LOG(info, "Loaded protocol " << key);
}

}} // namespace qpid::broker
