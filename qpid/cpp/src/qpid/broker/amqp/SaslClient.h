#ifndef QPID_BROKER_AMQP_SASLCLIENT_H
#define QPID_BROKER_AMQP_SASLCLIENT_H

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
#include "qpid/sys/ConnectionCodec.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/amqp/SaslClient.h"
#include <memory>
#include <boost/shared_ptr.hpp>

namespace qpid {
class Sasl;
namespace sys {
class OutputControl;
class SecurityLayer;
}
namespace broker {
class Broker;
namespace amqp {
class Interconnect;

/**
 * Implementation of SASL client role for when broker connects to
 * external peers.
 */
class SaslClient : public qpid::sys::ConnectionCodec, qpid::amqp::SaslClient
{
  public:
    SaslClient(qpid::sys::OutputControl& out, const std::string& id, boost::shared_ptr<Interconnect>, std::auto_ptr<qpid::Sasl>,
               const std::string& hostname, const std::string& allowedMechanisms, const qpid::sys::SecuritySettings&);
    ~SaslClient();
    std::size_t decode(const char* buffer, std::size_t size);
    std::size_t encode(char* buffer, std::size_t size);
    bool canEncode();
    void closed();
    bool isClosed() const;
    qpid::framing::ProtocolVersion getVersion() const;
  private:
    qpid::sys::OutputControl& out;
    boost::shared_ptr<Interconnect> connection;
    std::auto_ptr<qpid::Sasl> sasl;
    std::string hostname;
    std::string allowedMechanisms;
    qpid::sys::SecuritySettings transport;
    bool readHeader;
    bool writeHeader;
    bool haveOutput;
    bool initialised;
    enum {
        NONE, FAILED, SUCCEEDED
    } state;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;

    void mechanisms(const std::string&);
    void challenge(const std::string&);
    void challenge(); //null != empty string
    void outcome(uint8_t result, const std::string&);
    void outcome(uint8_t result);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_SASLCLIENT_H*/
