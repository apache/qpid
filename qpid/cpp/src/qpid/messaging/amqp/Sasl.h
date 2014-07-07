#ifndef QPID_MESSAGING_AMQP_SASL_H
#define QPID_MESSAGING_AMQP_SASL_H

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
#include "qpid/sys/Codec.h"
#include "qpid/amqp/SaslClient.h"
#include <memory>

namespace qpid {
class Sasl;
namespace sys {
class SecurityLayer;
}
namespace messaging {
struct ConnectionOptions;
namespace amqp {
class ConnectionContext;

/**
 *
 */
class Sasl : public qpid::sys::Codec, qpid::amqp::SaslClient
{
  public:
    Sasl(const std::string& id, ConnectionContext& context, const std::string& hostname);
    ~Sasl();
    std::size_t decode(const char* buffer, std::size_t size);
    std::size_t encode(char* buffer, std::size_t size);
    bool canEncode();

    bool authenticated();
    qpid::sys::Codec* getSecurityLayer();
    std::string getAuthenticatedUsername();
 private:
    ConnectionContext& context;
    std::auto_ptr<qpid::Sasl> sasl;
    std::string hostname;
    bool readHeader;
    bool writeHeader;
    bool haveOutput;
    enum {
        NONE, FAILED, SUCCEEDED
    } state;
    std::auto_ptr<qpid::sys::SecurityLayer> securityLayer;
    std::string error;

    void mechanisms(const std::string&);
    void challenge(const std::string&);
    void challenge(); //null != empty string
    void outcome(uint8_t result, const std::string&);
    void outcome(uint8_t result);
    void failed(const std::string&);
  protected:
    bool stopReading();
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SASL_H*/
