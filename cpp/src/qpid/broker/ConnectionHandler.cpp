
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

#include "ConnectionHandler.h"
#include "Connection.h"
#include "qpid/framing/ConnectionStartBody.h"
#include "qpid/framing/Connection010StartBody.h"
#include "qpid/framing/ClientInvoker.h"
#include "qpid/framing/ServerInvoker.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;


namespace 
{
const std::string PLAIN = "PLAIN";
const std::string en_US = "en_US";
}

void ConnectionHandler::close(ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    handler->client.close(code, text, classId, methodId);
}

void ConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        bool handled = false;
        if (handler->serverMode) {
            handled = invoke(static_cast<AMQP_ServerOperations::Connection010Handler&>(*handler.get()), *method);
        } else {
            handled = invoke(static_cast<AMQP_ClientOperations::ConnectionHandler&>(*handler.get()), *method);
        }
        if (!handled) {
            handler->connection.getChannel(frame.getChannel()).in(frame);
        }

    }catch(ConnectionException& e){
        handler->client.close(e.code, e.what(), method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what(), method->amqpClassId(), method->amqpMethodId());
    }
}

ConnectionHandler::ConnectionHandler(Connection& connection)  : handler(new Handler(connection)) {
    FieldTable properties;
    string mechanisms(PLAIN);
    string locales(en_US);
    handler->serverMode = true;
    handler->client.start(properties, mechanisms, locales);
}



ConnectionHandler::Handler:: Handler(Connection& c) : client(c.getOutput()), server(c.getOutput()), 
                                                      connection(c), serverMode(false) {}

void ConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
    const string& mechanism, 
    const string& response, const string& /*locale*/)
{
    //TODO: handle SASL mechanisms more cleverly
    if (mechanism == PLAIN) {
        if (response.size() > 0 && response[0] == (char) 0) {
            string temp = response.substr(1);
            string::size_type i = temp.find((char)0);
            string uid = temp.substr(0, i);
            string pwd = temp.substr(i + 1);
            //TODO: authentication
            connection.setUserId(uid);
        }
    }
    client.tune(framing::CHANNEL_MAX, connection.getFrameMax(), connection.getHeartbeat());
}
        
void ConnectionHandler::Handler::secureOk(const string& /*response*/){}
        
void ConnectionHandler::Handler::tuneOk(uint16_t /*channelmax*/,
    uint32_t framemax, uint16_t heartbeat)
{
    connection.setFrameMax(framemax);
    connection.setHeartbeat(heartbeat);
}
        
void ConnectionHandler::Handler::open(const string& /*virtualHost*/,
    const string& /*capabilities*/, bool /*insist*/)
{
    string knownhosts;
    client.openOk(knownhosts);
}

        
void ConnectionHandler::Handler::close(uint16_t /*replyCode*/, const string& /*replyText*/, 
    uint16_t /*classId*/, uint16_t /*methodId*/)
{
    client.closeOk();
    connection.getOutput().close();
} 
        
void ConnectionHandler::Handler::closeOk(){
    connection.getOutput().close();
} 


void ConnectionHandler::Handler::start(uint8_t /*versionMajor*/,
                                       uint8_t /*versionMinor*/,
                                       const FieldTable& /*serverProperties*/,
                                       const string& /*mechanisms*/,
                                       const string& /*locales*/)
{
    string uid = "qpidd";
    string pwd = "qpidd";
    string response = ((char)0) + uid + ((char)0) + pwd;
    server.startOk(FieldTable(), PLAIN, response, en_US);
    connection.initMgmt(true);
}

void ConnectionHandler::Handler::secure(const string& /*challenge*/)
{
    server.secureOk("");
}

void ConnectionHandler::Handler::tune(uint16_t channelMax,
                                      uint32_t frameMax,
                                      uint16_t heartbeat)
{
    connection.setFrameMax(frameMax);
    connection.setHeartbeat(heartbeat);
    server.tuneOk(channelMax, frameMax, heartbeat);
    server.open("/", "", true);
}

void ConnectionHandler::Handler::openOk(const string& /*knownHosts*/)
{
}

void ConnectionHandler::Handler::redirect(const string& /*host*/, const string& /*knownHosts*/)
{
    
}
