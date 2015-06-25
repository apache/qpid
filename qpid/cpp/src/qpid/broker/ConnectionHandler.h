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
#ifndef _ConnectionAdapter_
#define _ConnectionAdapter_

#include <memory>
#include "qpid/Sasl.h"
#include "qpid/broker/SaslAuthenticator.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/AMQMethodBody.h"
#include "qpid/framing/AMQP_AllOperations.h"
#include "qpid/framing/AMQP_AllProxy.h"
#include "qpid/framing/ConnectionStartOkBody.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/ProtocolInitiation.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/Exception.h"
#include "qpid/sys/SecurityLayer.h"
#include "qpid/broker/System.h"



namespace qpid {

namespace sys {
struct SecuritySettings;
}


namespace broker {

namespace amqp_0_10 {
class Connection;
}
class SecureConnection;

class ConnectionHandler : public framing::FrameHandler
{
    struct Handler : public framing::AMQP_AllOperations::ConnectionHandler
    {
        framing::AMQP_AllProxy::Connection proxy;
        amqp_0_10::Connection& connection;
        bool serverMode;
        std::auto_ptr<SaslAuthenticator> authenticator;
        SecureConnection* secured;
        bool isOpen;

        Handler(amqp_0_10::Connection& connection, bool isClient);
        ~Handler();
        void startOk(const qpid::framing::ConnectionStartOkBody& body);
        void startOk(const qpid::framing::FieldTable& clientProperties,
                     const std::string& mechanism, const std::string& response,
                     const std::string& locale);
        void secureOk(const std::string& response);
        void tuneOk(uint16_t channelMax, uint16_t frameMax, uint16_t heartbeat);
        void heartbeat();
        void open(const std::string& virtualHost,
                  const framing::Array& capabilities, bool insist);
        void close(uint16_t replyCode, const std::string& replyText);
        void closeOk();

        void start(const qpid::framing::FieldTable& serverProperties,
                   const framing::Array& mechanisms,
                   const framing::Array& locales);

        void secure(const std::string& challenge);

        void tune(uint16_t channelMax,
                  uint16_t frameMax,
                  uint16_t heartbeatMin,
                  uint16_t heartbeatMax);

        void openOk(const framing::Array& knownHosts);

        void redirect(const std::string& host, const framing::Array& knownHosts);

        std::auto_ptr<Sasl> sasl;
        typedef boost::function<const qpid::sys::SecuritySettings*()> GetSecuritySettings;
        std::string saslUserId;
        uint16_t maxFrameSize;
    };
    std::auto_ptr<Handler> handler;

    bool handle(const qpid::framing::AMQMethodBody& method);
    void close(framing::connection::CloseCode code, const std::string& text);
  public:
    ConnectionHandler(amqp_0_10::Connection& connection, bool isClient );
    void heartbeat();
    void handle(framing::AMQFrame& frame);
    void setSecureConnection(SecureConnection* secured);
    bool isOpen() { return handler->isOpen; }
  friend class amqp_0_10::Connection;
};


}}

#endif
