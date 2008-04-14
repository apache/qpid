
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
    string mechanisms(PLAIN);
    string locales(en_US);
    if (isClient) {
        handler->serverMode = false;
    }else {
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
 {}

PreviewConnectionHandler::Handler::~Handler() 
{
#if HAVE_LIBSASL2
    if (NULL != sasl_conn) {
        sasl_dispose(&sasl_conn);
        sasl_conn = NULL;
    }
#endif
}

void PreviewConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
    const string& mechanism, 
    const string& response, const string& /*locale*/)
{
    //TODO: handle SASL mechanisms more cleverly
    if (mechanism == PLAIN) {
        QPID_LOG(info, "SASL Plain: Attempting authentication");
        if (response.size() > 0 && response[0] == (char) 0) {
            string temp = response.substr(1);
            string::size_type i = temp.find((char)0);
            string uid = temp.substr(0, i);
            string pwd = temp.substr(i + 1);

#if HAVE_SASL
            if (connection.getBroker().getOptions().auth) {
                int code = sasl_server_new(BROKER_SASL_NAME,
                                           NULL, NULL, NULL, NULL, NULL, 0,
                                           &sasl_conn);

                if (SASL_OK != code) {
                    QPID_LOG(info, "SASL Plain: Connection creation failed: "
                             << sasl_errdetail(sasl_conn));

                        // TODO: Change this to an exception signaling
                        // server error, when one is available
                    throw CommandInvalidException("Unable to perform authentication");
                }

                code = sasl_checkpass(sasl_conn,
                                      uid.c_str(), uid.length(),
                                      pwd.c_str(), pwd.length());
                if (SASL_OK == code) {
                    QPID_LOG(info, "SASL Plain: Authentication accepted for " << uid);
                } else {
                        // See man sasl_errors(3) or sasl/sasl.h for possible errors
                    QPID_LOG(info, "SASL Plain: Authentication rejected for "
                             << uid << ": "
                             << sasl_errdetail(sasl_conn));

                        // TODO: Change this to an exception signaling
                        // authentication failure, when one is available
                    throw ConnectionForcedException("Authentication failed");
                }
            } else {
#endif
                QPID_LOG(warning,
                         "SASL Plain Warning: No Authentication Performed for "
                         << uid);
#if HAVE_SASL
            }
#endif

            connection.setUserId(uid);
        }
    } else {
			// The 0-10 spec states that if the client requests a
			// mechanism not proposed by the server the server MUST
			// close the connection. Assumption here is if we proposed
			// a mechanism we'd have a case for it above.
		throw NotImplementedException("Unsupported authentication mechanism");
	}
    client.tune(framing::CHANNEL_MAX, connection.getFrameMax(), connection.getHeartbeat());
}
        
void PreviewConnectionHandler::Handler::secureOk(const string& /*response*/){}
        
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
