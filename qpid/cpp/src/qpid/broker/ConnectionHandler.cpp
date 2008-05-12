
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

#include "ConnectionHandler.h"
#include "Connection.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/ServerInvoker.h"
#include "qpid/log/Statement.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;


namespace 
{
const std::string ANONYMOUS = "ANONYMOUS";
const std::string PLAIN     = "PLAIN";
const std::string en_US     = "en_US";
}

void ConnectionHandler::close(ReplyCode code, const string& text, ClassId, MethodId)
{
    handler->client.close(code, text);
}

void ConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        bool handled = false;
        if (handler->serverMode) {
            handled = invoke(static_cast<AMQP_ServerOperations::ConnectionHandler&>(*handler.get()), *method);
        } else {
            handled = invoke(static_cast<AMQP_ClientOperations::ConnectionHandler&>(*handler.get()), *method);
        }
        if (!handled) {
            handler->connection.getChannel(frame.getChannel()).in(frame);
        }

    }catch(ConnectionException& e){
        handler->client.close(e.code, e.what());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what());
    }
}

ConnectionHandler::ConnectionHandler(Connection& connection, bool isClient)  : handler(new Handler(connection, isClient)) {}

ConnectionHandler::Handler::Handler(Connection& c, bool isClient) :
    client(c.getOutput()), server(c.getOutput()), 
    connection(c), serverMode(!isClient)
{
    if (serverMode) {
        FieldTable properties;
        Array mechanisms(0x95);
        
        authenticator = SaslAuthenticator::createAuthenticator(c);
        authenticator->getMechanisms(mechanisms);
        
        Array locales(0x95);
        boost::shared_ptr<FieldValue> l(new Str16Value(en_US));
        locales.add(l);
        client.start(properties, mechanisms, locales);
    }
}


ConnectionHandler::Handler::~Handler() {}


void ConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
                                         const string& mechanism, 
                                         const string& response,
                                         const string& /*locale*/)
{
    authenticator->start(mechanism, response);
}
        
void ConnectionHandler::Handler::secureOk(const string& response)
{
    authenticator->step(response);
}
        
void ConnectionHandler::Handler::tuneOk(uint16_t /*channelmax*/,
    uint16_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void ConnectionHandler::Handler::open(const string& /*virtualHost*/,
                                      const framing::Array& /*capabilities*/, bool /*insist*/)
{
    framing::Array knownhosts;
    client.openOk(knownhosts);
}

        
void ConnectionHandler::Handler::close(uint16_t replyCode, const string& replyText)
{
    if (replyCode != 200) {
        QPID_LOG(warning, "Client closed connection with " << replyCode << ": " << replyText);
    }
    client.closeOk();
    connection.getOutput().close();
} 
        
void ConnectionHandler::Handler::closeOk(){
    connection.getOutput().close();
} 


void ConnectionHandler::Handler::start(const FieldTable& /*serverProperties*/,
                                       const framing::Array& /*mechanisms*/,
                                       const framing::Array& /*locales*/)
{
    string response;
    server.startOk(FieldTable(), ANONYMOUS, response, en_US);
    connection.initMgmt(true);
}

void ConnectionHandler::Handler::secure(const string& /*challenge*/)
{
    server.secureOk("");
}

void ConnectionHandler::Handler::tune(uint16_t channelMax,
                                      uint16_t frameMax,
                                      uint16_t /*heartbeatMin*/,
                                      uint16_t heartbeatMax)
{
    connection.setFrameMax(frameMax);
    connection.setHeartbeat(heartbeatMax);
    server.tuneOk(channelMax, frameMax, heartbeatMax);
    server.open("/", Array(), true);
}

void ConnectionHandler::Handler::openOk(const framing::Array& /*knownHosts*/)
{
}

void ConnectionHandler::Handler::redirect(const string& /*host*/, const framing::Array& /*knownHosts*/)
{
    
}
