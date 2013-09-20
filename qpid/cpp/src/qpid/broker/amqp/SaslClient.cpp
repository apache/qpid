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
#include "SaslClient.h"
#include "Interconnect.h"
#include "qpid/broker/Broker.h"
#include "qpid/sys/OutputControl.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/log/Statement.h"
#include "qpid/Sasl.h"
#include "qpid/SaslFactory.h"
#include "qpid/StringUtils.h"
#include <sstream>

namespace qpid {
namespace broker {
namespace amqp {

SaslClient::SaslClient(qpid::sys::OutputControl& out_, const std::string& id, boost::shared_ptr<Interconnect> c, std::auto_ptr<qpid::Sasl> s,
                       const std::string& hostname_, const std::string& mechs, const qpid::sys::SecuritySettings& t)
    : qpid::amqp::SaslClient(id), out(out_), connection(c), sasl(s),
      hostname(hostname_), allowedMechanisms(mechs), transport(t), readHeader(true), writeHeader(false), haveOutput(false), initialised(false), state(NONE) {}

SaslClient::~SaslClient()
{
    connection->transportDeleted();
}

std::size_t SaslClient::decode(const char* buffer, std::size_t size)
{
    size_t decoded = 0;
    if (readHeader) {
        decoded += readProtocolHeader(buffer, size);
        readHeader = !decoded;
    }
    if (state == NONE && decoded < size) {
        decoded += read(buffer + decoded, size - decoded);
    } else if (state == SUCCEEDED) {
        if (securityLayer.get()) decoded += securityLayer->decode(buffer + decoded, size - decoded);
        else decoded += connection->decode(buffer + decoded, size - decoded);
    }
    QPID_LOG(trace, id << " SaslClient::decode(" << size << "): " << decoded);
    return decoded;
}

std::size_t SaslClient::encode(char* buffer, std::size_t size)
{
    size_t encoded = 0;
    if (writeHeader) {
         encoded += writeProtocolHeader(buffer, size);
         writeHeader = !encoded;
    }
    if ((!initialised || state == NONE) && encoded < size) {
        size_t extra = write(buffer + encoded, size - encoded);
        encoded += extra;
        initialised = extra > 0;
    } else if (state == SUCCEEDED) {
        if (securityLayer.get()) encoded += securityLayer->encode(buffer + encoded, size - encoded);
        else encoded += connection->encode(buffer + encoded, size - encoded);
    }
    haveOutput = (encoded == size);
    QPID_LOG(trace, id << " SaslClient::encode(" << size << "): " << encoded);
    return encoded;
}

bool SaslClient::canEncode()
{
    if (state == NONE) {
        QPID_LOG(trace, id << " SaslClient::canEncode(): " << writeHeader << " || " << haveOutput);
        return writeHeader || haveOutput;
    } else if (state == SUCCEEDED) {
        if (securityLayer.get()) return securityLayer->canEncode();
        else return connection->canEncode();
    } else {
        return false;
    }
}

void SaslClient::mechanisms(const std::string& offered)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-MECHANISMS(" << offered << ")");
    std::string response;

    std::string mechanisms;
    if (allowedMechanisms.size()) {
        std::vector<std::string> allowed = split(allowedMechanisms, " ");
        std::vector<std::string> supported = split(offered, " ");
        std::stringstream intersection;
        for (std::vector<std::string>::const_iterator i = allowed.begin(); i != allowed.end(); ++i) {
            if (std::find(supported.begin(), supported.end(), *i) != supported.end()) {
                if (!intersection.str().empty()) intersection << " ";
                intersection << *i;
            }
        }
        mechanisms = intersection.str();
    } else {
        mechanisms = offered;
    }

    if (sasl->start(mechanisms, response, &transport)) {
        init(sasl->getMechanism(), &response, hostname.size() ? &hostname : 0);
    } else {
        init(sasl->getMechanism(), 0, hostname.size() ? &hostname : 0);
    }
    haveOutput = true;
    out.activateOutput();
}
void SaslClient::challenge(const std::string& challenge)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-CHALLENGE(" << challenge.size() << " bytes)");
    std::string r = sasl->step(challenge);
    response(&r);
    haveOutput = true;
    out.activateOutput();
}
namespace {
const std::string EMPTY;
}
void SaslClient::challenge()
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-CHALLENGE(null)");
    std::string r = sasl->step(EMPTY);
    response(&r);
}
void SaslClient::outcome(uint8_t result, const std::string& extra)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-OUTCOME(" << result << ", " << extra << ")");
    outcome(result);
}
void SaslClient::outcome(uint8_t result)
{
    QPID_LOG_CAT(debug, protocol, id << " Received SASL-OUTCOME(" << result << ")");
    if (result) state = FAILED;
    else state = SUCCEEDED;

    securityLayer = sasl->getSecurityLayer(65535);
    if (securityLayer.get()) {
        securityLayer->init(connection.get());
    }
    out.activateOutput();
}

void SaslClient::closed()
{
    if (state == SUCCEEDED) {
        connection->closed();
    } else {
        QPID_LOG(info, id << " Connection closed prior to authentication completing");
        state = FAILED;
    }
}

bool SaslClient::isClosed() const
{
    if (state == FAILED) return true;
    else if (state == SUCCEEDED) return connection->isClosed();
    else return false;
}
qpid::framing::ProtocolVersion SaslClient::getVersion() const
{
    return qpid::framing::ProtocolVersion(1,0,qpid::framing::ProtocolVersion::SASL);
}

}}} // namespace qpid::broker::amqp
