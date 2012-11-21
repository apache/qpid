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
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

qpid::sys::ConnectionCodec* ProtocolRegistry::create(const qpid::framing::ProtocolVersion& v, qpid::sys::OutputControl& o, const std::string& id, const qpid::sys::SecuritySettings& s)
{
    qpid::sys::ConnectionCodec* codec = 0;
    for (Protocols::const_iterator i = protocols.begin(); !codec && i != protocols.end(); ++i) {
        codec = i->second->create(v, o, id, s);
    }
    return codec;
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
    boost::shared_ptr<RecoverableMessage> msg;
    for (Protocols::const_iterator i = protocols.begin(); !msg && i != protocols.end(); ++i) {
        msg = i->second->recover(b);
    }
    return msg;
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
