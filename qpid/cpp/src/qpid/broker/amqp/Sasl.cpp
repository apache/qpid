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
#include "qpid/broker/amqp/Sasl.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/sys/SecurityLayer.h"
#include <boost/format.hpp>
#include <vector>

namespace qpid {
namespace broker {
namespace amqp {

Sasl::Sasl(qpid::sys::OutputControl& o, const std::string& id, BrokerContext& context, std::auto_ptr<qpid::SaslServer> auth)
    : qpid::amqp::SaslServer(id), out(o), connection(out, id, context, true, false),
      authenticator(auth),
      state(INCOMPLETE), writeHeader(true), haveOutput(true)
{
    out.activateOutput();
    mechanisms(authenticator->getMechanisms());
}

Sasl::~Sasl() {}

size_t Sasl::decode(const char* buffer, size_t size)
{
    size_t total = 0;
    while (total < size) {
        size_t decoded = 0;
        if (state == AUTHENTICATED || state == SUCCESS_PENDING) {
            if (securityLayer.get()) decoded = securityLayer->decode(buffer+total, size-total);
            else decoded = connection.decode(buffer+total, size-total);
        } else if (state == INCOMPLETE && size) {
            decoded = read(buffer+total, size-total);
            QPID_LOG(trace, id << " Sasl::decode(" << size << "): " << decoded << " (" << total << ")");
        }
        if (decoded) total += decoded;
        else break;
    }
    return total;
}

size_t Sasl::encode(char* buffer, size_t size)
{
    if (state == AUTHENTICATED) {
        if (securityLayer.get()) return securityLayer->encode(buffer, size);
        else return connection.encode(buffer, size);
    } else {
        size_t encoded = 0;
        if (writeHeader) {
            encoded += writeProtocolHeader(buffer, size);
            if (!encoded) return 0;
            writeHeader = false;
        }
        if (encoded < size) {
            encoded += write(buffer + encoded, size - encoded);
        }
        if (state == SUCCESS_PENDING) {
            state = AUTHENTICATED;
        } else if (state == FAILURE_PENDING) {
            state = FAILED;
        } else {
            haveOutput = (encoded == size);
        }
        QPID_LOG(trace, id << " Sasl::encode(" << size << "): " << encoded);
        return encoded;
    }
}

bool Sasl::canEncode()
{
    if (state == AUTHENTICATED) {
        if (securityLayer.get()) return securityLayer->canEncode();
        else return connection.canEncode();
    } else {
        return haveOutput;
    }
}

void Sasl::closed()
{
    if (state == AUTHENTICATED) {
        connection.closed();
    } else {
        QPID_LOG(info, id << " Connection closed prior to authentication completing");
        state = FAILED;
    }
}
bool Sasl::isClosed() const
{
    if (state == AUTHENTICATED) {
        return connection.isClosed();
    } else {
        return state == FAILED;
    }
}

framing::ProtocolVersion Sasl::getVersion() const
{
    return connection.getVersion();
}
namespace {
const std::string EMPTY;
}

void Sasl::init(const std::string& mechanism, const std::string* response, const std::string* /*hostname*/)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-INIT(" << mechanism << ", " << (response ? *response : EMPTY) <<  ")");
    //TODO: what should we do with hostname here?
    std::string c;
    respond(authenticator->start(mechanism, response, c), c);
    connection.setSaslMechanism(mechanism);
}

void Sasl::response(const std::string* r)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-RESPONSE(" << (r ? *r : EMPTY) <<  ")");
    std::string c;
    respond(authenticator->step(r, c), c);
}

void Sasl::respond(qpid::SaslServer::Status status, const std::string& chllnge)
{
    switch (status) {
      case qpid::SaslServer::OK:
        connection.setUserId(authenticator->getUserid());
        completed(true);
        //can't set authenticated & failed until we have actually sent the outcome
        state = SUCCESS_PENDING;
        securityLayer = authenticator->getSecurityLayer(65535);
        if (securityLayer.get()) {
            QPID_LOG_CAT(info, security, id << " Security layer installed");
            securityLayer->init(&connection);
            connection.setSaslSsf(securityLayer->getSsf());
        }
        QPID_LOG_CAT(info, security, id << " Authenticated as " << authenticator->getUserid());
        break;
      case qpid::SaslServer::FAIL:
        completed(false);
        state = FAILURE_PENDING;
        QPID_LOG_CAT(info, security, id << " Failed to authenticate");
        break;
      case qpid::SaslServer::CHALLENGE:
        challenge(&chllnge);
        QPID_LOG_CAT(info, security, id << " Challenge issued");
        break;
    }
    haveOutput = true;
    out.activateOutput();
}
}}} // namespace qpid::broker::amqp
