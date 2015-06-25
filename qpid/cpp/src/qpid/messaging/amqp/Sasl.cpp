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
#include "ConnectionContext.h"
#include "qpid/messaging/amqp/Sasl.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/log/Statement.h"
#include "qpid/Sasl.h"
#include "qpid/SaslFactory.h"
#include "qpid/StringUtils.h"
#include <sstream>

namespace qpid {
namespace messaging {
namespace amqp {

Sasl::Sasl(const std::string& id, ConnectionContext& c, const std::string& hostname_)
    : qpid::amqp::SaslClient(id), context(c),
      sasl(qpid::SaslFactory::getInstance().create(c.username, c.password, c.service, hostname_, c.minSsf, c.maxSsf, false)),
      hostname(hostname_), readHeader(true), writeHeader(true), haveOutput(false), state(NONE) {}

Sasl::~Sasl() {}
std::size_t Sasl::decode(const char* buffer, std::size_t size)
{
    size_t decoded = 0;
    if (readHeader) {
        decoded += readProtocolHeader(buffer, size);
        readHeader = !decoded;
    }
    if (state == NONE && decoded < size) {
        decoded += read(buffer + decoded, size - decoded);
    }
    QPID_LOG(trace, id << " Sasl::decode(" << size << "): " << decoded);
    return decoded;
}

std::size_t Sasl::encode(char* buffer, std::size_t size)
{
    size_t encoded = 0;
    if (writeHeader) {
         encoded += writeProtocolHeader(buffer, size);
         writeHeader = !encoded;
    }
    if (encoded < size) {
        encoded += write(buffer + encoded, size - encoded);
    }
    haveOutput = (encoded == size);
    QPID_LOG(trace, id << " Sasl::encode(" << size << "): " << encoded);
    return encoded;
}

bool Sasl::canEncode()
{
    QPID_LOG(trace, id << " Sasl::canEncode(): " << writeHeader << " || " << haveOutput);
    return writeHeader || haveOutput;
}

void Sasl::mechanisms(const std::string& offered)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-MECHANISMS(" << offered << ")");
    std::string response;

    std::string mechanisms;
    if (context.mechanism.size()) {
        std::vector<std::string> allowed = split(context.mechanism, " ");
        std::vector<std::string> supported = split(offered, " ");
        std::stringstream intersection;
        for (std::vector<std::string>::const_iterator i = allowed.begin(); i != allowed.end(); ++i) {
            if (std::find(supported.begin(), supported.end(), *i) != supported.end()) {
                intersection << *i << " ";
            }
        }
        mechanisms = intersection.str();
    } else {
        mechanisms = offered;
    }

    try {
        if (sasl->start(mechanisms, response, context.getTransportSecuritySettings())) {
            init(sasl->getMechanism(), &response, hostname.size() ? &hostname : 0);
        } else {
            init(sasl->getMechanism(), 0, hostname.size() ? &hostname : 0);
        }
        haveOutput = true;
        context.activateOutput();
    } catch (const std::exception& e) {
        failed(e.what());
    }
}
void Sasl::challenge(const std::string& challenge)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-CHALLENGE(" << challenge.size() << " bytes)");
    try {
        std::string r = sasl->step(challenge);
        response(&r);
        haveOutput = true;
        context.activateOutput();
    } catch (const std::exception& e) {
        failed(e.what());
    }
}
namespace {
const std::string EMPTY;
}
void Sasl::challenge()
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-CHALLENGE(null)");
    try {
        std::string r = sasl->step(EMPTY);
        response(&r);
    } catch (const std::exception& e) {
        failed(e.what());
    }
}
void Sasl::outcome(uint8_t result, const std::string& extra)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-OUTCOME(" << result << ", " << extra << ")");
    outcome(result);
}
void Sasl::outcome(uint8_t result)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-OUTCOME(" << result << ")");
    if (result) state = FAILED;
    else state = SUCCEEDED;

    securityLayer = sasl->getSecurityLayer(context.maxFrameSize);
    if (securityLayer.get()) {
        context.initSecurityLayer(*securityLayer);
    }
    context.activateOutput();
}

bool Sasl::stopReading()
{
    return state != NONE;
}

qpid::sys::Codec* Sasl::getSecurityLayer()
{
    return securityLayer.get();
}

namespace {
const std::string DEFAULT_ERROR("Authentication failed");
}

bool Sasl::authenticated()
{
    switch (state) {
      case SUCCEEDED: return true;
      case FAILED: throw qpid::messaging::AuthenticationFailure(error.size() ? error : DEFAULT_ERROR);
      case NONE: default: return false;
    }
}

void Sasl::failed(const std::string& text)
{
    QPID_LOG_CAT(info, client, id << " Failure during authentication: " << text);
    error = text;
    state = FAILED;
}

std::string Sasl::getAuthenticatedUsername()
{
    return sasl->getUserId();
}

}}} // namespace qpid::messaging::amqp
