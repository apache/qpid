
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

#include "config.h"

#include "PreviewConnectionHandler.h"
#include "PreviewConnection.h"
#include "qpid/framing/ConnectionStartBody.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"

#if HAVE_SASL
#include <sasl/sasl.h>
#endif

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;


namespace 
{
const std::string PLAIN = "PLAIN";
const std::string en_US = "en_US";
}

void PreviewConnectionHandler::close(ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    handler->client.close(code, text, classId, methodId);
}

void PreviewConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        if (handler->serverMode) {
            if (!invoke(static_cast<AMQP_ServerOperations::ConnectionHandler&>(*handler.get()), *method))
                throw ChannelErrorException(QPID_MSG("Class can't be accessed over channel 0"));
        } else {
            if (!invoke(static_cast<AMQP_ClientOperations::ConnectionHandler&>(*handler.get()), *method))
                throw ChannelErrorException(QPID_MSG("Class can't be accessed over channel 0"));
        }
    }catch(ConnectionException& e){
        handler->client.close(e.code, e.what(), method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what(), method->amqpClassId(), method->amqpMethodId());
    }
}

PreviewConnectionHandler::PreviewConnectionHandler(PreviewConnection& connection, bool isClient)  : handler(new Handler(connection)) {
    FieldTable properties;
    string mechanisms;
    string locales(en_US);
    if (isClient) {
        handler->serverMode = false;
    }else {
#if HAVE_SASL
        if (connection.getBroker().getOptions().auth) {
            const char *list;
            unsigned int list_len;
            int count;
            int code = sasl_listmech(handler->sasl_conn, NULL,
                                     "", " ", "",
                                     &list, &list_len,
                                     &count);

            if (SASL_OK != code) {
                QPID_LOG(info, "SASL: Mechanism listing failed: "
                         << sasl_errdetail(handler->sasl_conn));

                    // TODO: Change this to an exception signaling
                    // server error, when one is available
                throw CommandInvalidException("Mechanism listing failed");
            } else {
                    // TODO: For 0-10 the mechanisms must be returned
                    // in a list instead of space separated
                mechanisms = list;
            }
        } else {
#endif
                // TODO: It would be more proper for this to be ANONYMOUS
            mechanisms = PLAIN;
#if HAVE_SASL
        }
#endif

        QPID_LOG(info, "SASL: Sending mechanism list: " << mechanisms);

        handler->serverMode = true;
        handler->client.start(99, 0, properties, mechanisms, locales);
    }
}

PreviewConnectionHandler::Handler::Handler(PreviewConnection& c) : 
#if HAVE_SASL
    sasl_conn(NULL),
#endif
    client(c.getOutput()), server(c.getOutput()), 
    connection(c), serverMode(false)
{
#if HAVE_SASL
    if (connection.getBroker().getOptions().auth) {
        int code = sasl_server_new(BROKER_SASL_NAME,
                                   NULL, NULL, NULL, NULL, NULL, 0,
                                   &sasl_conn);

        if (SASL_OK != code) {
            QPID_LOG(info, "SASL: Connection creation failed: "
                     << sasl_errdetail(sasl_conn));

                // TODO: Change this to an exception signaling
                // server error, when one is available
            throw CommandInvalidException("Unable to perform authentication");
        }
    }
#endif
}

PreviewConnectionHandler::Handler::~Handler() 
{
#if HAVE_SASL
    if (NULL != sasl_conn) {
        sasl_dispose(&sasl_conn);
        sasl_conn = NULL;
    }
#endif
}

#if HAVE_SASL
void PreviewConnectionHandler::Handler::processAuthenticationStep(int code, const char *challenge, unsigned int challenge_len)
{
    if (SASL_OK == code) {
        const void *uid;

        code = sasl_getprop(sasl_conn,
                            SASL_USERNAME,
                            &uid);
        if (SASL_OK != code) {
            QPID_LOG(info, "SASL: Authentication succeeded, username unavailable");
                // TODO: Change this to an exception signaling
                // authentication failure, when one is available
            throw ConnectionForcedException("Authenticated username unavailable");
        }

        QPID_LOG(info, "SASL: Authentication succeeded for: " << (char *)uid);

        connection.setUserId((char *)uid);

        client.tune(framing::CHANNEL_MAX,
                    connection.getFrameMax(),
                    connection.getHeartbeat());
    } else if (SASL_CONTINUE == code) {
        string challenge_str(challenge, challenge_len);

        QPID_LOG(debug, "SASL: sending challenge to client");

        client.secure(challenge_str);
    } else {
        QPID_LOG(info, "SASL: Authentication failed: "
                 << sasl_errdetail(sasl_conn));

            // TODO: Change to more specific exceptions, when they are
            // available
        switch (code) {
          case SASL_NOMECH:
            throw ConnectionForcedException("Unsupported mechanism");
           break;
          case SASL_TRYAGAIN:
            throw ConnectionForcedException("Transient failure, try again");
           break;
          default:
            throw ConnectionForcedException("Authentication failed");
           break;
        }
    }
}
#endif

void PreviewConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
#if HAVE_SASL
    const string& mechanism,
    const string& response,
#else
    const string& /*mechanism*/, 
    const string& /*response*/,
#endif
    const string& /*locale*/)
{
#if HAVE_SASL
    if (connection.getBroker().getOptions().auth) {
        const char *challenge;
        unsigned int challenge_len;

        QPID_LOG(info, "SASL: Starting authentication with mechanism: " << mechanism);
        int code = sasl_server_start(sasl_conn,
                                     mechanism.c_str(),
                                     response.c_str(), response.length(),
                                     &challenge, &challenge_len);

        processAuthenticationStep(code, challenge, challenge_len);
    } else {
#endif
        QPID_LOG(warning, "SASL: No Authentication Performed");

            // TODO: Figure out what should actually be set in this case
        connection.setUserId("anonymous");

        client.tune(framing::CHANNEL_MAX,
                    connection.getFrameMax(),
                    connection.getHeartbeat());
#if HAVE_SASL
    }
#endif
}
        
void PreviewConnectionHandler::Handler::secureOk(const string&
#if HAVE_SASL
                                                    response
#endif
                                                 ) {
#if HAVE_SASL
    int code;
    const char *challenge;
    unsigned int challenge_len;

    code = sasl_server_step(sasl_conn,
                            response.c_str(), response.length(),
                            &challenge, &challenge_len);

    processAuthenticationStep(code, challenge, challenge_len);
#endif
}
        
void PreviewConnectionHandler::Handler::tuneOk(uint16_t /*channelmax*/,
    uint32_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void PreviewConnectionHandler::Handler::open(const string& /*virtualHost*/,
    const string& /*capabilities*/, bool /*insist*/)
{
    string knownhosts;
    client.openOk(knownhosts);
}

        
void PreviewConnectionHandler::Handler::close(uint16_t /*replyCode*/, const string& /*replyText*/, 
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk();
    connection.getOutput().close();
} 
        
void PreviewConnectionHandler::Handler::closeOk(){
    connection.getOutput().close();
} 


void PreviewConnectionHandler::Handler::start(uint8_t /*versionMajor*/,
                                       uint8_t /*versionMinor*/,
                                       const FieldTable& /*serverProperties*/,
                                       const string& /*mechanisms*/,
                                       const string& /*locales*/)
{
    string uid = "qpidd";
    string pwd = "qpidd";
    string response = ((char)0) + uid + ((char)0) + pwd;
    server.startOk(FieldTable(), PLAIN, response, en_US);
}

void PreviewConnectionHandler::Handler::secure(const string& /*challenge*/)
{
    server.secureOk("");
}

void PreviewConnectionHandler::Handler::tune(uint16_t channelMax,
                                      uint32_t frameMax,
                                      uint16_t heartbeat)
{
    connection.setFrameMax(frameMax);
    connection.setHeartbeat(heartbeat);
    server.tuneOk(channelMax, frameMax, heartbeat);
    server.open("/", "", true);
}

void PreviewConnectionHandler::Handler::openOk(const string& /*knownHosts*/)
{
}

void PreviewConnectionHandler::Handler::redirect(const string& /*host*/, const string& /*knownHosts*/)
{
    
}
