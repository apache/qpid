
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
#include "qpid/framing/ServerInvoker.h"

using namespace qpid;
using namespace qpid::broker;
using namespace qpid::framing;


namespace 
{
const std::string PLAIN = "PLAIN";
const std::string en_US = "en_US";
}

void ConnectionHandler::init(const framing::ProtocolInitiation& header) {
    FieldTable properties;
    string mechanisms(PLAIN);
    string locales(en_US);
    handler->client.start(header.getMajor(), header.getMinor(), properties, mechanisms, locales);
}

void ConnectionHandler::close(ReplyCode code, const string& text, ClassId classId, MethodId methodId)
{
    handler->client.close(code, text, classId, methodId);
}

void ConnectionHandler::handle(framing::AMQFrame& frame)
{
    AMQMethodBody* method=frame.getBody()->getMethod();
    try{
        if (!invoke(*handler.get(), *method))
            throw ChannelErrorException(QPID_MSG("Class can't be accessed over channel 0"));
    }catch(ConnectionException& e){
        handler->client.close(e.code, e.what(), method->amqpClassId(), method->amqpMethodId());
    }catch(std::exception& e){
        handler->client.close(541/*internal error*/, e.what(), method->amqpClassId(), method->amqpMethodId());
    }
}

ConnectionHandler::ConnectionHandler(Connection& connection)  : handler(new Handler(connection)) {}

ConnectionHandler::Handler:: Handler(Connection& c) : client(c.getOutput()), connection(c) {}

void ConnectionHandler::Handler::startOk(const FieldTable& /*clientProperties*/,
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
