
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

#include "PreviewConnectionHandler.h"
#include "PreviewConnection.h"
#include "qpid/framing/ConnectionStartBody.h"
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

PreviewConnectionHandler::PreviewConnectionHandler(PreviewConnection& connection)  : handler(new Handler(connection)) {
    FieldTable properties;
    string mechanisms(PLAIN);
    string locales(en_US);
    handler->serverMode = true;
    handler->client.start(0, 10, properties, mechanisms, locales);
}

PreviewConnectionHandler::Handler:: Handler(PreviewConnection& c) : client(c.getOutput()), server(c.getOutput()), 
                                                      connection(c), serverMode(false) {}

void PreviewConnectionHandler::Handler::startOk(const framing::FieldTable& /*clientProperties*/,
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
    connection.initMgmt(true);
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
