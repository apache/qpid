#ifndef QPID_BROKER_PROTOCOL_H
#define QPID_BROKER_PROTOCOL_H

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
#include <map>
#include <string>
#include <set>
#include <boost/shared_ptr.hpp>
#include <boost/intrusive_ptr.hpp>
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/broker/BrokerImportExport.h"

namespace qpid {
namespace sys {
class OutputControl;
struct SecuritySettings;
}
namespace framing {
class Buffer;
class ProtocolVersion;
}
namespace broker {
class Broker;
class Message;
class RecoverableMessage;
namespace amqp_0_10 {
class MessageTransfer;
}

/**
 * A simple abstraction allowing pluggable protocol(s)
 * (versions). AMQP 0-10 is considered the default. Alternatives must
 * provide a ConnectionCodec for encoding/decoding the protocol in
 * full, a means of translating the native message format of that
 * protocol into AMQP 0-10 and a means of recovering durable messages
 * from disk.
 */
class Protocol
{
  public:
    virtual ~Protocol() {}
    virtual qpid::sys::ConnectionCodec* create(const qpid::framing::ProtocolVersion&, qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&) = 0;
    virtual boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> translate(const Message&) = 0;
    virtual boost::shared_ptr<RecoverableMessage> recover(qpid::framing::Buffer&) = 0;
    virtual qpid::framing::ProtocolVersion supportedVersion() const = 0;

  private:
};

class ProtocolRegistry : public Protocol, public qpid::sys::ConnectionCodec::Factory
{
  public:
    QPID_BROKER_EXTERN qpid::sys::ConnectionCodec* create(const qpid::framing::ProtocolVersion&, qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&);
    QPID_BROKER_EXTERN qpid::sys::ConnectionCodec* create(qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&);
    QPID_BROKER_EXTERN qpid::framing::ProtocolVersion supportedVersion() const;
    QPID_BROKER_EXTERN boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> translate(const Message&);
    QPID_BROKER_EXTERN boost::shared_ptr<RecoverableMessage> recover(qpid::framing::Buffer&);
    QPID_BROKER_EXTERN Message decode(qpid::framing::Buffer&);

    QPID_BROKER_EXTERN ProtocolRegistry(const std::set<std::string>& enabled, Broker* b);
    QPID_BROKER_EXTERN ~ProtocolRegistry();
    QPID_BROKER_EXTERN void add(const std::string&, Protocol*);
  private:
    //name may be useful for descriptive purposes or even for some
    //limited manipulation of ordering
    typedef std::map<std::string, Protocol*> Protocols;
    Protocols protocols;
    const std::set<std::string> enabled;
    Broker* broker;

    qpid::sys::ConnectionCodec* create_0_10(qpid::sys::OutputControl&, const std::string&, const qpid::sys::SecuritySettings&, bool);
    bool isEnabled(const std::string&) const;

};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PROTOCOL_H*/
