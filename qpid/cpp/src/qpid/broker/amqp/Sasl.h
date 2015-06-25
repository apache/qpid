#ifndef QPID_BROKER_AMQP_SASL_H
#define QPID_BROKER_AMQP_SASL_H

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
#include "qpid/broker/amqp/Connection.h"
#include "qpid/SaslServer.h"
#include "qpid/amqp/SaslServer.h"
#include "qpid/sys/ConnectionCodec.h"
#include <memory>
namespace qpid {
namespace sys {
class SecurityLayer;
}
namespace broker {
namespace amqp {
/**
 * An AMQP 1.0 SASL Security Layer for authentication and optionally
 * encryption.
 */
class Sasl : public sys::ConnectionCodec, qpid::amqp::SaslServer
{
  public:
    Sasl(qpid::sys::OutputControl& out, const std::string& id, BrokerContext& context, std::auto_ptr<qpid::SaslServer> authenticator);
    ~Sasl();

    size_t decode(const char* buffer, size_t size);
    size_t encode(char* buffer, size_t size);
    bool canEncode();

    void closed();
    bool isClosed() const;

    framing::ProtocolVersion getVersion() const;
  private:
    qpid::sys::OutputControl& out;
    Connection connection;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;
    std::auto_ptr<qpid::SaslServer> authenticator;
    enum {
        INCOMPLETE, SUCCESS_PENDING, FAILURE_PENDING, AUTHENTICATED, FAILED
    } state;

    bool writeHeader;
    bool haveOutput;

    void init(const std::string& mechanism, const std::string* response, const std::string* hostname);
    void response(const std::string*);
    void respond(qpid::SaslServer::Status status, const std::string& challenge);
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_SASL_H*/
